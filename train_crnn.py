import os
import cv2
import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split
import random
import re
import pytesseract

SYNTHETIC_DIR = './data/synthetic'
CROPPED_DIR = './data/cropped'

# Configuration
IMG_WIDTH = 128
IMG_HEIGHT = 32
MAX_LEN = 10
BATCH_SIZE = 32
EPOCHS_PRETRAIN = 10
EPOCHS_FINETUNE = 30

# Character mapping (digits 0-9 + common tracking letters)
characters = [str(i) for i in range(10)] + ['D', 'Z', 'X']
char_to_num = layers.StringLookup(vocabulary=characters, mask_token=None)
num_to_char = layers.StringLookup(vocabulary=char_to_num.get_vocabulary(), mask_token=None, invert=True)

def encode_single_sample(img_path, label):
    img = tf.io.read_file(img_path)
    img = tf.io.decode_jpeg(img, channels=1)
    img = tf.image.convert_image_dtype(img, tf.float32)
    img = tf.image.resize(img, [IMG_HEIGHT, IMG_WIDTH])
    img = tf.transpose(img, perm=[1, 0, 2])
    
    # Pad or truncate label to MAX_LEN just in case, but CTC can handle variable length
    label = tf.strings.unicode_split(label, input_encoding="UTF-8")
    label = char_to_num(label)
    return img, label

# Model Building
def build_model():
    input_img = layers.Input(shape=(IMG_WIDTH, IMG_HEIGHT, 1), name="image", dtype="float32")
    labels = layers.Input(name="label", shape=(None,), dtype="float32")
    
    # Conv Block 1
    x = layers.Conv2D(32, (3, 3), activation="relu", kernel_initializer="he_normal", padding="same")(input_img)
    x = layers.MaxPooling2D((2, 2))(x)
    
    # Conv Block 2
    x = layers.Conv2D(64, (3, 3), activation="relu", kernel_initializer="he_normal", padding="same")(x)
    x = layers.MaxPooling2D((2, 2))(x)
    
    # Reshape for RNN
    new_shape = ((IMG_WIDTH // 4), (IMG_HEIGHT // 4) * 64)
    x = layers.Reshape(target_shape=new_shape)(x)
    x = layers.Dense(64, activation="relu")(x)
    x = layers.Dropout(0.2)(x)
    
    # RNNs
    x = layers.Bidirectional(layers.LSTM(128, return_sequences=True, dropout=0.25))(x)
    x = layers.Bidirectional(layers.LSTM(64, return_sequences=True, dropout=0.25))(x)
    
    # Output layer
    x = layers.Dense(len(char_to_num.get_vocabulary()) + 1, activation="softmax", name="dense2")(x)
    
    # CTC Loss
    def ctc_loss(y_true, y_pred):
        batch_len = tf.cast(tf.shape(y_true)[0], dtype="int64")
        input_length = tf.cast(tf.shape(y_pred)[1], dtype="int64")
        label_length = tf.cast(tf.shape(y_true)[1], dtype="int64")
        
        input_length = input_length * tf.ones(shape=(batch_len, 1), dtype="int64")
        label_length = label_length * tf.ones(shape=(batch_len, 1), dtype="int64")
        
        loss = tf.keras.backend.ctc_batch_cost(y_true, y_pred, input_length, label_length)
        return loss

    model = keras.models.Model(inputs=input_img, outputs=x)
    model.compile(optimizer=keras.optimizers.Adam(), loss=ctc_loss)
    
    # Inference model is the same
    inference_model = model
    
    return model, inference_model

# Load Synthetic Data
print("Loading synthetic data...")
synth_df = pd.read_csv(os.path.join(SYNTHETIC_DIR, 'synthetic_labels.csv'), dtype={'phone_number': str})
synth_df['filename'] = synth_df['filename'].apply(lambda x: os.path.join(SYNTHETIC_DIR, x))
# Ensure phone_number is string
synth_df['phone_number'] = synth_df['phone_number'].astype(str)

synth_dataset = tf.data.Dataset.from_tensor_slices((list(synth_df['filename']), list(synth_df['phone_number'])))
synth_dataset = synth_dataset.map(encode_single_sample, num_parallel_calls=tf.data.AUTOTUNE)
synth_dataset = synth_dataset.batch(BATCH_SIZE).prefetch(buffer_size=tf.data.AUTOTUNE)

# Build Model
model, inference_model = build_model()

print("Pretraining on synthetic data...")
history_pretrain = model.fit(synth_dataset, epochs=EPOCHS_PRETRAIN)

# Generate pseudo-labels for cropped dataset if labels.csv doesn't exist
LABELS_CSV = 'labels.csv'
if not os.path.exists(LABELS_CSV):
    print("Generating pseudo-labels for real data using OCR...")
    rows = []
    for f in os.listdir(CROPPED_DIR):
        if not f.endswith(('.jpg', '.jpeg', '.png')): continue
        img = cv2.imread(os.path.join(CROPPED_DIR, f))
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        text = pytesseract.image_to_string(gray, config='--psm 7 -c tessedit_char_whitelist=0123456789')
        digits = re.sub(r'\D', '', text)
        if len(digits) < 5:
            digits = "1234567890" # fallback
        elif len(digits) > 10:
            digits = digits[:10]
        digits = digits.zfill(10)
        rows.append([f, digits, 'model_labeled', 'low'])
    
    pd.DataFrame(rows, columns=['filename', 'phone_number', 'label_source', 'confidence']).to_csv(LABELS_CSV, index=False)

print("Loading real cropped data...")
LABELS_CSV = 'true_labels.csv'
real_df = pd.read_csv(LABELS_CSV, dtype={'phone_number': str})
real_df = real_df[real_df['phone_number'] != 'SKIP']
real_df['filename'] = real_df['filename'].apply(lambda x: os.path.join(CROPPED_DIR, x))
real_df['phone_number'] = real_df['phone_number'].astype(str)

if len(real_df) < 2:
    print("Not enough labeled data to split into train and test.")
    exit(1)

# Train/Test Split (15% test)
test_size = max(1, int(len(real_df) * 0.15))
train_df, test_df = train_test_split(real_df, test_size=test_size, random_state=42)
print(f"Real data - Train: {len(train_df)}, Test: {len(test_df)}")

# Create datasets
def encode_with_augmentation(img_path, label):
    # Load and decode
    img = tf.io.read_file(img_path)
    img = tf.io.decode_jpeg(img, channels=1)
    img = tf.image.convert_image_dtype(img, tf.float32)
    
    # Augmentation
    img = tf.image.random_brightness(img, 0.2)
    img = tf.image.random_contrast(img, 0.8, 1.2)
    
    # Random Crop & pad back to original (shift)
    shape = tf.shape(img)
    img = tf.image.resize_with_crop_or_pad(img, shape[0] + 4, shape[1] + 4)
    img = tf.image.random_crop(img, size=shape)
    
    img = tf.image.resize(img, [IMG_HEIGHT, IMG_WIDTH])
    img = tf.transpose(img, perm=[1, 0, 2])
    
    label = tf.strings.unicode_split(label, input_encoding="UTF-8")
    label = char_to_num(label)
    return img, label

train_dataset = tf.data.Dataset.from_tensor_slices((list(train_df['filename']), list(train_df['phone_number'])))
train_dataset = train_dataset.map(encode_with_augmentation, num_parallel_calls=tf.data.AUTOTUNE)
train_dataset = train_dataset.padded_batch(BATCH_SIZE, padded_shapes=([IMG_WIDTH, IMG_HEIGHT, 1], [None]))
train_dataset = train_dataset.prefetch(buffer_size=tf.data.AUTOTUNE)

test_dataset = tf.data.Dataset.from_tensor_slices((list(test_df['filename']), list(test_df['phone_number'])))
test_dataset = test_dataset.map(encode_single_sample, num_parallel_calls=tf.data.AUTOTUNE)
test_dataset = test_dataset.padded_batch(BATCH_SIZE, padded_shapes=([IMG_WIDTH, IMG_HEIGHT, 1], [None]))
test_dataset = test_dataset.prefetch(buffer_size=tf.data.AUTOTUNE)

print("Fine-tuning on real data...")
history_finetune = model.fit(train_dataset, validation_data=test_dataset, epochs=EPOCHS_FINETUNE)

# Plot Loss
plt.figure(figsize=(10, 5))
plt.plot(history_finetune.history['loss'], label='Train Loss')
plt.plot(history_finetune.history['val_loss'], label='Validation Loss')
plt.title('Fine-tuning Loss')
plt.xlabel('Epochs')
plt.ylabel('Loss')
plt.legend()
plt.savefig('training_loss.png')

# Evaluation
def decode_batch_predictions(pred):
    input_len = np.ones(pred.shape[0]) * pred.shape[1]
    results = keras.backend.ctc_decode(pred, input_length=input_len, greedy=True)[0][0][:, :MAX_LEN]
    output_text = []
    for res in results:
        res = tf.strings.reduce_join(num_to_char(res)).numpy().decode("utf-8")
        output_text.append(res.replace('[UNK]', ''))
    return output_text

total_chars = 0
correct_chars = 0
exact_matches = 0
total_samples = 0
test_results = []

for batch_images, batch_labels in test_dataset:
    preds = inference_model.predict(batch_images)
    pred_texts = decode_batch_predictions(preds)
    
    for i in range(len(pred_texts)):
        pred_text = pred_texts[i]
        true_label = tf.strings.reduce_join(num_to_char(batch_labels[i])).numpy().decode("utf-8")
        
        # Character acc
        min_len = min(len(pred_text), len(true_label))
        for j in range(min_len):
            if pred_text[j] == true_label[j]:
                correct_chars += 1
        total_chars += len(true_label)
        
        # Exact match
        if pred_text == true_label:
            exact_matches += 1
            
        total_samples += 1
        test_results.append(f"True: {true_label} | Pred: {pred_text}")

char_acc = correct_chars / total_chars if total_chars > 0 else 0
exact_acc = exact_matches / total_samples if total_samples > 0 else 0

print(f"Final Performance on Test Set:")
print(f"Character-level accuracy: {char_acc:.4f}")
print(f"Exact-match accuracy: {exact_acc:.4f}")

with open('test_breakdown.txt', 'w') as f:
    for res in test_results:
        f.write(res + "\n")
    f.write(f"\nCharacter-level accuracy: {char_acc:.4f}\n")
    f.write(f"Exact-match accuracy: {exact_acc:.4f}\n")

# Save model for TFLite conversion
inference_model.save('crnn_inference_model.keras')

with open('crnn_results.md', 'w') as f:
    f.write(f"# CRNN Training Results\n\n")
    f.write(f"## Fine-tuning Performance\n")
    f.write(f"- **Character-level accuracy**: {char_acc:.2%}\n")
    f.write(f"- **Exact-match accuracy**: {exact_acc:.2%}\n\n")
    f.write(f"![Training Loss](file:///Users/bhavya_agarwal/Desktop/Numcheck/training_loss.png)\n")
