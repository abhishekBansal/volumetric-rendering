# Running with DICOM Data

## Quick Start

### Load DICOM files via command line:

```bash
# Load a single DICOM file
./gradlew desktop:run --args="/path/to/file.dcm"

# Load a DICOM series (directory)
./gradlew desktop:run --args="/Users/yourname/Downloads/dicom-series"
```

### Or run with test sphere:
```bash
./gradlew desktop:run
```

## Getting Sample DICOM Data

1. **DICOM Library** (Easiest - No registration):
   ```bash
   curl -o sample.dcm https://www.dicomlibrary.com/download/file1.dcm
   ```

2. **Manual Download**:
   - Visit: https://www.dicomlibrary.com/
   - Download any CT or MRI sample
   - Run: `./gradlew desktop:run --args="/path/to/downloaded/file.dcm"`

## Controls

- **Mouse Drag**: Rotate camera around volume
- **WASD**: Move camera position
- **Scroll**: Zoom in/out
- **P Key**: Cycle through transfer function presets
  - Bone (grayscale/brown)
  - Hot Metal (black→red→yellow→white)
  - Purple-Gold (custom gradient)

## Compose Desktop UI

**Coming in Phase 1.6!**

The proper Compose Desktop UI with interactive transfer function editor, file browser, and controls will be introduced in the next phase. For now, we're using command-line arguments to avoid Swing dialog issues with GLFW's main thread requirement on macOS.

### Phase 1.6 Will Include:
- ✨ Full Compose Desktop UI
- 🎨 Interactive gradient editor with drag & drop color stops
- 📊 Real-time histogram overlay
- 🎛️ Material property sliders (Ka, Kd, Ks, shininess)
- 💾 Transfer function preset library with save/load
- 📁 Native file browser integration
- 🔍 DICOM metadata viewer

## Example Usage

```bash
# Download sample CT scan
mkdir -p ~/dicom-test
cd ~/dicom-test
curl -o brain.dcm "https://www.dicomlibrary.com/mri/files/MR-MONO2-8-16x-heart"

# Run with the file
cd /path/to/kotlin-volumetric-renderer
./gradlew desktop:run --args="$HOME/dicom-test"
```

## Troubleshooting

**Issue**: Volume appears as black/white noise
- **Cause**: Compressed DICOM (JPEG/RLE) or localizer scans (low quality positioning images)
- **Solution**: 
  - Try a different DICOM series with more slices (> 100 slices recommended)
  - Look for "Axial" or "Sagittal" series, not "Localizer"
  - Press 'P' to cycle transfer function presets
  - Download uncompressed DICOM samples

**Issue**: "DICOM path not found"
- Check the absolute path is correct
- Ensure file has `.dcm` extension or is in a directory with DICOM files

**Issue**: Black/empty volume
- Press 'P' multiple times to cycle transfer function presets
- Some DICOM data may need specific presets for visualization

**Issue**: "No DICOM files found in directory"
- Ensure directory contains files with valid DICOM headers
- Try loading a single .dcm file first
