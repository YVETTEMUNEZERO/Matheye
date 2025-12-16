"""
Audit labels.json and fix incorrect mappings
This script identifies and corrects symbols that have wrong LaTeX/Unicode
"""

import json
import os
import pandas as pd

def audit_labels():
    """Audit labels.json for incorrect mappings"""
    
    
    dataset_path = "C:\Users\USER\Documents\HandwrittenMathOCR\Model_Training\dataset\HASYv2"
    
    print("="*70)
    print("AUDITING LABELS.JSON")
    print("="*70)
    print()
    
    # Load current labels.json
    labels_path = os.path.join( "labels.json")
    if not os.path.exists(labels_path):
        print(f"❌ Error: {labels_path} not found!")
        return False
    
    with open(labels_path, 'r', encoding='utf-8') as f:
        labels = json.load(f)
    
    print(f"✓ Loaded {len(labels)} labels")
    
    # Count occurrences of each LaTeX symbol
    latex_counts = {}
    for idx, info in labels.items():
        latex = info.get('latex', '')
        latex_counts[latex] = latex_counts.get(latex, 0) + 1
    
    print("\n" + "="*70)
    print("LATEX SYMBOL FREQUENCY")
    print("="*70)
    
    # Show most common symbols
    sorted_latex = sorted(latex_counts.items(), key=lambda x: x[1], reverse=True)
    
    suspicious = []
    for latex, count in sorted_latex[:20]:
        status = "⚠️  SUSPICIOUS" if count > 50 else "✓"
        print(f"{status} '{latex}': {count} occurrences")
        
        if count > 50:  # Likely incorrect if one symbol appears too many times
            suspicious.append((latex, count))
    
    # Load original HASYv2 data for comparison
    print("\n" + "="*70)
    print("CHECKING AGAINST ORIGINAL DATASET")
    print("="*70)
    
    hasy_csv_path = os.path.join(dataset_path, "hasy-data-labels.csv")
    if not os.path.exists(hasy_csv_path):
        print(f"⚠️  Warning: {hasy_csv_path} not found")
        print("   Cannot verify against original dataset")
        return True
    
    hasy_df = pd.read_csv(hasy_csv_path)
    print(f"✓ Loaded HASYv2 dataset: {len(hasy_df)} samples")
    
    # Get unique symbols from original dataset
    unique_symbols = hasy_df.drop_duplicates(subset=['symbol_id'])
    original_mapping = {}
    
    for _, row in unique_symbols.iterrows():
        sid = int(row['symbol_id'])
        latex = str(row['latex'])
        unicode_val = str(row.get('unicode', ''))
        
        original_mapping[sid] = {
            'latex': latex,
            'unicode': unicode_val if unicode_val != 'nan' else ''
        }
    
    print(f"✓ Original dataset has {len(original_mapping)} unique symbols")
    
    # Load reverse mapping to check
    reverse_mapping_path = os.path.join( "reverse_mapping.json")
    if not os.path.exists(reverse_mapping_path):
        print("⚠️  No reverse_mapping.json found - using direct IDs")
        reverse_mapping = {}
    else:
        with open(reverse_mapping_path, 'r') as f:
            reverse_mapping = json.load(f)
    
    # Compare labels.json with original dataset
    print("\n" + "="*70)
    print("MISMATCHES DETECTED")
    print("="*70)
    
    mismatches = []
    correct = 0
    
    for idx, info in labels.items():
        current_latex = info.get('latex', '')
        
        # Get original symbol_id
        if reverse_mapping:
            original_id = reverse_mapping.get(idx)
        else:
            original_id = int(idx)
        
        if original_id and original_id in original_mapping:
            expected_latex = original_mapping[original_id]['latex']
            
            if current_latex != expected_latex:
                mismatches.append({
                    'index': idx,
                    'original_id': original_id,
                    'current_latex': current_latex,
                    'expected_latex': expected_latex
                })
            else:
                correct += 1
    
    print(f"✓ Correct mappings: {correct}")
    print(f"❌ Incorrect mappings: {len(mismatches)}")
    
    if mismatches:
        print("\nFirst 10 mismatches:")
        for i, m in enumerate(mismatches[:10]):
            print(f"  {i+1}. Index {m['index']} (Original ID {m['original_id']}):")
            print(f"     Current:  '{m['current_latex']}'")
            print(f"     Expected: '{m['expected_latex']}'")
    
    # Offer to fix
    print("\n" + "="*70)
    print("FIX OPTIONS")
    print("="*70)
    
    if mismatches:
        print(f"Found {len(mismatches)} incorrect mappings")
        print("\nWould you like to fix them? (This will regenerate labels.json)")
        
        # Auto-fix for script execution
        return fix_labels(original_mapping, reverse_mapping, models_dir)
    else:
        print("✅ No mismatches found - labels.json is correct!")
        return True


def fix_labels(original_mapping, reverse_mapping, models_dir):
    """Fix labels.json using original dataset"""
    
    print("\n" + "="*70)
    print("FIXING LABELS.JSON")
    print("="*70)
    
    # Comprehensive LaTeX to Unicode mapping
    LATEX_TO_UNICODE = {
        '\\alpha': 'α', '\\beta': 'β', '\\gamma': 'γ', '\\delta': 'δ',
        '\\epsilon': 'ε', '\\zeta': 'ζ', '\\eta': 'η', '\\theta': 'θ',
        '\\lambda': 'λ', '\\mu': 'μ', '\\sigma': 'σ', '\\pi': 'π',
        '\\sum': '∑', '\\int': '∫', '\\infty': '∞', '\\pm': '±',
        '\\times': '×', '\\div': '÷', '\\leq': '≤', '\\geq': '≥',
        '\\neq': '≠', '\\approx': '≈', '\\in': '∈', '\\subset': '⊂',
        '\\forall': '∀', '\\exists': '∃', '\\rightarrow': '→',
        '\\Rightarrow': '⇒', '\\blacksquare': '■', '\\square': '□',
        # Add more as needed
        **{str(i): str(i) for i in range(10)},
        **{chr(i): chr(i) for i in range(ord('a'), ord('z') + 1)},
        **{chr(i): chr(i) for i in range(ord('A'), ord('Z') + 1)},
    }
    
    new_labels = {}
    
    if reverse_mapping:
        # Use reverse mapping
        for remapped_idx, original_id in reverse_mapping.items():
            if original_id in original_mapping:
                latex = original_mapping[original_id]['latex']
                unicode_val = original_mapping[original_id]['unicode']
                
                # If unicode is empty, try to get from mapping
                if not unicode_val or unicode_val == 'nan':
                    unicode_val = LATEX_TO_UNICODE.get(latex, latex)
                
                new_labels[remapped_idx] = {
                    'latex': latex,
                    'unicode': unicode_val
                }
    else:
        # Direct mapping
        for sid, info in original_mapping.items():
            latex = info['latex']
            unicode_val = info['unicode']
            
            if not unicode_val or unicode_val == 'nan':
                unicode_val = LATEX_TO_UNICODE.get(latex, latex)
            
            new_labels[str(sid)] = {
                'latex': latex,
                'unicode': unicode_val
            }
    
    # Backup old labels
    labels_path = os.path.join(models_dir, "labels.json")
    backup_path = os.path.join(models_dir, "labels_broken_backup.json")
    
    if os.path.exists(labels_path):
        os.rename(labels_path, backup_path)
        print(f"✓ Backed up broken labels to: {backup_path}")
    
    # Save fixed labels
    with open(labels_path, 'w', encoding='utf-8') as f:
        json.dump(new_labels, f, ensure_ascii=False, indent=2)
    
    print(f"✓ Saved fixed labels.json: {len(new_labels)} entries")
    
    # Verify fix
    latex_counts = {}
    for info in new_labels.values():
        latex = info['latex']
        latex_counts[latex] = latex_counts.get(latex, 0) + 1
    
    blacksquare_count = latex_counts.get('\\blacksquare', 0)
    
    print("\n" + "="*70)
    print("VERIFICATION")
    print("="*70)
    print(f"\\blacksquare occurrences: {blacksquare_count}")
    
    if blacksquare_count > 10:
        print("⚠️  Still many blacksquare entries - this might be normal")
    else:
        print("✓ blacksquare count looks reasonable")
    
    # Show most common symbols
    sorted_latex = sorted(latex_counts.items(), key=lambda x: x[1], reverse=True)
    print("\nMost common symbols (top 10):")
    for latex, count in sorted_latex[:10]:
        print(f"  '{latex}': {count}")
    
    return True


if __name__ == "__main__":
    success = audit_labels()
    
    if success:
        print("\n" + "="*70)
        print("✅ DONE!")
        print("="*70)
        print("\nNext steps:")
        print("1. Restart Flask: python app.py")
        print("2. Try recognition again")
        print("3. Symbols should now be correctly identified!")
    else:
        print("\n❌ Audit failed - check errors above")
        exit(1)