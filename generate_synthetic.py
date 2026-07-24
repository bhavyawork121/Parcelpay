import os
import cv2
import csv
import numpy as np
import random
import matplotlib.pyplot as plt
from tensorflow.keras.datasets import mnist

SYNTHETIC_DIR = './data/synthetic'
os.makedirs(SYNTHETIC_DIR, exist_ok=True)

# Load MNIST
(x_train, y_train), (x_test, y_test) = mnist.load_data()
images = np.concatenate((x_train, x_test))
labels = np.concatenate((y_train, y_test))

# Group by label
digit_indices = {i: np.where(labels == i)[0] for i in range(10)}

def augment_digit(img):
    # Padding
    img = cv2.copyMakeBorder(img, 5, 5, 5, 5, cv2.BORDER_CONSTANT, value=0)
    
    # Random Rotation (-15 to 15 degrees)
    angle = random.uniform(-15, 15)
    h, w = img.shape
    M = cv2.getRotationMatrix2D((w//2, h//2), angle, 1.0)
    img = cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    
    # Elastic deformation (simulating warp)
    alpha = img.shape[1] * 2
    sigma = img.shape[1] * 0.08
    alpha_affine = img.shape[1] * 0.08
    
    random_state = np.random.RandomState(None)
    shape = img.shape
    dx = cv2.GaussianBlur((random_state.rand(*shape) * 2 - 1).astype(np.float32), (0, 0), sigma) * alpha
    dy = cv2.GaussianBlur((random_state.rand(*shape) * 2 - 1).astype(np.float32), (0, 0), sigma) * alpha
    x, y = np.meshgrid(np.arange(shape[1]), np.arange(shape[0]))
    map_x = np.float32(x + dx)
    map_y = np.float32(y + dy)
    
    img = cv2.remap(img, map_x, map_y, interpolation=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=0)
    
    return img

def generate_sequence():
    digits = [random.randint(0, 9) for _ in range(10)]
    label_str = "".join(map(str, digits))
    
    imgs = []
    for d in digits:
        idx = random.choice(digit_indices[d])
        img = images[idx].copy()
        img = augment_digit(img)
        imgs.append(img)
        
    # Stitching
    stitched_w = sum(img.shape[1] for img in imgs) + 9 * 10 # Add random spacing margin max
    h = max(img.shape[0] for img in imgs)
    
    out_img = np.zeros((h, stitched_w), dtype=np.uint8)
    
    current_x = 0
    for img in imgs:
        # Random spacing (-2 to +5)
        spacing = random.randint(-2, 5)
        current_x = max(0, current_x + spacing)
        
        # Overlay
        ih, iw = img.shape
        # Place centered vertically
        y_off = (h - ih) // 2
        
        # Avoid out of bounds
        if current_x + iw > stitched_w:
            break
            
        # Add to output (bitwise OR since background is 0)
        roi = out_img[y_off:y_off+ih, current_x:current_x+iw]
        out_img[y_off:y_off+ih, current_x:current_x+iw] = np.maximum(roi, img)
        
        current_x += iw
        
    # Crop right blank space
    non_zero_cols = np.where(out_img.max(axis=0) > 0)[0]
    if len(non_zero_cols) > 0:
        out_img = out_img[:, :non_zero_cols[-1]+10]
        
    # Brightness / Contrast jitter
    alpha = random.uniform(0.8, 1.2) # Contrast
    beta = random.randint(-30, 30)   # Brightness
    out_img = cv2.convertScaleAbs(out_img, alpha=alpha, beta=beta)
    
    # Invert to match typical handwritten on paper (dark text, light background)
    out_img = 255 - out_img
    
    return out_img, label_str

samples = []
sample_labels = []
csv_rows = []

print("Generating synthetic dataset...")
for i in range(5000):
    img, label = generate_sequence()
    filename = f"synth_{i:04d}.jpg"
    filepath = os.path.join(SYNTHETIC_DIR, filename)
    cv2.imwrite(filepath, img)
    csv_rows.append([filename, label])
    
    if i < 10:
        samples.append(img)
        sample_labels.append(label)

with open(os.path.join(SYNTHETIC_DIR, 'synthetic_labels.csv'), 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['filename', 'phone_number'])
    writer.writerows(csv_rows)

print("Finished generating 5000 images.")

# Save sample grid
fig, axes = plt.subplots(5, 2, figsize=(10, 10))
axes = axes.flatten()
for i in range(10):
    axes[i].imshow(samples[i], cmap='gray')
    axes[i].set_title(sample_labels[i])
    axes[i].axis('off')
    
plt.tight_layout()
plt.savefig('synthetic_samples_grid.png')
print("Sample grid saved to synthetic_samples_grid.png")
