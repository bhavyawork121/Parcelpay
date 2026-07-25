#!/bin/bash
API_KEY="${GEMINI_API_KEY:?Set GEMINI_API_KEY env var}"
URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${API_KEY}"

# Create a small dummy base64 image (1x1 pixel)
IMG_B64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

cat << INNER_EOF > payload.json
{
  "contents": [
    {
      "parts": [
        {
          "text": "Return ONLY a JSON object with fields: phone_number, recipient_name, address, confidence, raw_text_seen"
        },
        {
          "inline_data": {
            "mime_type": "image/png",
            "data": "${IMG_B64}"
          }
        }
      ]
    }
  ]
}
INNER_EOF

curl -s -X POST -H "Content-Type: application/json" -d @payload.json $URL
