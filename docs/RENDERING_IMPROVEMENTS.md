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
- [ ] **Task 1.3: Gradient Estimation Tuning**
  - Improve `getNormal()` to use a configurable `delta` (epsilon).
  - Use `soot` reduction technique: blur the gradient slightly by increasing the sampling distance for normals (e.g., `2.0 * texelSize`).

## Phase 2: Render Quality & Performance
**Goal**: Balance performance and visual fidelity.

- [ ] **Task 2.1: Dynamic Quality Adjustment**
  - Implement two rendering modes: `Interaction` (Low Res) and `Stationary` (High Res).
  - Automatically switch resolution or step count when the camera is moving.
- [ ] **Task 2.2: Opacity Correction**
  - Ensure transparency is mathematically correct regardless of `stepSize`.
  - Formula: `alpha_corrected = 1.0 - pow(1.0 - alpha_sample, stepSize * reference_sampling_rate)`.
- [ ] **Task 2.3: Jittering (Stochastic Raycasting) [Optional]**
  - Add ray start-point jittering to break banding artifacts (Wood-grain effect).
  - **Risk**: Introduces noise. Must include a toggle or be paired with high-sample counts.

## Phase 3: Transfer Function & Data Handling
**Goal**: Smooth and accurate color mapping.

- [ ] **Task 3.1: Texture Filtering**
  - Ensure Transfer Function texture uses `GL_LINEAR` interpolation.
  - Validate that the Texture generation on CPU side uses correct interpolation (Linear vs Spline).
- [ ] **Task 3.2: 16-bit Data Support**
  - Verify current handling of 16-bit DICOM/Raw data.
  - Ensure proper normalization range [0..1] in the shader.

## Execution Strategy
- All changes must be tested on both standard and noisy datasets (like CT-Abdomen).
- "Blackness/Soot" artifacts are the primary test case for Gradient Estimation changes.
