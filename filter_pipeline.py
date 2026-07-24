import os
import cv2
import csv
import shutil
import pytesseract
import re
import matplotlib.pyplot as plt

RAW_DIR = './data/raw_photos'
CROP_DIR = './data/cropped'
EXCLUDED_DIR = './data/excluded'

os.makedirs(CROP_DIR, exist_ok=True)
os.makedirs(EXCLUDED_DIR, exist_ok=True)

# 1. Filter raw dataset
excluded_files = []
for f in os.listdir(RAW_DIR):
    if not f.lower().endswith(('.jpg', '.jpeg', '.png', '.heic')): continue
    
    # "check whether it's actually a genuine parcel-label photo at all (not a screenshot)"
    if 'screenshot' in f.lower() or 'whatsapp' in f.lower() and f.startswith('Screenshot'):
        excluded_files.append((f, 'Screenshot image detected'))
        
with open('excluded.csv', 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['filename', 'reason'])
    for fname, reason in excluded_files:
        writer.writerow([fname, reason])
        # Move to excluded
        try:
            shutil.move(os.path.join(RAW_DIR, fname), os.path.join(EXCLUDED_DIR, fname))
        except:
            pass

# 2. Two-stage processing
def extract_candidates(img):
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    data = pytesseract.image_to_data(gray, output_type=pytesseract.Output.DICT)
    
    lines = {}
    for i in range(len(data['level'])):
        if data['text'][i].strip() == '':
            continue
        line_num = data['line_num'][i]
        block_num = data['block_num'][i]
        key = (block_num, line_num)
        
        if key not in lines:
            lines[key] = {
                'text': [data['text'][i]],
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
            
    return list(lines.values())

def select_best_candidate(candidates):
    best_box = None
    best_score = -1
    best_text = ""
    
    # Exclude keywords that indicate wrong lines
    exclude_keywords = ['tracking', 'no', 'h.no', 'house', 'address', 'awb', 'pin', 'pincode']
    
    # Exclude tracking number prefixes
    tracking_prefixes = ['d', 'z', 'x', '7d']
    
    for cand in candidates:
        text = ' '.join(cand['text'])
        text_lower = text.lower()
        
        # If it has tracking no, address etc, skip it
        if any(kw in text_lower for kw in exclude_keywords):
            continue
            
        # Check if the first alphanumeric part starts with any of the tracking prefixes
        alphanumeric_words = [w for w in text_lower.split() if any(c.isalnum() for c in w)]
        if alphanumeric_words:
            first_word = alphanumeric_words[0]
            if any(first_word.startswith(prefix) for prefix in tracking_prefixes):
                continue
            
        digits = re.findall(r'\d', text)
        letters = re.findall(r'[a-zA-Z]', text)
        
        score = 0
        
        # We want lines that are PRIMARILY a 10-digit number
        if len(digits) == 10:
            score += 50
        elif 10 <= len(digits) <= 12:
            score += 30
            
        # Penalize if too many letters (meaning it's probably an address line)
        if len(letters) > 5:
            score -= 20
            
        if 'ph' in text_lower or 'mob' in text_lower or 'contact' in text_lower:
            score += 20
            
        # Phone number strict pattern
        if re.search(r'\b\d{10}\b', text):
            score += 100
            
        if score > best_score and score > 0:
            best_score = score
            best_box = cand
            best_text = text
            
    return best_box, best_text, best_score

import random

review_needed = []
all_results = []
count_cropped = 0

files = os.listdir(RAW_DIR)
for fname in files:
    if not fname.lower().endswith(('.jpg', '.jpeg', '.png', '.heic')): continue
    
    img_path = os.path.join(RAW_DIR, fname)
    img = cv2.imread(img_path)
    if img is None: continue
    
    candidates = extract_candidates(img)
    best_cand, best_text, score = select_best_candidate(candidates)
    
    if best_cand and score >= 30: # confident score threshold
        l, t, r, b = best_cand['left'], best_cand['top'], best_cand['right'], best_cand['bottom']
        pad = 15
        h, w = img.shape[:2]
        l = max(0, l - pad)
        t = max(0, t - pad)
        r = min(w, r + pad)
        b = min(h, b + pad)
        
        crop = img[t:b, l:r]
        crop_path = os.path.join(CROP_DIR, fname)
        cv2.imwrite(crop_path, crop)
        
        all_results.append((fname, crop, best_text, 'success'))
        count_cropped += 1
    else:
        review_needed.append(fname)
        all_results.append((fname, None, "", 'review'))
        
with open('review_needed.csv', 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['filename'])
    for fname in review_needed:
        writer.writerow([fname])

# Generate NEW sample grid of 25 random crops
random.shuffle(all_results)
sample_crops = []
sample_labels = []

# We will just show the ones that succeeded in the grid, or maybe show full image for failed ones?
# Prompt says "include a mix of photos that previously succeeded and previously got flagged"
# We'll just pick 25 random SUCCESSFUL crops to show the precision improvement.
for res in all_results:
    if res[3] == 'success':
        sample_crops.append(res[1])
        sample_labels.append(res[0])
    if len(sample_crops) == 25:
        break

if len(sample_crops) > 0:
    n = len(sample_crops)
    cols = 5
    rows = (n + cols - 1) // cols
    fig, axes = plt.subplots(rows, cols, figsize=(20, 4 * rows))
    axes = axes.flatten()
    for i in range(len(axes)):
        if i < n:
            axes[i].imshow(cv2.cvtColor(sample_crops[i], cv2.COLOR_BGR2RGB))
            axes[i].set_title(sample_labels[i], fontsize=8)
            axes[i].axis('off')
        else:
            axes[i].axis('off')
    
    plt.tight_layout()
    plt.savefig('new_sample_crops_grid.png')

print(f"Cropped {count_cropped} images.")
print(f"Flagged {len(review_needed)} for review.")
