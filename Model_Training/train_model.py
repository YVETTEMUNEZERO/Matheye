import os
import json
import cv2
import numpy as np
import pandas as pd
import tensorflow as tf

from datetime import datetime
from tensorflow import keras
from keras import layers, regularizers
from sklearn.model_selection import train_test_split


# ====================================================
# CONFIGURATION
# ====================================================

CONFIG = {
    "dataset_path": "dataset/HASYv2",
    "models_dir": "models",
    "epochs": 50,
    "batch_size": 128,
    "learning_rate": 1e-3,
    "confidence_threshold": 0.8
}


# ====================================================
# DATA LOADING
# ====================================================

def load_hasy_dataset(base_path):
    print("=" * 60)
    print("LOADING HASYv2 DATASET")
    print("=" * 60)

    labels_csv = pd.read_csv(os.path.join(base_path, "hasy-data-labels.csv"))

    images, labels = [], []

    for idx, row in labels_csv.iterrows():
        img_path = os.path.join(base_path, row["path"])
        if not os.path.exists(img_path):
            continue

        img = cv2.imread(img_path, cv2.IMREAD_GRAYSCALE)
        img = cv2.resize(img, (32, 32))
        img = img.astype(np.float32) / 255.0

        images.append(img)
        labels.append(int(row["symbol_id"]))

        if (idx + 1) % 10000 == 0:
            print(f"Loaded {idx + 1} images...")

    images = np.array(images).reshape(-1, 32, 32, 1)
    labels = np.array(labels, dtype=np.int32)

    print(f"✓ Images shape: {images.shape}")
    print(f"✓ Labels shape: {labels.shape}")

    return images, labels, labels_csv


# ====================================================
# LABEL REMAPPING
# ====================================================

def remap_labels(labels):
    unique = np.unique(labels)
    forward = {int(old): int(new) for new, old in enumerate(unique)}
    reverse = {int(new): int(old) for new, old in enumerate(unique)}
    new_labels = np.array([forward[int(l)] for l in labels], dtype=np.int32)
    return new_labels, forward, reverse


# ====================================================
# FULL LaTeX → UNICODE MAPPING (UNCHANGED)
# ====================================================

def create_label_mapping(labels_csv, save_dir):
    os.makedirs(save_dir, exist_ok=True)

    latex_to_unicode = {
        # Greek lowercase
        '\\alpha': 'α', '\\beta': 'β', '\\gamma': 'γ', '\\delta': 'δ',
        '\\epsilon': 'ε', '\\zeta': 'ζ', '\\eta': 'η', '\\theta': 'θ',
        '\\iota': 'ι', '\\kappa': 'κ', '\\lambda': 'λ', '\\mu': 'μ',
        '\\nu': 'ν', '\\xi': 'ξ', '\\pi': 'π', '\\rho': 'ρ',
        '\\sigma': 'σ', '\\tau': 'τ', '\\upsilon': 'υ', '\\phi': 'φ',
        '\\chi': 'χ', '\\psi': 'ψ', '\\omega': 'ω',

        # Greek uppercase
        '\\Gamma': 'Γ', '\\Delta': 'Δ', '\\Theta': 'Θ', '\\Lambda': 'Λ',
        '\\Xi': 'Ξ', '\\Pi': 'Π', '\\Sigma': 'Σ', '\\Phi': 'Φ',
        '\\Psi': 'Ψ', '\\Omega': 'Ω',

        # Operators
        '\\sum': '∑', '\\prod': '∏', '\\int': '∫', '\\oint': '∮',
        '\\infty': '∞', '\\partial': '∂', '\\nabla': '∇',
        '\\pm': '±', '\\mp': '∓', '\\times': '×', '\\div': '÷',
        '\\cdot': '·', '\\ast': '∗',

        # Relations
        '\\neq': '≠', '\\leq': '≤', '\\geq': '≥', '\\ll': '≪', '\\gg': '≫',
        '\\approx': '≈', '\\equiv': '≡', '\\sim': '∼', '\\simeq': '≃',
        '\\propto': '∝',

        # Logic
        '\\forall': '∀', '\\exists': '∃', '\\neg': '¬',
        '\\wedge': '∧', '\\vee': '∨',

        # Sets
        '\\emptyset': '∅', '\\cap': '∩', '\\cup': '∪',
        '\\subset': '⊂', '\\supset': '⊃',
        '\\subseteq': '⊆', '\\supseteq': '⊇',
        '\\in': '∈', '\\notin': '∉', '\\ni': '∋',

        # Arrows
        '\\rightarrow': '→', '\\leftarrow': '←', '\\uparrow': '↑', '\\downarrow': '↓',
        '\\Rightarrow': '⇒', '\\Leftarrow': '⇐',
        '\\leftrightarrow': '↔', '\\Leftrightarrow': '⇔',
        '\\to': '→', '\\gets': '←', '\\mapsto': '↦',

        # Geometry
        '\\angle': '∠', '\\perp': '⊥', '\\parallel': '∥', '\\triangle': '△',

        # Numbers
        **{str(i): str(i) for i in range(10)},

        # Lowercase letters
        **{chr(i): chr(i) for i in range(ord('a'), ord('z') + 1)},

        # Uppercase letters
        **{chr(i): chr(i) for i in range(ord('A'), ord('Z') + 1)},

        # Basic symbols
        '+': '+', '-': '-', '=': '=', '<': '<', '>': '>',
        '(': '(', ')': ')', '[': '[', ']': ']',
        '{': '{', '}': '}', '/': '/', '*': '*',
        '^': '^', '_': '_', '!': '!', '?': '?',
        '.': '.', ',': ',', ':': ':', ';': ';'
    }

    label_map = {}

    for _, row in labels_csv.iterrows():
        sid = str(int(row["symbol_id"]))
        if sid in label_map:
            continue

        latex = str(row["latex"])
        unicode_val = str(row.get("unicode", ""))

        if not unicode_val or unicode_val == "nan":
            unicode_val = latex_to_unicode.get(latex, "")

        label_map[sid] = {
            "latex": latex,
            "unicode": unicode_val
        }

    with open(os.path.join(save_dir, "labels.json"), "w", encoding="utf-8") as f:
        json.dump(label_map, f, ensure_ascii=False, indent=2)

    print(f"✓ labels.json saved ({len(label_map)} classes)")
    return label_map


# ====================================================
# MODEL (BALANCED CNN)
# ====================================================

def build_model(num_classes):
    data_augmentation = keras.Sequential([
        layers.RandomRotation(0.15),
        layers.RandomZoom(0.15),
        layers.RandomTranslation(0.1, 0.1),
        layers.RandomContrast(0.25),
    ])

    inputs = keras.Input(shape=(32, 32, 1))
    x = data_augmentation(inputs)

    for filters in [32, 64, 128]:
        x = layers.Conv2D(filters, 3, padding="same", use_bias=False)(x)
        x = layers.BatchNormalization()(x)
        x = layers.ReLU()(x)
        x = layers.MaxPooling2D()(x)

    x = layers.Conv2D(256, 3, padding="same", use_bias=False)(x)
    x = layers.BatchNormalization()(x)
    x = layers.ReLU()(x)

    x = layers.GlobalAveragePooling2D()(x)

    x = layers.Dense(256, activation="relu",
                     kernel_regularizer=regularizers.l2(1e-4))(x)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.5)(x)

    outputs = layers.Dense(num_classes, activation="softmax")(x)
    return keras.Model(inputs, outputs, name="MathEye_CNN_Balanced")


# ====================================================
# TRAINING
# ====================================================

def train_model(images, labels):
    num_classes = len(np.unique(labels))
    labels_cat = keras.utils.to_categorical(labels, num_classes)

    X_train, X_val, y_train, y_val = train_test_split(
        images, labels_cat,
        test_size=0.2,
        stratify=labels,
        random_state=42
    )

    print(f"Training samples: {X_train.shape[0]}")
    print(f"Validation samples: {X_val.shape[0]}")

    model = build_model(num_classes)

    # Use simple float learning rate (not schedule)
    optimizer = keras.optimizers.Adam(
        learning_rate=CONFIG["learning_rate"]
    )

    loss_fn = keras.losses.CategoricalCrossentropy(label_smoothing=0.1)

    model.compile(optimizer=optimizer, loss=loss_fn, metrics=["accuracy"])
    
    model.summary()

    callbacks = [
        keras.callbacks.ModelCheckpoint(
            os.path.join(CONFIG["models_dir"], "best_model.keras"),
            monitor="val_accuracy",
            save_best_only=True,
            verbose=1
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=5,
            min_lr=1e-6,
            verbose=1
        ),
        keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=10,
            restore_best_weights=True,
            verbose=1
        )
    ]

    print("\n🚀 Starting training...")
    print("="*60)

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=CONFIG["epochs"],
        batch_size=CONFIG["batch_size"],
        callbacks=callbacks,
        verbose=1
    )

    # Evaluate
    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    print(f"\n✓ Final Validation Accuracy: {val_acc*100:.2f}%")

    return model

# ====================================================
# SAVE MODELS
# ====================================================

def save_all(model, reverse_mapping):
    os.makedirs(CONFIG["models_dir"], exist_ok=True)

    model.save(os.path.join(CONFIG["models_dir"], "math_ocr_model.keras"))
    model.save(os.path.join(CONFIG["models_dir"], "math_ocr_model.h5"))

    with open(os.path.join(CONFIG["models_dir"], "reverse_mapping.json"), "w") as f:
        json.dump({str(k): int(v) for k, v in reverse_mapping.items()}, f, indent=2)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(os.path.join(CONFIG["models_dir"], "math_ocr_model.tflite"), "wb") as f:
        f.write(tflite_model)

    print("✓ Models saved (.keras, .h5, .tflite)")


# ====================================================
# MAIN
# ====================================================

def main():
    images, labels, labels_csv = load_hasy_dataset(CONFIG["dataset_path"])
    create_label_mapping(labels_csv, CONFIG["models_dir"])
    labels, _, reverse_mapping = remap_labels(labels)
    model = train_model(images, labels)
    save_all(model, reverse_mapping)
    print("\n✓ TRAINING COMPLETE")


if __name__ == "__main__":
    main()
