import os
import cv2
import numpy as np

RAW_DIR = './data/raw_photos'

files = [f for f in os.listdir(RAW_DIR) if f.lower().endswith(('.jpg', '.jpeg', '.png', '.heic'))]
files.sort()

# We will create a contact sheet of all raw photos
# size: 100x100 per cell, 20 columns, 16 rows
rows, cols = 16, 20
cell_size = 120

contact_sheet = np.zeros((rows * cell_size, cols * cell_size, 3), dtype=np.uint8)

mapping = []

for i, f in enumerate(files):
    if i >= rows * cols:
        break
    
    r = i // cols
    c = i % cols
    
    img_path = os.path.join(RAW_DIR, f)
    img = cv2.imread(img_path)
    if img is None:
        continue
        
    img = cv2.resize(img, (cell_size, cell_size))
    
    # Put text with ID
    cv2.putText(img, str(i), (5, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)
    
    y = r * cell_size
    x = c * cell_size
    contact_sheet[y:y+cell_size, x:x+cell_size] = img
    
    mapping.append((i, f))

cv2.imwrite('raw_contact_sheet.jpg', contact_sheet)

import csv
with open('raw_mapping.csv', 'w') as f:
    writer = csv.writer(f)
    for i, name in mapping:
        writer.writerow([i, name])

print(f"Created contact sheet with {len(mapping)} images.")
