import os
import time
import cv2
import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

MODEL_PATH = 'crnn_inference_model.keras'
TFLITE_PATH = 'crnn_model.tflite'
IMG_WIDTH = 128
IMG_HEIGHT = 32

def export_and_evaluate():
    # 1. Convert to TFLite
    print(f"Loading keras model from {MODEL_PATH}...")
    model = keras.models.load_model(MODEL_PATH, compile=False)
    
    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Enable optimizations for mobile
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS, tf.lite.OpsSet.SELECT_TF_OPS]
    converter._experimental_lower_tensor_list_ops = False
    
    tflite_model = converter.convert()
    
    with open(TFLITE_PATH, 'wb') as f:
        f.write(tflite_model)
        
    size_mb = os.path.getsize(TFLITE_PATH) / (1024 * 1024)
    print(f"TFLite Model saved to {TFLITE_PATH}")
    print(f"File Size: {size_mb:.2f} MB")
    
    # 2. Run Inference with TFLite
    interpreter = tf.lite.Interpreter(
        model_path=TFLITE_PATH,
        experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_OPS
    )
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Load a test image (grab first one from cropped)
    cropped_dir = './data/cropped'
    test_img_path = None
    for f in os.listdir(cropped_dir):
        if f.endswith('.jpg') or f.endswith('.jpeg'):
            test_img_path = os.path.join(cropped_dir, f)
            break
            
    if not test_img_path:
        print("No test image found.")
        return
        
    print(f"\nRunning inference on {test_img_path}...")
    
    # Preprocess exactly as in training
    img = tf.io.read_file(test_img_path)
    img = tf.io.decode_jpeg(img, channels=1)
    img = tf.image.convert_image_dtype(img, tf.float32)
    img = tf.image.resize(img, [IMG_HEIGHT, IMG_WIDTH])
    img = tf.transpose(img, perm=[1, 0, 2])
    img_array = tf.expand_dims(img, axis=0) # add batch dim
    
    interpreter.set_tensor(input_details[0]['index'], img_array)
    
    # Measure inference time
    start_time = time.time()
    interpreter.invoke()
    end_time = time.time()
    
    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    inf_time_ms = (end_time - start_time) * 1000
    print(f"Inference Time: {inf_time_ms:.2f} ms")
    
    with open('tflite_report.txt', 'w') as f:
        f.write(f"TFLite File Size: {size_mb:.2f} MB\n")
        f.write(f"Single Image Inference Time: {inf_time_ms:.2f} ms\n")

if __name__ == '__main__':
    if os.path.exists(MODEL_PATH):
        export_and_evaluate()
    else:
        print("Keras model not found, wait for training to finish.")
