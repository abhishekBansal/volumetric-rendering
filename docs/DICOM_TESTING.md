# DICOM Testing Guide

## DICOM Support Implementation ✅

The volumetric renderer now supports loading real medical imaging data in DICOM format!

### Features Implemented:
- ✅ Single DICOM file loading
- ✅ DICOM series loading (multiple slices from directory)
- ✅ Automatic slice sorting by location/instance number
- ✅ Support for 8-bit and 16-bit pixel data
- ✅ Rescale slope/intercept application (Hounsfield units for CT)
- ✅ Automatic data normalization to [0, 1] range
- ✅ Metadata extraction (patient name, modality, series description, etc.)
- ✅ Progress callbacks during loading

### How to Test:

1. **Run the application:**
   ```bash
   ./gradlew desktop:run --no-daemon
   ```

2. **Load DICOM data:**
   - Press **'O'** key to open file browser
   - Select either:
     - A single `.dcm` file
     - A directory containing DICOM series (multiple slices)
   
3. **Interact with the volume:**
   - Mouse drag: Rotate camera
   - WASD: Move camera
   - Scroll: Zoom in/out
   - P: Cycle transfer function presets

### Sample DICOM Datasets:

**Free Medical Imaging Datasets:**

1. **DICOM Library** - https://www.dicomlibrary.com/
   - Pre-anonymized CT, MRI, X-ray samples
   - Direct download, no registration

2. **The Cancer Imaging Archive (TCIA)** - https://www.cancerimagingarchive.net/
   - Large collection of medical imaging data
   - Requires registration (free)

3. **Sample DICOM Files** - https://barre.dev/medical/samples/
   - Small test datasets
   - Quick download

### Expected Output:

When loading a DICOM series, you should see console output like:

```
=== Loading DICOM data from: /path/to/dicom/series ===
Found 64 potential DICOM files
Volume dimensions: 512x512x64
Modality: CT
Bits allocated: 16
Loading progress: 25%
Loading progress: 50%
Loading progress: 75%
Loading progress: 100%
Data range: [-1024.0, 3071.0]

✓ DICOM loaded successfully!
  Dimensions: 512x512x64
  Modality: CT
  Patient: ANONYMOUS
  Series: Brain Axial
✓ Volume loaded and ready to render
```

### Transfer Function Recommendations:

- **CT Scans**: Use "Bone" preset (good for skeletal structures)
- **MRI**: Use "Hot Metal" or "Purple-Gold" presets
- **Angiography**: Use "Hot Metal" preset

### Troubleshooting:

**Issue**: File dialog doesn't appear
- **Solution**: Make sure X11 or native window manager is running

**Issue**: "No DICOM files found"
- **Solution**: Ensure files have `.dcm` extension or valid DICOM preamble

**Issue**: Volume appears black/empty
- **Solution**: 
  - Press 'P' to cycle through transfer function presets
  - Adjust camera position with WASD keys
  - Check console for data range (might need TF adjustment)

**Issue**: Build errors with dcm4che
- **Solution**: Run `./gradlew --refresh-dependencies` to re-download libraries

### Performance Notes:

- **Large volumes** (512³+): May take 10-30 seconds to load and normalize
- **Memory usage**: ~2GB for typical CT series (512x512x200 slices)
- **Rendering**: Real-time 60 FPS on M1 Pro with adaptive ray marching

### Next Steps:

- [ ] Add window/level adjustment for CT data
- [ ] Implement histogram visualization
- [ ] Add multi-planar reconstruction (MPR) views
- [ ] Support compressed DICOM transfer syntaxes
- [ ] Add DICOM tag viewer UI
