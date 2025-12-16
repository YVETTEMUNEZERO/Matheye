package com.example.matheye

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// Data class to hold rich recognition results
data class RecognitionResult(
    val symbol: String,           // The Unicode symbol
    val latex: String,            // LaTeX representation
    val confidence: Float,        // Confidence score (0-1)
    val isRecognized: Boolean,    // Whether it passed threshold
    val originalSize: String      // Original bitmap size
)

class TFLiteClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null
    var initializationError: String? = null
        private set

    private var labels: Map<String, String>? = null
    private var reverseMapping: Map<String, Int>? = null

    val isInitialized: Boolean
        get() = interpreter != null && labels != null && reverseMapping != null

    // Make threshold configurable
    var confidenceThreshold: Float = 0.8f // Match your training threshold!

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            try {
                val modelBuffer = loadModelFile()
                interpreter = Interpreter(modelBuffer)
                loadLabelsAndMapping()
                initializationError = null
            } catch (e: Exception) {
                e.printStackTrace()
                initializationError = e.message
            }
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd("math_ocr_model.tflite")
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabelsAndMapping() {
        try {
            // Load labels.json
            val labelsInputStream: InputStream = context.assets.open("labels.json")
            val labelsJsonString = labelsInputStream.bufferedReader().use { it.readText() }
            val labelsJson = JSONObject(labelsJsonString)
            val tempLabels = mutableMapOf<String, String>()
            labelsJson.keys().forEach { key ->
                val latex = labelsJson.getJSONObject(key).getString("latex")
                tempLabels[key] = latex
            }
            labels = tempLabels

            // Load reverse_mapping.json
            val reverseMappingInputStream: InputStream = context.assets.open("reverse_mapping.json")
            val reverseMappingJsonString = reverseMappingInputStream.bufferedReader().use { it.readText() }
            val reverseMappingJson = JSONObject(reverseMappingJsonString)
            val tempReverseMapping = mutableMapOf<String, Int>()
            reverseMappingJson.keys().forEach { key ->
                val value = reverseMappingJson.getInt(key)
                tempReverseMapping[key] = value
            }
            reverseMapping = tempReverseMapping
        } catch (e: FileNotFoundException) {
            throw Exception("Make sure 'labels.json' and 'reverse_mapping.json' are in the assets folder.", e)
        }
    }

    /**
     * NEW: Enhanced recognition that returns rich result object
     * Use this for better UI presentation
     */
    fun recognizeImageEnhanced(bitmap: Bitmap): RecognitionResult {
        if (!isInitialized) {
            return RecognitionResult(
                symbol = "⚠️",
                latex = "",
                confidence = 0f,
                isRecognized = false,
                originalSize = getSymbolSize(bitmap)
            )
        }

        try {
            val inputImageWidth = 32
            val inputImageHeight = 32
            val modelOutputClasses = 369

            // Preprocess image
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputImageWidth, inputImageHeight, true)
            val inputBuffer = ByteBuffer.allocateDirect(1 * inputImageWidth * inputImageHeight * 1 * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }
            val intValues = IntArray(inputImageWidth * inputImageHeight)
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
            for (pixelValue in intValues) {
                val gray = (pixelValue shr 16 and 0xFF) * 0.299f + (pixelValue shr 8 and 0xFF) * 0.587f + (pixelValue and 0xFF) * 0.114f
                inputBuffer.putFloat(gray / 255.0f)
            }

            // Run inference
            val outputBuffer = ByteBuffer.allocateDirect(1 * modelOutputClasses * 4).apply {
                order(ByteOrder.nativeOrder())
                rewind()
            }

            interpreter!!.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val probabilities = FloatArray(modelOutputClasses)
            outputBuffer.asFloatBuffer().get(probabilities)

            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val confidence = if (maxIndex != -1) probabilities[maxIndex] else 0.0f

            // Check threshold
            return if (confidence >= confidenceThreshold) {
                val originalId = reverseMapping!![maxIndex.toString()]?.toString()
                val latex = originalId?.let { labels!![it] } ?: "Unknown"
                val unicode = getUnicodeFromLatex(latex)

                RecognitionResult(
                    symbol = unicode,
                    latex = latex,
                    confidence = confidence,
                    isRecognized = true,
                    originalSize = getSymbolSize(bitmap)
                )
            } else {
                RecognitionResult(
                    symbol = "❌",
                    latex = "",
                    confidence = confidence,
                    isRecognized = false,
                    originalSize = getSymbolSize(bitmap)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return RecognitionResult(
                symbol = "⚠️",
                latex = "Error: ${e.message}",
                confidence = 0f,
                isRecognized = false,
                originalSize = getSymbolSize(bitmap)
            )
        }
    }

    /**
     * LEGACY: Keep for backward compatibility
     * Returns simple string message
     */
    fun recognizeImage(bitmap: Bitmap): String {
        val result = recognizeImageEnhanced(bitmap)

        return if (result.isRecognized) {
            result.symbol
        } else if (result.symbol == "⚠️") {
            "Error: ${result.latex}"
        } else {
            "⚠️ Error: No math symbol recognized. Please try again!"
        }
    }

    fun getSymbolSize(bitmap: Bitmap): String {
        return "${bitmap.width} × ${bitmap.height}px"
    }

    private fun getUnicodeFromLatex(latex: String): String {
        val map = mapOf(
            "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
            "\\epsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ",
            "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ",
            "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ",
            "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
            "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",

            // Greek uppercase
            "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
            "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Phi" to "Φ",
            "\\Psi" to "Ψ", "\\Omega" to "Ω",

            // Mathematical operators
            "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\oint" to "∮",
            "\\infty" to "∞", "\\partial" to "∂", "\\nabla" to "∇",
            "\\pm" to "±", "\\mp" to "∓", "\\times" to "×", "\\div" to "÷",
            "\\cdot" to "·", "\\ast" to "∗",

            // Basic relations
            "\\neq" to "≠", "\\leq" to "≤", "\\geq" to "≥", "\\ll" to "≪", "\\gg" to "≫",
            "\\approx" to "≈", "\\equiv" to "≡", "\\sim" to "∼", "\\simeq" to "≃",
            "\\propto" to "∝",

            // Extended relations
            "\\leqslant" to "⩽", "\\geqslant" to "⩾",
            "\\nleq" to "≰", "\\ngeq" to "≱",
            "\\nless" to "≮", "\\ngtr" to "≯",
            "\\lneq" to "⪇", "\\gneq" to "⪈",
            "\\lneqq" to "≨", "\\gneqq" to "≩",
            "\\lesssim" to "≲", "\\gtrsim" to "≳",
            "\\lessapprox" to "⪅", "\\lessdot" to "⋖", "\\gtrdot" to "⋗",
            "\\triangleq" to "≜", "\\doteq" to "≐",
            "\\fallingdotseq" to "≒", "\\risingdotseq" to "≓",
            "\\eqcirc" to "≖", "\\circeq" to "≗",
            "\\cong" to "≅", "\\ncong" to "≇",

            // Set theory
            "\\subset" to "⊂", "\\supset" to "⊃",
            "\\subseteq" to "⊆", "\\supseteq" to "⊇",
            "\\nsubset" to "⊄", "\\nsupset" to "⊅",
            "\\nsubseteq" to "⊈", "\\nsupseteq" to "⊉",
            "\\subsetneq" to "⊊", "\\supsetneq" to "⊋",
            "\\sqsubset" to "⊏", "\\sqsupset" to "⊐",
            "\\sqsubseteq" to "⊑", "\\sqsupseteq" to "⊒",
            "\\in" to "∈", "\\notin" to "∉", "\\ni" to "∋",
            "\\emptyset" to "∅", "\\varnothing" to "∅",
            "\\cap" to "∩", "\\cup" to "∪",
            "\\setminus" to "∖", "\\smallsetminus" to "∖",

            // Number sets
            "\\N" to "ℕ", "\\Z" to "ℤ", "\\Q" to "ℚ",
            "\\R" to "ℝ", "\\C" to "ℂ",
            "\\mathbb{N}" to "ℕ", "\\mathbb{Z}" to "ℤ",
            "\\mathbb{Q}" to "ℚ", "\\mathbb{R}" to "ℝ",
            "\\mathbb{C}" to "ℂ", "\\mathbb{P}" to "ℙ",
            "\\mathbb{H}" to "ℍ",

            // Logic
            "\\forall" to "∀", "\\exists" to "∃", "\\neg" to "¬",
            "\\wedge" to "∧", "\\vee" to "∨",

            // Arrows
            "\\rightarrow" to "→", "\\leftarrow" to "←",
            "\\uparrow" to "↑", "\\downarrow" to "↓",
            "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐",
            "\\Uparrow" to "⇑", "\\Downarrow" to "⇓",
            "\\leftrightarrow" to "↔", "\\Leftrightarrow" to "⇔",
            "\\to" to "→", "\\gets" to "←",
            "\\mapsto" to "↦", "\\longmapsto" to "⟼",
            "\\longrightarrow" to "⟶", "\\longleftarrow" to "⟵",
            "\\longleftrightarrow" to "⟷",
            "\\Longrightarrow" to "⟹", "\\Longleftarrow" to "⟸",
            "\\Longleftrightarrow" to "⟺",
            "\\nearrow" to "↗", "\\searrow" to "↘",
            "\\swarrow" to "↙", "\\nwarrow" to "↖",
            "\\hookleftarrow" to "↩", "\\hookrightarrow" to "↪",
            "\\leftharpoonup" to "↼", "\\rightharpoonup" to "⇀",
            "\\leftharpoondown" to "↽", "\\rightharpoondown" to "⇁",
            "\\rightleftharpoons" to "⇌",

            // Geometry
            "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
            "\\triangle" to "△", "\\measuredangle" to "∡",
            "\\sphericalangle" to "∢", "\\nparallel" to "∦",

            // Other mathematical symbols
            "\\sqrt" to "√", "\\degree" to "°", "\\prime" to "′",
            "\\doubleprime" to "″", "\\tripleprime" to "‴",
            "\\backprime" to "‵",

            // Measurement symbols
            "\\ohm" to "Ω", "\\mho" to "℧",
            "\\angstrom" to "Å", "\\micro" to "µ",

            // Currency
            "\\$" to "$", "\\cent" to "¢",
            "\\pound" to "£", "\\yen" to "¥",
            "\\euro" to "€",

            // Common fractions
            "\\frac{1}{2}" to "½", "\\frac{1}{3}" to "⅓", "\\frac{2}{3}" to "⅔",
            "\\frac{1}{4}" to "¼", "\\frac{3}{4}" to "¾", "\\frac{1}{5}" to "⅕",
            "\\frac{1}{8}" to "⅛",

            // Superscripts
            "^0" to "⁰", "^1" to "¹", "^2" to "²", "^3" to "³", "^4" to "⁴",
            "^5" to "⁵", "^6" to "⁶", "^7" to "⁷", "^8" to "⁸", "^9" to "⁹",
            "^+" to "⁺", "^-" to "⁻", "^=" to "⁼", "^n" to "ⁿ", "^i" to "ⁱ",

            // Subscripts
            "_0" to "₀", "_1" to "₁", "_2" to "₂", "_3" to "₃", "_4" to "₄",
            "_5" to "₅", "_6" to "₆", "_7" to "₇", "_8" to "₈", "_9" to "₉",
            "_+" to "₊", "_-" to "₋", "_=" to "₌", "_n" to "ₙ", "_i" to "ᵢ",

            // Numbers
            "0" to "0", "1" to "1", "2" to "2", "3" to "3", "4" to "4",
            "5" to "5", "6" to "6", "7" to "7", "8" to "8", "9" to "9",

            // Lowercase letters
            "a" to "a", "b" to "b", "c" to "c", "d" to "d", "e" to "e",
            "f" to "f", "g" to "g", "h" to "h", "i" to "i", "j" to "j",
            "k" to "k", "l" to "l", "m" to "m", "n" to "n", "o" to "o",
            "p" to "p", "q" to "q", "r" to "r", "s" to "s", "t" to "t",
            "u" to "u", "v" to "v", "w" to "w", "x" to "x", "y" to "y", "z" to "z",

            // Uppercase letters
            "A" to "A", "B" to "B", "C" to "C", "D" to "D", "E" to "E",
            "F" to "F", "G" to "G", "H" to "H", "I" to "I", "J" to "J",
            "K" to "K", "L" to "L", "M" to "M", "N" to "N", "O" to "O",
            "P" to "P", "Q" to "Q", "R" to "R", "S" to "S", "T" to "T",
            "U" to "U", "V" to "V", "W" to "W", "X" to "X", "Y" to "Y", "Z" to "Z",

            // Basic operators and punctuation
            "+" to "+", "-" to "-", "=" to "=", "<" to "<", ">" to ">",
            "(" to "(", ")" to ")", "[" to "[", "]" to "]", "{" to "{", "}" to "}",
            "/" to "/", "*" to "*", "^" to "^", "_" to "_",
            "!" to "!", "?" to "?", "." to ".", "," to ",", ":" to ":", ";" to ";",
            "\\celsius" to "°C", "\\fahrenheit" to "°F", "\\mathscr{S}" to "S"
        )
        return map[latex] ?: latex
    }

    fun close() {
        interpreter?.close()
    }
}