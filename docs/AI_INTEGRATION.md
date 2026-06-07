# AI Integration Guide for Hayat

This guide covers how to integrate GGUF models and ONNX embeddings for on-device medical inference in Hayat AI.

## Model Preparation

### LLM Model (Qwen2.5-1.5B-Instruct)

1. Download from HuggingFace:
   - `Qwen2.5-1.5B-Instruct-GGUF` from bartowski/Qwen2.5-1.5B-Instruct-GGUF
   - Recommended quantization: `Q4_K_M` (~800MB-1.1GB)
   - This provides the best quality-to-speed tradeoff for mobile devices

2. Quantization Options:
   - `Q4_K_M`: Recommended for most devices (best quality/speed)
   - `Q3_K_M`: Use if RAM is constrained (<4GB)
   - `Q5_K_M`: Higher quality, larger file (16GB+ RAM devices)

### Embedding Model (e5-small-v2)

1. Download from HuggingFace:
   - `intfloat/e5-small-v2` ONNX export
   - Convert to ONNX using optimum or download pre-converted
   - Expected size: ~100MB

## File Placement

Place all files in `/sdcard/Hayat/` on the device:

```
/sdcard/Hayat/
├── model.gguf          (~800MB-1.1GB) - LLM model
├── e5-small.onnx       (~100MB)       - Embedding model
└── medical.db          (~5-10MB)      - Medical database
```

Create the directory:
```bash
adb shell mkdir -p /sdcard/Hayat
adb push model.gguf /sdcard/Hayat/
adb push e5-small.onnx /sdcard/Hayat/
adb push medical.db /sdcard/Hayat/
```

## Android Integration

### Model Loading

```kotlin
val modelInferenceService = ModelInferenceService.getInstance(context)
val result = modelInferenceService.loadModel("/sdcard/Hayat/model.gguf")
```

The service uses `io.github.ljcamargo:llamacpp-kotlin:0.4.0` which provides:

- JNI bindings for llama.cpp
- File descriptor-based model loading
- Coroutine-based streaming via Flow
- Multimodal support (optional mmproj)

### Inference Parameters

Configured in `InferenceParams`:
- `temperature`: 0.7 (controls randomness)
- `maxTokens`: 512 (maximum response length)
- `topP`: 0.9 (nucleus sampling threshold)
- `contextLength`: 2048 (model context window)
- `threads`: 4 (CPU threads to use)

### Streaming Response

```kotlin
val tokenFlow = modelInferenceService.generateResponse(userQuery, contextChunks)

tokenFlow.collect { token ->
    // Each token is emitted as it's generated
    accumulatedResponse += token
}
```

## Medical Prompt Engineering

### System Prompt (Arabic)

The system prompt sets the medical context:
```
أنت مساعد طبي متوفر بالكامل في الموقع...
```

Key constraints:
- Never prescribe specific medications without alternatives
- Always recommend hospital for severe cases
- Cite medical sources in responses
- Focus on emergency protocols

### RAG Context Injection

Before inference:
1. Embed user query using e5-small ONNX
2. Vector search top-5 relevant medical chunks
3. Inject context into prompt:

```kotlin
val prompt = buildString {
    append(systemPrompt)
    append("\n\nالسياق الطبي:\n")
    contextChunks.forEach { chunk ->
        append("${chunk.protocol_title}: ${chunk.content}\n")
    }
    append("السؤال: $userQuery\n\nالإجابة:")
}
```

## Testing

### Sample Queries (Arabic)

1. "كيف أتعامل مع نزيف حاد؟"
2. "أعراض صدمة انفارغوني؟"
3. "إنعاش قلبي رئوي للرضع؟"
4. "علامات كسر عظمي؟"

### Debug Inference

```bash
adb logcat | grep -E "(ModelInferenceService|LlamaHelper)"
```

## Troubleshooting

### OOM (Out of Memory)

- Use Q3_K_M quantization instead of Q4_K_M
- Reduce context length to 1024
- Close other apps before running

### Slow Inference

- Ensure model is Q4_K_M or lower quantization
- Check CPU threads setting (4-6 is optimal)
- ARM devices with i8mm/dotprod support run faster

### Missing Files

- Verify `/sdcard/Hayat/` exists
- Check file permissions (readable)
- Ensure correct file names (case-sensitive)

### Database Issues

- `MedicalDatabaseSeeder` populates DB on first run
- Embeddings are NULL initially, computed lazily
- Vector search returns empty if no embeddings available

## Architecture

```
┌─────────────────────────────────────┐
│           ChatViewModel             │
│  (Manages state, RAG pipeline flow) │
└───────────────┬─────────────────────┘
                │
        ┌───────┴───────┐
        ▼               ▼
┌─────────────┐   ┌──────────────┐
│  LLM Engine │   │ Embedding SVC│
│ (LlamaHelper)│   │(ONNX Runtime)│
└─────────────┘   └──────────────┘
        ▲               ▲
        │               │
┌───────┴───────┐   ┌─┴────────────┐
│  model.gguf   │   │ e5-small.onnx│
└───────────────┘   └──────────────┘

┌─────────────────────────────────────┐
│        VectorSearchEngine           │
│  (Cosine similarity, top-k=5)     │
└───────────────┬─────────────────────┘
                ▼
┌─────────────────────────────────────┐
│         MedicalChunkDao             │
│    (Room DB, nullable embeddings)  │
└─────────────────────────────────────┘
```