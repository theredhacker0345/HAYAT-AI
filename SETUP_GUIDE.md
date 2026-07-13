# Hayat AI - Quick Setup Guide

## For Field Workers & NGOs

### Step 1: Download Required Files

**Option A: Complete Package (Recommended)**
Download the pre-packaged ZIP containing:
- `HayatAI.apk` - The application
- `model.gguf` - Medical LLM (~600MB)
- `e5-small.onnx` - Embedding model (~67MB)

**Option B: Manual Download**
1. Install app from APK
2. Download models separately (see links below)

### Step 2: Prepare Android Device

1. **Enable Unknown Sources**
   - Settings → Security → Enable "Install unknown apps"
   - Or Settings → Apps → Special access → Install unknown apps

2. **Create Hayat Folder**
   ```
   Open file manager
   Navigate to Internal Storage
   Create folder: Hayat
   ```

3. **Copy Model Files**
   Place these files in `/sdcard/Hayat/`:
   - `model.gguf` (Medical LLM)
   - `e5-small.onnx` (Embedding model)
   - Medical databases are bundled with app

### Step 3: Install & Launch

1. Tap `HayatAI.apk` to install
2. Open the app
3. App will verify assets automatically
4. If prompted, tap "Copy Bundled Assets"
5. Start chatting!

### Step 4: Verify Installation

In the app:
1. Go to Settings (gear icon)
2. Check "Model Status" shows ✓
3. Try asking: "How to treat severe bleeding?"
4. You should get a medical protocol response

## Model Download Links

### For High-End Devices (>4GB RAM)
```bash
wget https://huggingface.co/TheBloke/Medical-Llama-3-8B-GGUF/resolve/main/medical-llama-3-8b.Q4_K_M.gguf -O model.gguf
```

### For Low-End Devices (<3GB RAM)
```bash
wget https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf -O model.gguf
```

### Embedding Model (All Devices)
```bash
wget https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/onnx/model.onnx -O e5-small.onnx
```

## Troubleshooting

### "Model not found" error
- Verify files are in `/sdcard/Hayat/` (not `/sdcard/Android/data/...`)
- Check file names match exactly: `model.gguf`, `e5-small.onnx`
- Restart the app after copying files

### App crashes on startup
- Ensure Android 8.0 or higher
- Free up at least 1.5GB storage space
- Close other apps to free RAM
- Try the Lite version with TinyLlama model

### Slow response times
- Use Q4_K_M quantized models (smaller = faster)
- Close background apps
- Consider using TinyLlama for older devices
- Wait 1-2 minutes after launching app (model warm-up)

### "Insufficient storage" message
- Delete unused apps/photos/videos
- Move media to SD card if available
- Use Lite model (TinyLlama ~600MB vs Medical-Llama ~1.1GB)

## Sharing with Others (P2P)

Once installed on one device:

1. Open Hayat AI
2. Go to Settings → Share Hayat AI
3. Choose sharing method:
   - Bluetooth (slow but universal)
   - WiFi Direct (faster, both devices need support)
   - QR Code (generates download link)
4. Recipient receives APK + models bundle

**Note**: Full transfer is ~1.2GB. WiFi Direct recommended.

## Printing Emergency Cheat Sheets

1. Open Hayat AI
2. Go to Settings → Print Emergency Sheets
3. Select protocols to include:
   - Bleeding Control
   - Burn Treatment
   - Fracture Care
   - CPR Instructions
   - All protocols (recommended)
4. Choose language: English / Arabic / Bilingual
5. Tap "Generate PDF"
6. Print or save for offline reference

## NGO Distribution Kit

For organizations deploying to multiple devices:

### Pre-loaded SD Cards
1. Format SD card as portable storage
2. Create `/Hayat/` folder
3. Copy all model files
4. Include `HayatAI.apk`
5. Distribute with devices

### Bulk Installation Script (ADB)
```bash
#!/bin/bash
for device in $(adb devices | grep device | cut -f1); do
  adb -s $device install HayatAI.apk
  adb -s $device shell mkdir -p /sdcard/Hayat
  adb -s $device push model.gguf /sdcard/Hayat/
  adb -s $device push e5-small.onnx /sdcard/Hayat/
done
```

### Offline Server Setup
For camps/clinics with local network:
1. Set up local HTTP server with model files
2. Configure app to download from local IP
3. Devices download over WiFi (no internet needed)

Contact us for custom deployment solutions.

## Contact Support

- Email: support@hayat-ai.org (placeholder)
- Documentation: https://hayat-ai.org/docs
- Issue Tracker: https://github.com/hayat-ai/hayat-ai/issues

---

**Last Updated**: July 2024  
**Version**: 1.0.0
