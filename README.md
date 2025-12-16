## Matheye

Matheye is an end‑to‑end math OCR project that lets you capture handwritten mathematical expressions, recognize them using a deep‑learning model, and serve the results through both an Android app and a web interface.

The repository is organized into three main parts:

- **`MathEye/`** – Android app (Kotlin, Android Studio/Gradle) that runs the OCR model on‑device.
- **`Model_Training/`** – Python training pipeline for the math OCR model using the HASYv2 dataset.
- **`web_app/`** – Lightweight web app (Python, likely Flask) that exposes the trained model via a browser UI.

---

## Features

- **Handwritten math OCR** using a trained deep‑learning model.
- **Android mobile app** for capturing expressions via the camera and running inference locally.
- **Web application** for uploading images or using the camera via the browser.
- **Reusable training pipeline** to retrain/improve the model using HASYv2 or similar symbol datasets.

---

## Project Structure

At the top level:

- **`MathEye/`** – Android Studio project
  - **`app/src/main/`**
    - **`assets/`** – Deployed model files such as:
      - `math_ocr_model.tflite`
      - `labels.json`
      - `reverse_mapping.json`
    - **`java/com/...`** – Kotlin source code for activities, camera handling, and inference.
    - **`res/`** – Android resources (layouts, drawables, icons, etc.).
- **`Model_Training/`**
  - **`dataset/HASYv2/`** – HASYv2 dataset utilities and data.
  - **`Models/`** – Saved models and configuration:
    - `math_ocr_model.h5`, `math_ocr_model.keras`, `math_ocr_model.tflite`
    - `labels.json`, `reverse_mapping.json`
  - **`train_model.py`** – Main training script.
- **`web_app/`**
  - `app.py` – Web app entry point.
  - `requirements.txt` – Python dependencies.
  - `templates/index.html` – Main UI.
  - `static/` – CSS, JavaScript, and images.

---

## Prerequisites

### Common

- **Git** – to clone and manage the repository.
- **Python 3.9+** – for model training and the web app.

### Android App (`MathEye/`)

- **Android Studio (latest stable)** with:
  - Android SDK
  - Kotlin support

### Model Training (`Model_Training/`)

- Python 3.9+
- Recommended: a virtual environment (`venv`, `conda`, etc.).
- GPU with CUDA (optional but recommended) if you plan to train from scratch.

### Web App (`web_app/`)

- Python 3.9+

---

## Getting Started

### 1. Clone the Repository

From a terminal or PowerShell:

```bash
git clone https://github.com/YVETTEMUNEZERO/Matheye.git
cd Matheye
```

If you are working from a local folder first (e.g. `C:\Users\USER\Documents\Matheye`), see the **Pushing to GitHub** section below.

---

### 2. Run the Android App (`MathEye/`)

1. **Open in Android Studio**
   - Start Android Studio.
   - Choose **Open** and select the `MathEye` folder.
2. **Let Gradle sync** and install any suggested SDK/platform updates.
3. **Build and run**
   - Connect an Android device (or use an emulator).
   - Click **Run** to build and install the app.

---

### 3. Train / Retrain the Model (`Model_Training/`)

1. **Create and activate a virtual environment** (optional but recommended):

   ```bash
   cd Model_Training
   python -m venv .venv
   .venv\Scripts\activate  # On Windows
   # source .venv/bin/activate  # On macOS/Linux
   ```

2. **Install dependencies**

   If you have a separate requirements file, install from it. Otherwise, install common packages manually, for example:

   ```bash
   pip install tensorflow numpy pandas scikit-learn matplotlib
   ```

3. **Prepare dataset**
   - Ensure the HASYv2 dataset is available under `dataset/HASYv2/` (as currently organized).

4. **Run training**

   ```bash
   python train_model.py
   ```

   The trained models and related files will be saved in the `Models/` directory (e.g. `.h5`, `.keras`, `.tflite`, `labels.json`, `reverse_mapping.json`).

5. **Deploy updated model**
   - Copy the new `math_ocr_model.tflite`, `labels.json`, and `reverse_mapping.json` into:
     - `MathEye/app/src/main/assets/` for the Android app.
     - `web_app/models/` for the web app (see below).

---

### 4. Run the Web App (`web_app/`)

1. **Create and activate a virtual environment** (optional but recommended):

   ```bash
   cd web_app
   python -m venv .venv
   .venv\Scripts\activate  # On Windows
   # source .venv/bin/activate  # On macOS/Linux
   ```

2. **Install dependencies**

   ```bash
   pip install -r requirements.txt
   ```

3. **Start the server**

   ```bash
   python app.py
   ```

4. **Open the app**
   - Visit `http://localhost:5000` (or the port printed in the console) in your browser.

---

## Pushing This Project to GitHub

If you already have this project locally (for example at `C:\Users\USER\Documents\Matheye`) and want to push it to the **Matheye** repository on GitHub (`https://github.com/YVETTEMUNEZERO/Matheye`), follow these steps in **PowerShell**:

1. **Navigate to the project root**

   ```powershell
   cd "C:\Users\USER\Documents\Matheye"
   ```

2. **Initialize Git (only once)**

   ```powershell
   git init
   ```

3. **Configure your Git identity (only if not set globally)**

   ```powershell
   git config user.name "YVETTEMUNEZERO"
   git config user.email "your-email@example.com"
   ```

4. **Create a `.gitignore` (recommended)**

   At minimum, ignore build artifacts, virtual environments, and large dataset files (for example: `MathEye/app/build/`, `Model_Training/dataset/HASYv2/hasy-data/`, `web_app/uploads/`, `.venv/`). You can create or edit `.gitignore` at the root before committing.

5. **Stage all files**

   ```powershell
   git add .
   ```

6. **Commit**

   ```powershell
   git commit -m "Initial commit: Matheye project"
   ```

7. **Add the remote pointing to GitHub**

   ```powershell
   git remote add origin https://github.com/YVETTEMUNEZERO/Matheye.git
   ```

8. **Push to GitHub**

   If the GitHub repository is empty (as on `https://github.com/YVETTEMUNEZERO/Matheye`), push your `main` branch:

   ```powershell
   git branch -M main
   git push -u origin main
   ```

After this, your local project will be linked to the GitHub repository. Future changes can be pushed with:

```powershell
git add .
git commit -m "Describe your changes"
git push
```

---

## License

Add your preferred license here (for example, MIT). Create a `LICENSE` file at the root if you plan to open‑source this project.

---

## Contact

If you have questions or suggestions about Matheye, feel free to open an issue on the GitHub repository: `https://github.com/YVETTEMUNEZERO/Matheye`.


