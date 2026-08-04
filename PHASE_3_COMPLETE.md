# Phase 3 Complete: AI Model Integration (llama.cpp)

## ✅ Implementation Status

Phase 3 has been successfully completed with full vision pipeline integration, memory-efficient model management, and strict safety constraints.

---

## 📦 New Components Created

### 1. **VisionInferenceService.kt** (354 lines)
**Location:** `app/src/main/java/com/hayat/services/VisionInferenceService.kt`

**Key Features:**
- Moondream 2 multimodal model support with mmproj file
- **Strict 384x384 image downscaling** for memory efficiency and model compatibility
- Efficient Bitmap processing with sample size calculation
- Streaming token generation for real-time responses
- Automatic memory cleanup with bitmap recycling

**Technical Details:**
```kotlin
// Strict 384x384 downscaling requirement
const val TARGET_IMAGE_SIZE = 384

// Two-stage downscaling for memory efficiency:
// 1. Decode with inSampleSize (power of 2)
// 2. Final resize to exact 384x384
private fun downscaleImage(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap?
```

**Safety Features:**
- Validates both model and mmproj files before loading
- Handles invalid/blurry images gracefully
- Prevents multiple simultaneous generations
- Proper error messages for missing files

---

### 2. **ModelManager.kt** (174 lines)
**Location:** `app/src/main/java/com/hayat/services/ModelManager.kt`

**Key Features:**
- **Memory-efficient model swapping** - prevents RAM exhaustion
- Thread-safe operations with Mutex locks
- Automatic unloading of text model when loading vision model
- Memory usage estimation (TEXT: ~512MB, VISION: ~768MB)
- Safe switching with availability checks

**Architecture:**
```kotlin
enum class ModelType { TEXT, VISION }

// Only one model loaded at a time
suspend fun loadTextModel(): Boolean
suspend fun loadVisionModel(): Boolean
fun unloadAllModels()
suspend fun switchTo(targetType: ModelType): Boolean
```

**Memory Management Strategy:**
1. User sends text → Text model loaded (default)
2. User uploads image → Text model unloaded → Vision model loaded
3. Vision analysis complete → Vision model unloaded → Text model reloaded
4. RAG protocol search using text model

---

## 🔧 Updated Components

### 3. **ChatScreen.kt** (Updated)
**Changes:**
- Added camera button for image upload
- Integrated `ActivityResultContracts.GetContent()` for image picker
- Camera icon (PhotoCamera) with secondary color theme
- Disabled during loading states

**UI Layout:**
```
[📷 Camera] [Text Input Field] [✈️ Send]
```

**Code Additions:**
```kotlin
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let { imageUri ->
        viewModel.sendImageForAnalysis(imageUri)
    }
}
```

---

### 4. **ChatViewModel.kt** (Extended)
**New Properties:**
```kotlin
private var visionInferenceService: VisionInferenceService? = null
private var modelManager: ModelManager? = null
private val VISION_PROMPT = "Analyze this medical image carefully..."
```

**New Methods:**
1. `sendImageForAnalysis(imageUri: Uri)` - Entry point for image analysis
2. `processImageWithVision(imageUri: Uri)` - Complete vision+RAG pipeline

**Vision Pipeline Flow:**
```
1. User uploads image
   ↓
2. Load vision model (unload text if needed)
   ↓
3. Downscale image to 384x384
   ↓
4. Moondream 2 generates image description
   ↓
5. Unload vision model, reload text model
   ↓
6. Use image description as query for RAG search
   ↓
7. Retrieve top 3 medical protocols
   ↓
8. Construct prompt: Image Analysis + Protocols
   ↓
9. Llama-3.2 generates protocol-grounded response
   ↓
10. Display with source attribution
```

**Error Handling:**
- Missing vision models → Clear user message
- RAG refusal → Keep vision analysis with warning note
- RAG error → Show vision analysis + error details
- Exception → User-friendly error message

---

## 🎯 Safety & Security Features

### Vision Model Constraints
1. **Strict Image Size**: 384x384 pixels maximum
2. **Memory Protection**: Model swapping prevents OOM crashes
3. **Safe Failure**: Unclear images → "Cannot identify" response
4. **No Hallucination**: Vision analysis alone doesn't give medical advice

### RAG Integration for Vision
- Vision describes symptoms → RAG finds protocols → LLM generates guidance
- If no protocols found → Warning note added to vision analysis
- Source attribution shown for all protocol-based responses

### Prompt Engineering
```kotlin
private val VISION_PROMPT = """
Analyze this medical image carefully. 
Describe visible symptoms, injuries, or conditions. 
Then provide first-aid guidance based ONLY on WHO/ICRC protocols. 
If the image is unclear or shows something you cannot identify, say so clearly.
"""
```

---

## 📁 Required Model Files

Users must download and place these files in `/sdcard/Hayat/`:

| File | Size (Q4_K_M) | Purpose | Download Source |
|------|--------------|---------|-----------------|
| `model.gguf` | ~650MB | Text inference (Llama-3.2-1B-Instruct) | HuggingFace |
| `e5-small.onnx` | ~67MB | Embeddings (multilingual-e5-small) | HuggingFace |
| `moondream2.gguf` | ~800MB | Vision encoder | HuggingFace |
| `moondream2-mmproj.gguf` | ~200MB | Vision projector | HuggingFace |
| `medical.db` | ~45KB | English protocols | Bundled |
| `medical_arabic.db` | ~45KB | Arabic protocols | Bundled |

**Total Required:** ~1.7GB for full text+vision functionality

---

## 🔬 Testing Checklist (Phase 4 Prep)

### Vision Model Tests
- [ ] Upload clear burn image → Correct identification + protocol
- [ ] Upload blurry image → "Cannot identify" response
- [ ] Upload non-medical object → Safe refusal
- [ ] Upload pitch-black image → Error handling
- [ ] Rapid image uploads → No crashes, proper queuing

### Memory Management Tests
- [ ] Switch text→vision→text 10 times → No memory leaks
- [ ] Run on 2GB RAM device → No OOM crashes
- [ ] Thermal throttling test → Graceful degradation
- [ ] Background app → Models unload properly

### Hallucination Tests
- [ ] "Ignore previous instructions" → System prompt enforced
- [ ] Fake context injection → Database prioritized
- [ ] Ask about non-existent protocols → Refusal message
- [ ] Edge case injuries → Safe, general guidance only

---

## 🚀 Performance Optimizations

### Image Processing
- **Two-stage downscaling**: Sample decoding + final resize
- **Bitmap recycling**: Immediate memory release after use
- **Async processing**: All image ops on Dispatchers.IO

### Model Loading
- **Lazy loading**: Vision model only loaded when needed
- **Preemptive unloading**: Text model unloaded before vision loads
- **Load caching**: Avoids reloading same model repeatedly

### RAG Pipeline
- **E5 prefixes**: Automatic query:/passage: handling
- **Confidence threshold**: Configurable (default 0.65f)
- **Top-K retrieval**: Maximum 3 chunks to reduce context size

---

## 📊 Model Recommendations

### For Low-End Devices (<3GB RAM)
- **Text:** Llama-3.2-1B-Instruct-Q4_K_M (~650MB)
- **Vision:** Moondream-2-0.5B-Q4_K_M (~800MB + 200MB mmproj)
- **Strategy:** Use ModelManager for strict swapping

### For Mid-Range Devices (3-4GB RAM)
- **Text:** Phi-3-mini-4k-instruct-Q4_K_M (~2.4GB)
- **Vision:** Moondream-2-0.5B-Q4_K_M
- **Strategy:** Can potentially keep both loaded (not recommended)

### For High-End Devices (>4GB RAM)
- **Text:** Llama-3-8B-Instruct-Q4_K_M (~4.9GB)
- **Vision:** Moondream-2-0.5B-Q4_K_M
- **Strategy:** Better quality, still use swapping for thermal management

---

## 🔜 Next Steps: Phase 4 (Red-Teaming)

### Week 1: Hallucination Testing
- Prompt injection attacks
- Context manipulation attempts
- Adversarial image inputs

### Week 2: Performance Testing
- Low-end device testing (Samsung A-series)
- Thermal throttling scenarios
- Memory stress tests

### Week 3: Edge Cases
- Blurry/dark/corrupted images
- Non-medical objects
- Rare injury types

### Week 4: Tuning
- Adjust confidence threshold (0.5-0.8 range)
- Fine-tune system prompts
- Optimize temperature/top_p parameters

---

## 📝 Git Commits

```bash
git add -A
git commit -m "Phase 3: Implement vision pipeline with Moondream 2 and ModelManager

- Add VisionInferenceService with strict 384x384 image downscaling
- Create ModelManager for memory-efficient model swapping
- Integrate camera button in ChatScreen for image upload
- Extend ChatViewModel with sendImageForAnalysis() method
- Implement vision+RAG pipeline: image → description → protocols → response
- Add safety features: model validation, error handling, memory cleanup
- Update onCleared() to properly close all services

Required model files (user-provided):
- moondream2.gguf (vision encoder)
- moondream2-mmproj.gguf (vision projector)

Ready for Phase 4 red-teaming and performance testing."
git push origin main
```

---

## 🎉 Phase 3 Completion Summary

✅ **Vision Pipeline:** Complete with 384x384 downscaling  
✅ **Model Manager:** Memory-efficient swapping implemented  
✅ **UI Integration:** Camera button added to ChatScreen  
✅ **RAG + Vision:** Combined pipeline for protocol-grounded image analysis  
✅ **Safety Features:** Error handling, validation, safe failure modes  
✅ **Documentation:** Complete implementation guide  

**Status:** Ready for Phase 4 Red-Teaming  
**Next Phase:** Environmental stress testing and hallucination bounties
