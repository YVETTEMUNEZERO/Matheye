// ============================================
// DOM ELEMENTS
// ============================================
const video = document.getElementById('video');
const canvas = document.getElementById('canvas');
const context = canvas.getContext('2d');
const startCameraBtn = document.getElementById('startCamera');
const captureBtn = document.getElementById('captureBtn');
const uploadBtn = document.getElementById('uploadBtn');
const fileInput = document.getElementById('fileInput');
const recognizeBtn = document.getElementById('recognizeBtn');
const previewSection = document.getElementById('previewSection');
const resultsSection = document.getElementById('resultsSection');
const loading = document.getElementById('loading');
const preview = document.getElementById('preview');
const results = document.getElementById('results');

let stream = null;
let isCameraActive = false;

// ============================================
// LATEX → UNICODE (MINIMAL HOOK)
// ============================================
function latexToText(input) {
    if (!input) return "";
    let output = input;
    Object.keys(latexToUnicode)
        .sort((a, b) => b.length - a.length)
        .forEach(key => {
            output = output.split(key).join(latexToUnicode[key]);
        });
    return output;
}

// ============================================
// CAMERA CONTROLS
// ============================================
startCameraBtn.addEventListener('click', async () => {
    if (!isCameraActive) await startCamera();
    else stopCamera();
});

async function startCamera() {
    try {
        stream = await navigator.mediaDevices.getUserMedia({
            video: {
                facingMode: 'environment',
                width: { ideal: 1280 },
                height: { ideal: 720 }
            }
        });

        video.srcObject = stream;
        video.style.display = 'block';

        isCameraActive = true;
        captureBtn.disabled = false;
        startCameraBtn.innerHTML = `<span class="btn-icon">🔴</span><span class="btn-text">Stop Camera</span>`;
        startCameraBtn.classList.replace('btn-primary', 'btn-secondary');

        console.log('✓ Camera started');
    } catch (err) {
        alert('Camera access denied');
        console.error(err);
    }
}

function stopCamera() {
    if (!stream) return;
    stream.getTracks().forEach(t => t.stop());
    video.srcObject = null;
    video.style.display = 'none';

    isCameraActive = false;
    captureBtn.disabled = true;
    startCameraBtn.innerHTML = `<span class="btn-icon">📷</span><span class="btn-text">Start Camera</span>`;
    startCameraBtn.classList.replace('btn-secondary', 'btn-primary');

    console.log('✓ Camera stopped');
}

// ============================================
// IMAGE CAPTURE
// ============================================
captureBtn.addEventListener('click', () => {
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    context.drawImage(video, 0, 0);

    preview.src = canvas.toDataURL('image/png');
    previewSection.style.display = 'block';
    resultsSection.style.display = 'none';

    previewSection.scrollIntoView({ behavior: 'smooth' });
    
});

// ============================================
// IMAGE UPLOAD
// ============================================
uploadBtn.addEventListener('click', () => fileInput.click());

fileInput.addEventListener('change', e => {
    const file = e.target.files[0];
    if (!file || !file.type.startsWith('image/')) {
        alert('Upload a valid image');
        return;
    }

    const reader = new FileReader();
    reader.onload = ev => {
        preview.src = ev.target.result;
        previewSection.style.display = 'block';
        resultsSection.style.display = 'none';
        previewSection.scrollIntoView({ behavior: 'smooth' });
    };
    reader.readAsDataURL(file);
    
});

// ============================================
// SYMBOL RECOGNITION (SINGLE SOURCE OF TRUTH) - FIX
// ============================================
recognizeBtn.addEventListener('click', async () => {
    loading.style.display = 'block';
    resultsSection.style.display = 'none';
    recognizeBtn.disabled = true;

    try {
        const blob = await (await fetch(preview.src)).blob();
        const formData = new FormData();
        formData.append('image', blob, 'symbol.png');
        
     const response = await fetch('/predict', {
            method: 'POST',
            body: formData
        });

        // 1. Check for bad HTTP status (e.g., 500 Internal Server Error)
        if (!response.ok) {
            // Read the text response for a better error message
            const errorText = await response.text();
            displayError(`Server Error (${response.status}): ${errorText.substring(0, 100)}`);
            throw new Error(`Server returned status ${response.status}`);
        }
        
        // 2. Safely parse JSON. This line is where the crash is happening now.
        // If the server sends an empty body or plain text, response.json() fails.
        const data = await response.json();
        
        loading.style.display = 'none';
        recognizeBtn.disabled = false;
        
        // --- ADDED LOGIC FOR HANDLING SUCCESS:FALSE MESSAGES ---
        if (!data.success) {
            displayError(data.error || data.message || 'Server processing failed.');
            return;
        }

        // --- Handle Low Confidence Failure (e.g., for the 'B' symbol) ---
        if (data.status !== 'recognized') {
            displayError(data.message || 'No symbol recognized');
            return;
        }
        
        // --- ADDED LOGIC FOR HANDLING EMPTY OUTPUT (e.g., for the empty unicode symbols) ---
        // Prioritize unicode, then fallback to rendering the LaTeX string
        let displaySymbol = data.unicode;
        if (!displaySymbol) {
            // Use a function that converts LaTeX to displayable text or KaTeX string
            // Assuming latexToText is defined elsewhere to handle simple strings:
            displaySymbol = data.latex; 
        }

        if (displaySymbol.trim() === "") {
             displayError("Symbol recognized, but the output is empty.");
        } else {
             displaySuccess(displaySymbol);
        }
        
    } catch (err) {
        // This catch handles network failures AND JSON parsing failures.
        loading.style.display = 'none';
        recognizeBtn.disabled = false;
        console.error('Frontend Error during recognition:', err);
        // Update generic message to be more specific
        displayError('Failed to connect to server or parse response.'); 
    }
});
// ============================================
// DISPLAY RESULTS
// ============================================
function copyToClipboard(text) {
    navigator.clipboard.writeText(text)
        .then(() => alert('Copied!'))
        .catch(() => alert('Copy failed'));
}

function displaySuccess(symbol) {
    // Escape single quotes for use in onclick (important if symbol is LaTeX like \alpha)
    const escapedSymbol = symbol.replace(/'/g, "\\'"); 
    
    // Check if the symbol is potentially LaTeX (starts with \ or contains {), 
    // and if so, wrap it in a math-rendering element (like MathJax/KaTeX's display)
    // NOTE: This assumes you have a rendering library on your page for LaTeX.
    const symbolHtml = symbol.startsWith('\\') || symbol.includes('{') 
        ? `<span class="math-render-placeholder">${symbol}</span>` 
        : symbol;

    results.innerHTML = `
        <div class="result-box success-box">
            <h3 class="success-heading">✅ Symbol Recognized!</h3>
            <div class="recognized-symbol-container">
                <div class="recognized-symbol">${symbolHtml}</div>
            </div>
            
            <div class="action-buttons">
                <button class="btn btn-action" onclick="copyToClipboard('${escapedSymbol}')">📋 Copy</button>
                
            </div>
        </div>
    `;
    resultsSection.style.display = 'block';
    resultsSection.scrollIntoView({ behavior: 'smooth' });
}

function displayError(message) {
    results.innerHTML = `
        <div class="result-box error-box">
            <h3 class="error-heading">❌ Recognition Failed</h3>
            <p class="message">${message}</p>
            <a href="/" class="btn btn-discard">↩ Try Again</a>
        </div>
    `;
    resultsSection.style.display = 'block';
    resultsSection.scrollIntoView({ behavior: 'smooth' });
}
