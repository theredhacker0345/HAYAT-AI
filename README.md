# Hayat AI - Offline Medical Assistant

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Hayat** (Arabic for "Life") is an offline-first AI medical assistant designed for crisis zones, remote areas, and situations where internet connectivity and hospitals are unavailable. Built with privacy and accessibility in mind.

## 🎯 Mission

Provide life-saving medical guidance in emergency situations when:
- Internet connectivity is unavailable or unreliable
- Hospitals/clinics are inaccessible (conflict zones, natural disasters)
- Professional medical help is not immediately available
- Language barriers exist (supports English & Arabic)

## ✨ Features

### Core Functionality
- **Offline LLM Inference**: Runs locally on device using GGUF quantized models
- **RAG-Powered Responses**: Retrieval-Augmented Generation prevents hallucinations by grounding answers in verified medical protocols
- **Vector Search**: Semantic search through ICRC/WHO emergency guidelines
- **Bilingual Support**: Full English and Arabic interface with RTL layout support
- **Privacy-First**: No data leaves your device, no tracking, no analytics

### Distribution Features
- **P2P Sharing**: Share app and medical assets via Bluetooth/WiFi Direct
- **Printable Cheat Sheets**: Generate 1-page PDF emergency protocol summaries
- **APK Export**: Bundle app with models for offline distribution
- **Low-End Device Support**: Optimized for devices with limited RAM/storage

### Safety Features
- **Triple-Check Validation**: All responses validated against medical database
- **Clear Disclaimers**: Every response includes a medical disclaimer
- **Source Attribution**: Shows which protocol each answer comes from
- **Refusal Messages**: Gracefully declines queries outside medical scope

## 📋 Requirements

### Minimum Specifications
- Android 8.0 (API 26) or higher
- 2GB RAM (3GB+ recommended)
- 1.5GB free storage space
- ARM64-v8a processor (ARMv7 supported with reduced performance)

### Required Model Files
Place these files in `/sdcard/Hayat/`:

| File | Size | Description | Download Source |
|------|------|-------------|-----------------|
| `model.gguf` | ~600MB-1.1GB | Quantized medical LLM | [HuggingFace](https://huggingface.co/TheBloke/Medical-Llama-3-8B-GGUF) |
| `e5-small.onnx` | ~67MB | Embedding model | [HuggingFace](https://huggingface.co/intfloat/multilingual-e5-small) |
| `medical.db` | ~44KB | Emergency protocols DB | Bundled with app |

**Recommended Models:**
- **LLM**: `Medical-Llama-3-8B-GGUF` (Q4_K_M quantization) or `TinyLlama-1.1B-Chat-v1.0-GGUF` for low-end devices
- **Embeddings**: `multilingual-e5-small` (ONNX format) for English/Arabic support

## 🚀 Setup Instructions

### Option 1: Manual Setup (Recommended for Testing)

1. **Clone the Repository**
   ```bash
   git clone https://github.com/your-org/hayat-ai.git
   cd hayat-ai
   ```

2. **Download Model Files**
   ```bash
   # Create Hayat directory
   mkdir -p /sdcard/Hayat
   
   # Download medical LLM (choose one based on device specs)
   # For powerful devices (>4GB RAM):
   wget https://huggingface.co/TheBloke/Medical-Llama-3-8B-GGUF/resolve/main/medical-llama-3-8b.Q4_K_M.gguf -O /sdcard/Hayat/model.gguf
   
   # For low-end devices (<3GB RAM):
   wget https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf -O /sdcard/Hayat/model.gguf
   
   # Download embedding model
   wget https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/model.onnx -O /sdcard/Hayat/e5-small.onnx
   ```

3. **Build and Install**
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Copy Bundled Assets** (if needed)
   The app will prompt you to copy medical databases from bundled assets on first launch.

### Option 2: Android Studio

1. Open project in Android Studio
2. Sync Gradle files
3. Connect Android device or start emulator (min 2GB RAM)
4. Run `app` configuration

### Option 3: Pre-built APK (For NGOs/Field Workers)

Download pre-built APK with models included (coming soon):
- Full version (~1.2GB): Includes high-quality medical LLM
- Lite version (~700MB): Uses TinyLlama for low-end devices

## 🏥 Medical Content

The app includes emergency protocols from:

### English Protocols
- Bleeding Control (Direct Pressure, Tourniquets, Wound Packing)
- Burn Treatment (Thermal, Chemical, Electrical)
- Fracture Stabilization (Splinting, Immobilization)
- CPR & Basic Life Support
- Dehydration Management (ORS Preparation)
- Wound Care & Infection Prevention
- Emergency Triage (START Protocol)
- Shock Management

### Arabic Protocols (بروتوكولات عربية)
- التحكم في النزيف
- علاج الحروق
- العناية بالكسور
- الإنعاش القلبي الرئوي
- إدارة الجفاف
- العناية بالجروح
- الفرز الطبي في حالات الطوارئ
- إدارة الصدمة

All protocols sourced from:
- International Committee of the Red Cross (ICRC)
- World Health Organization (WHO) Emergency Guidelines
- Médecins Sans Frontières (MSF) Field Manuals

## 🏗️ Architecture

```
app/
├── src/main/java/com/hayat/
│   ├── MainActivity.kt              # Main entry point
│   ├── assets/
│   │   └── AssetManager.kt          # Asset copying/validation
│   ├── data/
│   │   ├── AppDatabase.kt           # Room database setup
│   │   ├── MedicalChunk.kt          # Database entities
│   │   ├── VectorSearchEngine.kt    # Semantic search
│   │   └── ChatModels.kt            # Chat data classes
│   ├── services/
│   │   ├── ModelInferenceService.kt # Llama.cpp integration
│   │   ├── EmbeddingService.kt      # ONNX runtime
│   │   ├── PeerSharingService.kt    # Bluetooth/WiFi Direct
│   │   └── WordPieceTokenizer.kt    # Text tokenization
│   ├── ui/
│   │   ├── screens/
│   │   │   ├── ChatScreen.kt        # Main chat interface
│   │   │   ├── SettingsScreen.kt    # Configuration
│   │   │   ├── SharingScreen.kt     # P2P sharing UI
│   │   │   ├── CheatSheetScreen.kt  # PDF generation
│   │   │   └── SetupScreen.kt       # First-run setup
│   │   ├── theme/
│   │   │   ├── Theme.kt             # Material 3 theme
│   │   │   ├── Color.kt             # Color palette
│   │   │   └── Type.kt              # Typography
│   │   └── viewmodel/
│   │       └── ChatViewModel.kt     # RAG pipeline logic
│   └── utils/
│       └── CheatSheetGenerator.kt   # PDF generation
├── src/main/assets/
│   ├── medical.db                   # English protocols
│   ├── medical_arabic.db            # Arabic protocols
│   └── vocab.json                   # Tokenizer vocabulary
└── build.gradle.kts                 # App-level build config
```

## 🔧 Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/your-org/hayat-ai.git
cd hayat-ai

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing key)
./gradlew assembleRelease
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumentation tests
./gradlew connectedAndroidTest

# Code coverage
./gradlew jacocoTestReport
```

### Adding New Medical Protocols

1. Add protocol text to `app/src/main/assets/protocols_en.txt` or `protocols_ar.txt`
2. Run embedding generation script:
   ```bash
   python scripts/generate_embeddings.py --language en
   ```
3. Rebuild app to include updated database

### Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

**Priority Areas:**
- Additional language translations
- More medical protocols (pediatric, obstetric, mental health)
- Performance optimizations for low-end devices
- Accessibility improvements
- NGO partnership integrations

## ⚠️ Medical Disclaimer

**IMPORTANT**: This application is for educational and informational purposes only. It does NOT:

- Replace professional medical advice, diagnosis, or treatment
- Provide guarantees of accuracy or completeness
- Establish a doctor-patient relationship
- Handle all possible medical scenarios

**Always seek professional medical help when available.** In emergencies, contact local emergency services immediately.

The developers and contributors are not liable for any adverse outcomes resulting from use of this application.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

**Important**: Medical protocol content may have separate licensing restrictions. Always verify usage rights for ICRC/WHO/MSF content before redistribution.

## 🤝 Partnerships

We're actively seeking partnerships with:
- Humanitarian organizations (ICRC, MSF, WHO)
- Refugee camp administrators
- Disaster response teams
- Medical NGOs working in conflict zones

Contact: team@hayat-ai.org (placeholder)

## 🙏 Acknowledgments

- **Llama.cpp** team for efficient GGUF inference
- **Hugging Face** for open-source medical models
- **ICRC/WHO/MSF** for open-access medical guidelines
- **Android Jetpack Compose** team for modern UI toolkit
- All contributors and supporters of this humanitarian project

## 📊 Roadmap

### v1.0 (Current)
- ✅ Core RAG chat functionality
- ✅ English/Arabic bilingual support
- ✅ Medical protocol database
- ✅ P2P sharing infrastructure
- ✅ Printable cheat sheets

### v1.1 (Planned)
- [ ] French language support
- [ ] Pediatric-specific protocols
- [ ] Obstetric emergency guidelines
- [ ] Mental health first aid
- [ ] Voice input/output for hands-free use

### v1.2 (Planned)
- [ ] Offline map integration for nearest clinics
- [ ] Symptom checker with decision trees
- [ ] Medication dosage calculator
- [ ] Multi-patient triage management
- [ ] SMS-based query fallback

### Future Considerations
- iOS version
- Wear OS support for smartwatches
- Integration with portable diagnostic devices
- Mesh networking for group deployments

---

**Built with ❤️ for humanity**  
*Hayat AI - Saving lives through technology*
