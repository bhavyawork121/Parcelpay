import os
import csv
import tkinter as tk
from tkinter import messagebox
from PIL import Image, ImageTk

CROPPED_DIR = './data/cropped'
LABELS_FILE = 'true_labels.csv'

class LabelerApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Phone Number Labeler")
        self.root.geometry("800x600")
        
        self.images = [f for f in os.listdir(CROPPED_DIR) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        self.images.sort()
        
        self.labeled = {}
        if os.path.exists(LABELS_FILE):
            with open(LABELS_FILE, 'r') as f:
                reader = csv.reader(f)
                next(reader, None)  # Skip header
                for row in reader:
                    if len(row) >= 2:
                        self.labeled[row[0]] = row[1]
                        
        self.remaining_images = [img for img in self.images if img not in self.labeled]
        self.current_idx = 0
        
        if not self.remaining_images:
            messagebox.showinfo("Done", "All images have been labeled!")
            self.root.destroy()
            return
            
        # UI Elements
        self.info_label = tk.Label(root, text="", font=("Arial", 12))
        self.info_label.pack(pady=10)
        
        self.image_label = tk.Label(root)
        self.image_label.pack(pady=10)
        
        self.entry_var = tk.StringVar()
        self.entry = tk.Entry(root, textvariable=self.entry_var, font=("Arial", 24), width=15)
        self.entry.pack(pady=10)
        self.entry.bind("<Return>", lambda e: self.next_image())
        
        btn_frame = tk.Frame(root)
        btn_frame.pack(pady=10)
        
        self.skip_btn = tk.Button(btn_frame, text="Skip (Unreadable)", command=self.skip_image, font=("Arial", 14))
        self.skip_btn.pack(side=tk.LEFT, padx=10)
        
        self.next_btn = tk.Button(btn_frame, text="Next (Enter)", command=self.next_image, font=("Arial", 14), bg="green")
        self.next_btn.pack(side=tk.LEFT, padx=10)
        
        self.load_image()
        
    def load_image(self):
        if self.current_idx >= len(self.remaining_images):
            messagebox.showinfo("Done", "All images have been labeled!")
            self.root.destroy()
            return
            
        img_name = self.remaining_images[self.current_idx]
        self.info_label.config(text=f"Image {self.current_idx + 1} of {len(self.remaining_images)}: {img_name}")
        
        img_path = os.path.join(CROPPED_DIR, img_name)
        image = Image.open(img_path)
        
        # Resize for better visibility while keeping aspect ratio
        basewidth = 600
        wpercent = (basewidth / float(image.size[0]))
        hsize = int((float(image.size[1]) * float(wpercent)))
        image = image.resize((basewidth, hsize), Image.Resampling.LANCZOS)
        
        self.photo = ImageTk.PhotoImage(image)
        self.image_label.config(image=self.photo)
        
        self.entry_var.set("")
        self.entry.focus()
        
    def save_label(self, label_text):
        img_name = self.remaining_images[self.current_idx]
        file_exists = os.path.exists(LABELS_FILE)
        
        with open(LABELS_FILE, 'a', newline='') as f:
            writer = csv.writer(f)
            if not file_exists:
                writer.writerow(['filename', 'phone_number'])
            writer.writerow([img_name, label_text])
            
    def next_image(self):
        label = self.entry_var.get().strip()
        if not label:
            messagebox.showwarning("Empty", "Please enter a number or click Skip.")
            return
            
        self.save_label(label)
        self.current_idx += 1
        self.load_image()
        
    def skip_image(self):
        self.save_label("SKIP")
        self.current_idx += 1
        self.load_image()

if __name__ == "__main__":
    root = tk.Tk()
    app = LabelerApp(root)
    root.mainloop()
