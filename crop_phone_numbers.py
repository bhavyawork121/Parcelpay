import cv2
import pytesseract
import os
import re
import csv
import pandas as pd
from PIL import Image
import matplotlib.pyplot as plt
import numpy as np

RAW_DIR = './data/raw_photos'
CROP_DIR = './data/cropped'
os.makedirs(CROP_DIR, exist_ok=True)

# Heuristic keywords
keywords = ['ph', 'mob', 'contact', 'cell', 'no', 'number']

def find_phone_line(data):
    lines = {}
    for i in range(len(data['level'])):
        if data['text'][i].strip() == '':
            continue
        line_num = data['line_num'][i]
        block_num = data['block_num'][i]
        key = (block_num, line_num)
        
        if key not in lines:
            lines[key] = {
                'text': [],
                'left': data['left'][i],
                'top': data['top'][i],
                'right': data['left'][i] + data['width'][i],
                'bottom': data['top'][i] + data['height'][i]
            }
        else:
            lines[key]['text'].append(data['text'][i])
            lines[key]['left'] = min(lines[key]['left'], data['left'][i])
            lines[key]['top'] = min(lines[key]['top'], data['top'][i])
            lines[key]['right'] = max(lines[key]['right'], data['left'][i] + data['width'][i])
            lines[key]['bottom'] = max(lines[key]['bottom'], data['top'][i] + data['height'][i])
            
    best_box = None
    best_score = -1
    
    for key, line in lines.items():
        text = ' '.join(line['text'])
        text_lower = text.lower()
        
        score = 0
        
        # Heuristic 1: Contains 10 digits
        digits = re.findall(r'\d', text)
        if len(digits) >= 10:
            score += 10
            
        # Heuristic 2: Keywords
        for kw in keywords:
            if kw in text_lower:
                score += 5
                
        # Phone number pattern
        if re.search(r'\d{3}[-\s]?\d{3}[-\s]?\d{4}', text):
            score += 10
            
        if score > best_score and score > 0:
            best_score = score
            best_box = (line['left'], line['top'], line['right'], line['bottom'])
            
    return best_box

review_needed = []

def process_images():
    count = 0
    sample_crops = []
    sample_labels = []
    
    for filename in os.listdir(RAW_DIR):
        if not filename.lower().endswith(('.png', '.jpg', '.jpeg', '.heic')):
            continue
            
        img_path = os.path.join(RAW_DIR, filename)
        img = cv2.imread(img_path)
        if img is None:
            continue
            
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        data = pytesseract.image_to_data(gray, output_type=pytesseract.Output.DICT)
        
        box = find_phone_line(data)
        
        if box:
            l, t, r, b = box
            
            # Padding
            pad = 10
            h, w = img.shape[:2]
            l = max(0, l - pad)
            t = max(0, t - pad)
            r = min(w, r + pad)
            b = min(h, b + pad)
            
            crop = img[t:b, l:r]
            crop_path = os.path.join(CROP_DIR, filename)
            cv2.imwrite(crop_path, crop)
            
            if len(sample_crops) < 15:
                # Convert BGR to RGB for matplotlib
                sample_crops.append(cv2.cvtColor(crop, cv2.COLOR_BGR2RGB))
                sample_labels.append(filename)
                
            count += 1
        else:
            review_needed.append(filename)
            
    print(f"Cropped {count} images.")
    print(f"Review needed for {len(review_needed)} images.")
    
    with open('review_needed.csv', 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['filename'])
        for f_name in review_needed:
            writer.writerow([f_name])
            
    # Show sample grid
    if sample_crops:
        n = len(sample_crops)
        cols = 3
        rows = (n + cols - 1) // cols
        fig, axes = plt.subplots(rows, cols, figsize=(15, 5 * rows))
        axes = axes.flatten()
        for i in range(len(axes)):
            if i < n:
                axes[i].imshow(sample_crops[i])
                axes[i].set_title(sample_labels[i])
                axes[i].axis('off')
            else:
                axes[i].axis('off')
        
        plt.tight_layout()
        plt.savefig('sample_crops_grid.png')
        print("Sample crops grid saved to sample_crops_grid.png")

if __name__ == '__main__':
    process_images()
