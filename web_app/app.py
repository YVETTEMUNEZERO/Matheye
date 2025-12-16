from flask import Flask, request, jsonify, render_template
from keras.models import load_model
import numpy as np
from PIL import Image
import io
import json
import os

# Define the minimum confidence level for a "recognized" symbol
CONFIDENCE_THRESHOLD = 0.85

# Simple heuristics to reject blank/noisy images before trusting the model
MIN_FOREGROUND_FRACTION = 0.02   # very little ink -> likely blank/irrelevant
MAX_FOREGROUND_FRACTION = 0.60   # almost full ink -> likely noisy blob/photo
MIN_STD_DEV = 0.05               # low contrast -> likely blank background

app = Flask(__name__)

# Configuration
UPLOAD_FOLDER = 'uploads'
MODEL_PATH = 'models/math_ocr_model.h5'
LABELS_PATH = 'models/labels.json' 

# Create upload folder if it doesn't exist
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Global variables for model and labels
model = None
labels = {}

# Load model and labels at startup
print("="*50)
print("Loading Math OCR Model...")
print("="*50)

try:
    # Set verbose=False to keep terminal clean
    model = load_model(MODEL_PATH, compile=False) 
    print("✓ Model loaded successfully!")
except Exception as e:
    print(f"✗ Error loading model: {e}")
    model = None

try:
    with open(LABELS_PATH, 'r', encoding='utf-8') as f:
        labels = json.load(f)
    print(f"✓ Loaded {len(labels)} symbol labels from {LABELS_PATH}")
except Exception as e:
    print(f"✗ Error loading labels file at {LABELS_PATH}: {e}")
    labels = {}

print("="*50)

def preprocess_image(image_file):
    """
    Preprocess image for model prediction
    
    Args:
        image_file: File object from request
        
    Returns:
        numpy array ready for prediction
    """
    try:
        # Open image and convert to grayscale
        img = Image.open(io.BytesIO(image_file.read())).convert('L')
        
        # Resize to 32x32 (HASYv2 input size)
        img = img.resize((32, 32))
        
        # Convert to numpy array and normalize to 0-1
        img_array = np.array(img) / 255.0
        
        # Reshape for model input: (1, 32, 32, 1)
        img_array = img_array.reshape(1, 32, 32, 1)
        
        return img_array
    
    except Exception as e:
        # Re-raise error to be caught by the /predict route's main try-except block
        raise Exception(f"Error preprocessing image: {str(e)}")

@app.route('/')
def home():
    """Render main page"""
    return render_template('index.html')

@app.route('/predict', methods=['POST'])
def predict():
    """
    Handle image prediction and apply confidence threshold.
    
    Returns: JSON with either a confirmed top prediction or an error status.
    """
    try:
        # 1. Validation Checks (Omitted for brevity, assumed correct)
        if model is None:
             return jsonify({'success': False, 'error': 'Model not loaded. Please check server logs.'}), 500
        if 'image' not in request.files or request.files['image'].filename == '':
             return jsonify({'success': False, 'error': 'No image provided in the request.'}), 400

        file = request.files['image']
        
        # 2. Preprocess and Predict
        img_array = preprocess_image(file)

        # Extra safety: reject obviously blank or noisy uploads before model inference
        flat = img_array.squeeze()
        foreground_fraction = float(np.mean(flat < 0.85))  # proportion of dark pixels
        std_dev = float(np.std(flat))

        if std_dev < MIN_STD_DEV or foreground_fraction < MIN_FOREGROUND_FRACTION or foreground_fraction > MAX_FOREGROUND_FRACTION:
            return jsonify({
                'success': True,
                'status': 'unrecognized',
                'message': 'No math symbol recognised.'
            })

        predictions = model.predict(img_array, verbose=0)
        
        # Get top prediction details
        top_idx = np.argmax(predictions[0])
        top_confidence = float(predictions[0][top_idx])

        # ** CRITICAL FIX **: Use 'None' as default to detect label file mismatch
        symbol_info = labels.get(str(top_idx), None)

        # 3. Label/Data Integrity Check
        if symbol_info is None:
            # The model predicted an index that is not in the labels.json file
            print(f"ERROR: Model predicted index {top_idx}, but no label found.")
            return jsonify({
                'success': False,
                'status': 'data_error',
                'error': f'Label file mismatch. Index {top_idx} not found.'
            }), 500
        
        # 4. Extract data (Use .get() for safety, fallback to "" if key is missing)
        recognized_unicode = symbol_info.get('unicode', '')
        recognized_latex = symbol_info.get('latex', '')
        
        # 5. Apply Confidence Threshold and Return Result
        
        # Scenario A: High Confidence -> Successful Recognition
        if top_confidence >= CONFIDENCE_THRESHOLD:
            # Check for empty symbol data (the original error cause)
            if not recognized_unicode and not recognized_latex:
                 return jsonify({
                     'success': False,
                     'status': 'data_error',
                     'error': 'Label found, but both Unicode and LaTeX strings were empty.'
                 }), 500

            return jsonify({
                'success': True,
                'status': 'recognized',
                'symbol': recognized_unicode, # Used for quick display/copy on frontend
                'unicode': recognized_unicode,
                'latex': recognized_latex,
                'confidence': top_confidence * 100
            })
            
        # Scenario B: Low Confidence -> Symbol NOT Recognized
        else:
            # If confidence is low, we report it as a failure to recognize a symbol clearly.
            # We explicitly set status to 'unrecognized' so the frontend can display the 'Try Again' error state.
            return jsonify({
                'success': True, # Still a successful API call
                'status': 'unrecognized', 
                'message': 'No math symbol recognised.'
            })
    
    # <--- CRITICAL: This 'except' must match the indentation of 'try'
    except Exception as e:
        print(f"Error in prediction: {str(e)}")
        # Catch any unexpected errors (e.g., file reading, model failure)
        return jsonify({
            'success': False,
            'error': f"An internal server error occurred: {str(e)}"
        }), 500
@app.route('/health')
def health():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'model_loaded': model is not None,
        'labels_loaded': len(labels) > 0,
        'num_classes': len(labels)
    })

@app.route('/info')
def info():
    """Get model information"""
    if model is None:
        return jsonify({'error': 'Model not loaded'}), 500
    
    return jsonify({
        'model_name': 'Handwritten Math Symbol Recognition',
        'num_classes': len(labels),
        'input_shape': [32, 32, 1],
        'framework': 'TensorFlow/Keras',
        'dataset': 'HASYv2'
    })

if __name__ == '__main__':
    # Run Flask app
    print("\n🚀 Starting Flask server...")
    print("📱 Open your browser and go to: http://localhost:5000")
    print("Press CTRL+C to stop the server\n")
    
    app.run(debug=True, host='0.0.0.0', port=5000)