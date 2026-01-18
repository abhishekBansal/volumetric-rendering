# Rendering Improvement Plan

This document needs to outline the roadmap for upgrading the rendering engine to support high-quality volumetric visualization.

## Phase 1: Lighting & Material System Upgrade
**Goal**: Move from simple Phong/Lambertian shading to a more robust model that handles volume data better.

- [x] **Task 1.1: Refactor Shader Structure**
  - Separate lighting logic from ray-marching loop.
  - Create reusable functions for `getNormal()` and `calculateLighting()`.
- [x] **Task 1.2: Implement Blinn-Phong Shading**
  - Replace reflection vector calculation with Half-Vector (`H`) for softer highlights.
  - Add `Material` struct uniform (Ambient `Ka`, Diffuse `Kd`, Specular `Ks`, Shininess).
- [x] **Task 1.3: Gradient Estimation Tuning**
  - Improve `getNormal()` to use a configurable `delta` (epsilon).
  - Use `soot` reduction technique: blur the gradient slightly by increasing the sampling distance for normals (e.g., `2.0 * texelSize`).

## Phase 2: Render Quality & Performance
**Goal**: Balance performance and visual fidelity.

- [ ] **Task 2.1: Dynamic Quality Adjustment**
  - [x] **Task 2.1.1: Implement High Res Mode**
    - Set default `stepSize` to `0.001` (High Quality).
    - Increase `maxSteps` to `2000` to ensure ray traversal.
  - [x] **Task 2.1.2: Implement Interaction Mode**
    - Automatically switch resolution or step count when the camera is moving.
- [x] **Task 2.2: Opacity Correction**
  - Ensure transparency is mathematically correct regardless of `stepSize`.
  - Formula: `alpha_corrected = 1.0 - pow(1.0 - alpha_sample, stepSize * reference_sampling_rate)`.
- [ ] **Task 2.3: Jittering (Stochastic Raycasting) [Optional]**
  - Add ray start-point jittering to break banding artifacts (Wood-grain effect).
  - **Risk**: Introduces noise. Must include a toggle or be paired with high-sample counts.

## Phase 3: Transfer Function & Data Handling
**Goal**: Smooth and accurate color mapping.

- [x] **Task 3.1: Texture Filtering**
  - [x] Ensure Transfer Function texture uses `GL_LINEAR` interpolation. (Equivalent in JOGL: `GL_LINEAR`)
  - [x] Validate that the Texture generation on CPU side uses correct interpolation (Linear vs Spline).
- [x] **Task 3.2: 16-bit Data Support**
  - [x] Verify current handling of 16-bit DICOM/Raw data. (Correctly loaded into FloatArray)
  - [x] Ensure proper normalization range [0..1] in the shader. (Data is pre-normalized to [0..1] on CPU).
  - [x] **Fix**: Added proper Big Endian support for raw DICOM data extraction.

## Execution Strategy
- All changes must be tested on both standard and noisy datasets (like CT-Abdomen).
- "Blackness/Soot" artifacts are the primary test case for Gradient Estimation changes.
