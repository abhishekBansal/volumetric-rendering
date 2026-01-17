# Contributing Guide

## Project Goals
We aim for a clean, modular architecture that supports cross-platform rendering (Desktop & Mobile) using Kotlin Multiplatform.

## Project Structure (Code Map)
- **`core/`**: Pure Kotlin logic. Contains Math (`Vector3`, `Matrix4x4`), IO (`DicomLoader`, `NiftiLoader`), and Data Models.
  - **Rule**: `core` must NEVER depend on `desktop` or `renderer`. It is the source of truth.
  - **Rule**: Keep this module dependency-free (standard library only).

- **`renderer/`**: Abstract rendering logic and specific implementations.
  - Currently contains `JOGL` implementation details.
  - Future: Will contain Android/GLES logic.

- **`desktop/`**: The Application Entry Point.
  - Uses Compose Desktop for UI.
  - Integrates `JOGLVolumeRenderer` inside `SwingPanel` (Compose interop).
  - Handles Window management, Validations, and File Pickers.

## Architectural Constraints (CRITICAL)
1. **Windowing System**: Do NOT refactor to use `GLFW` or `LWJGL` directly. We use `JOGL` inside a `SwingPanel` because it provides the most stable offscreen rendering context on macOS/Metal.
2. **State Management**: 
   - `core` defines the data (`VolumeData`).
   - `desktop` holds the UI state (`MutableState<VolumeData>`).
   - `renderer` consumes the state. 
   - *Do not mix these interactions.*

## Verification
### Running the App
Since this is a UI-heavy application, manual verification is often required.
```bash
./gradlew desktop:run
```
*   **Success Criteria**: App launches, purple/black screen appears. Load a dataset (drag & drop) to see the volume.
*   **Failure**: "GL Context not found" or crash on startup.

### Running Tests
We are building out our test suite. 
```bash
./gradlew core:test
```
Add new unit tests in `core/src/commonTest/kotlin/` whenever modifying math or logic.

## Code Style
- **Kotlin Pure**: Prefer idiomatic Kotlin (`val`, `apply`, `let`).
- **File Size**: Keep files under 300 lines. If a file grows larger (like `JOGLVolumeRenderer`), propose a refactor to split concerns.
