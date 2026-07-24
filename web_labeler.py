import os
import csv
from flask import Flask, render_template_string, request, redirect, url_for, send_from_directory

app = Flask(__name__)

CROPPED_DIR = './data/cropped'
LABELS_FILE = 'true_labels.csv'

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Numcheck Labeler</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-color: #0f172a;
            --surface-color: rgba(30, 41, 59, 0.7);
            --border-color: rgba(255, 255, 255, 0.1);
            --text-primary: #f8fafc;
            --text-secondary: #94a3b8;
            --accent-primary: #3b82f6;
            --accent-hover: #2563eb;
            --danger-color: #ef4444;
            --danger-hover: #dc2626;
            --success-color: #10b981;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            font-family: 'Inter', sans-serif;
            background: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 2rem;
        }

        .container {
            background: var(--surface-color);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid var(--border-color);
            border-radius: 24px;
            padding: 3rem;
            width: 100%;
            max-width: 800px;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
            text-align: center;
        }

        .header {
            margin-bottom: 2rem;
        }

        .progress {
            font-size: 0.875rem;
            font-weight: 600;
            color: var(--accent-primary);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            margin-bottom: 0.5rem;
            display: block;
        }

        h2 {
            font-size: 1.5rem;
            font-weight: 600;
            margin-bottom: 0.5rem;
        }

        .filename {
            font-size: 0.875rem;
            color: var(--text-secondary);
            font-family: monospace;
            background: rgba(0,0,0,0.2);
            padding: 0.25rem 0.75rem;
            border-radius: 999px;
            display: inline-block;
        }

        .image-container {
            background: rgba(0, 0, 0, 0.2);
            border-radius: 16px;
            padding: 1.5rem;
            margin-bottom: 2.5rem;
            border: 1px solid var(--border-color);
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 200px;
        }

        img {
            max-width: 100%;
            max-height: 300px;
            border-radius: 8px;
            box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.3);
            transition: transform 0.3s ease;
        }

        img:hover {
            transform: scale(1.02);
        }

        .form-group {
            position: relative;
            margin-bottom: 2rem;
            max-width: 400px;
            margin-left: auto;
            margin-right: auto;
        }

        input[type="text"] {
            width: 100%;
            background: rgba(255, 255, 255, 0.05);
            border: 2px solid var(--border-color);
            color: white;
            font-size: 1.5rem;
            padding: 1rem 1.5rem;
            border-radius: 12px;
            text-align: center;
            transition: all 0.2s ease;
            font-family: 'Inter', sans-serif;
            font-weight: 500;
            letter-spacing: 0.1em;
        }

        input[type="text"]:focus {
            outline: none;
            border-color: var(--accent-primary);
            background: rgba(255, 255, 255, 0.1);
            box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.25);
        }

        input[type="text"]::placeholder {
            color: rgba(255, 255, 255, 0.3);
            letter-spacing: normal;
            font-size: 1.25rem;
        }

        .actions {
            display: flex;
            gap: 1rem;
            justify-content: center;
        }

        button {
            font-family: 'Inter', sans-serif;
            font-size: 1rem;
            font-weight: 600;
            padding: 1rem 2rem;
            border-radius: 12px;
            border: none;
            cursor: pointer;
            transition: all 0.2s ease;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
        }

        .next {
            background-color: var(--accent-primary);
            color: white;
            flex: 2;
            box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.5);
        }

        .next:hover {
            background-color: var(--accent-hover);
            transform: translateY(-2px);
            box-shadow: 0 6px 8px -1px rgba(59, 130, 246, 0.6);
        }

        .next:active {
            transform: translateY(0);
        }

        .skip {
            background-color: transparent;
            color: var(--text-secondary);
            border: 1px solid var(--border-color);
            flex: 1;
        }

        .skip:hover {
            background-color: rgba(239, 68, 68, 0.1);
            color: var(--danger-color);
            border-color: rgba(239, 68, 68, 0.3);
        }
        
        .progress-bar-container {
            width: 100%;
            height: 6px;
            background: rgba(255,255,255,0.1);
            border-radius: 999px;
            margin-top: 1rem;
            overflow: hidden;
        }
        
        .progress-bar {
            height: 100%;
            background: var(--accent-primary);
            border-radius: 999px;
            transition: width 0.3s ease;
        }
    </style>
</head>
<body onload="document.getElementById('label_input').focus()">
    <div class="container">
        <div class="header">
            <span class="progress">Task {{ current_idx + 1 }} of {{ total }}</span>
            <h2>Identify Phone Number</h2>
            <div class="filename">{{ img_name }}</div>
            <div class="progress-bar-container">
                <div class="progress-bar" style="width: {{ ((current_idx) / total) * 100 }}%"></div>
            </div>
        </div>
        
        <div class="image-container">
            <img src="{{ url_for('serve_image', filename=img_name) }}" alt="Crop Image">
        </div>
        
        <form method="POST" action="/submit">
            <input type="hidden" name="filename" value="{{ img_name }}">
            <div class="form-group">
                <input type="text" name="label" id="label_input" autocomplete="off" placeholder="Enter number...">
            </div>
            <div class="actions">
                <button type="submit" name="action" value="skip" class="skip" tabindex="2">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
                    Skip
                </button>
                <button type="submit" name="action" value="next" class="next" tabindex="1">
                    Save & Next
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
                </button>
            </div>
        </form>
    </div>
    
    <script>
        // Simple keyboard shortcuts
        document.addEventListener('keydown', function(e) {
            // If pressing Escape, focus the skip button
            if (e.key === 'Escape') {
                document.querySelector('.skip').focus();
            }
        });
    </script>
</body>
</html>
"""

def get_remaining_images():
    images = [f for f in os.listdir(CROPPED_DIR) if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
    images.sort()
    
    labeled = set()
    if os.path.exists(LABELS_FILE):
        with open(LABELS_FILE, 'r') as f:
            reader = csv.reader(f)
            next(reader, None)
            for row in reader:
                if len(row) >= 2:
                    labeled.add(row[0])
                    
    return [img for img in images if img not in labeled], len(images)

@app.route('/')
def index():
    remaining, total = get_remaining_images()
    if not remaining:
        return "<h2>All done! You can close this tab and stop the server.</h2>"
        
    current_img = remaining[0]
    current_idx = total - len(remaining)
    
    return render_template_string(HTML_TEMPLATE, img_name=current_img, current_idx=current_idx, total=total)

@app.route('/images/<filename>')
def serve_image(filename):
    return send_from_directory(CROPPED_DIR, filename)

@app.route('/submit', methods=['POST'])
def submit():
    filename = request.form['filename']
    action = request.form['action']
    label = request.form['label'].strip()
    
    if action == 'skip' or not label:
        label = "SKIP"
        
    file_exists = os.path.exists(LABELS_FILE)
    with open(LABELS_FILE, 'a', newline='') as f:
        writer = csv.writer(f)
        if not file_exists:
            writer.writerow(['filename', 'phone_number'])
        writer.writerow([filename, label])
        
    return redirect(url_for('index'))

if __name__ == '__main__':
    try:
        import flask
    except ImportError:
        print("Installing Flask...")
        os.system("pip install flask")
        
    print("Starting labeler on http://127.0.0.1:5001")
    app.run(port=5001, debug=False)
