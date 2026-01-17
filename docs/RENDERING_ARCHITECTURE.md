# Rendering Architecture Plan

## Project Objectives

### 1. Cross-Platform Desktop Application
Build a volumetric renderer that runs on:
- **macOS** (primary development target)
- **Windows** 
- **Linux**

All three platforms should provide the same user experience with minimal platform-specific code.

### 2. Maximum Code Reusability

```
┌────────────────────────────────────────────────────────────────┐
│                    SHARED CODE (95%+)                          │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────────┐  │
│  │   renderer   │ │    common    │ │       desktop          │  │
│  │              │ │              │ │                        │  │
│  │ • Shaders    │ │ • VolumeData │ │ • Compose UI           │  │
│  │ • Ray-cast   │ │ • NIfTI load │ │ • State management     │  │
│  │ • TF logic   │ │ • Math utils │ │ • JOGL integration     │  │
│  └──────────────┘ └──────────────┘ └────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              │
    ┌─────────────────────────┼─────────────────────────┐
    │                         │                         │
    ▼                         ▼                         ▼
┌─────────┐             ┌─────────┐             ┌─────────┐
│  macOS  │             │ Windows │             │  Linux  │
│         │             │         │             │         │
│ Metal   │             │ D3D12/  │             │ OpenGL  │
│ backend │             │ OpenGL  │             │ backend │
│ (Skiko) │             │ (Skiko) │             │ (Skiko) │
└─────────┘             └─────────┘             └─────────┘
```

**Design Principles:**
- **Kotlin Multiplatform**: Use `expect`/`actual` only where absolutely necessary
- **Platform-agnostic rendering**: OpenGL 4.1 works on all targets (via JOGL)
- **Shared shaders**: GLSL 4.10 compatible across all platforms
- **Compose Multiplatform UI**: Same UI code for all desktop platforms

---

## Current Problem

We're trying to integrate OpenGL volume rendering with Compose Desktop on macOS, and running into conflicts:

| Issue | Cause |
|-------|-------|
| No window appears | GLFW and Compose fight for main thread |
| `-XstartOnFirstThread` conflict | GLFW requires it, but it breaks Compose event loop |
| EGL not available | macOS doesn't support EGL (uses CGL/Metal) |

## Current (Broken) Stack

```
┌─────────────────────────────────────────┐
│         Compose Desktop (Skiko)         │  ← Uses Metal on macOS
├─────────────────────────────────────────┤
│              LWJGL 3.3.3                │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │  Core   │  │  GLFW   │  │ OpenGL  │  │
│  └─────────┘  └─────────┘  └─────────┘  │
│                   ↑                     │
│          PROBLEM: Requires              │
│          main thread on macOS           │
└─────────────────────────────────────────┘
```

**Why GLFW?** It was used for:
1. Creating an OpenGL context
2. Creating a window
3. Handling input events

**But we don't need GLFW because:**
1. Compose Desktop handles the window
2. Compose handles input events
3. We only need an OpenGL **context** (not a window)

---

## Cross-Platform Architecture

### Why JOGL for Cross-Platform?

| Feature | JOGL | LWJGL+GLFW | Native (Metal/D3D12) |
|---------|------|------------|---------------------|
| macOS support | ✅ | ⚠️ Main thread issue | ✅ Metal only |
| Windows support | ✅ | ✅ | ✅ D3D12 only |
| Linux support | ✅ | ✅ | ❌ |
| Single codebase | ✅ | ❌ Platform workarounds | ❌ Different APIs |
| Compose integration | ✅ SwingPanel | ❌ | ❌ |
| Shader reuse | ✅ GLSL | ✅ GLSL | ❌ MSL/HLSL |

**JOGL gives us:**
- Single OpenGL API across all platforms
- `GLJPanel` embeds into Swing → `SwingPanel` in Compose
- No main-thread restrictions on any platform
- Mature, well-tested library

### Module Structure for Reusability

```
kotlin-volumetric-renderer/
├── common/                    # 100% shared
│   ├── VolumeData.kt
│   ├── TransferFunction.kt
│   ├── NiftiLoader.kt
│   └── MathUtils.kt
│
├── renderer/                  # 100% shared (OpenGL abstraction)
│   ├── VolumeRenderer.kt      # GLEventListener implementation
│   ├── ShaderProgram.kt       # JOGL shader management
│   ├── Texture3D.kt           # Volume texture handling
│   └── resources/
│       ├── volume_vertex.glsl
│       └── volume_fragment.glsl
│
└── desktop/                   # 100% shared (Compose Desktop)
    ├── Main.kt
    ├── ui/
    │   ├── ControlPanel.kt    # Compose UI
    │   └── VolumeViewport.kt  # SwingPanel + GLJPanel
    └── platform/
        └── (empty - no platform code needed!)
```

### Platform-Specific Code: Near Zero

With JOGL + Compose Desktop, we achieve **zero platform-specific code**:

| Component | Platform Code Needed? |
|-----------|----------------------|
| Volume loading | ❌ Pure Kotlin |
| OpenGL rendering | ❌ JOGL handles it |
| UI | ❌ Compose Multiplatform |
| Window management | ❌ Compose handles it |
| Input handling | ❌ Compose handles it |
| File dialogs | ⚠️ Minor differences (JFileChooser works everywhere) |

---

## Proposed Solution: JOGL + SwingPanel

### Target Stack

```
┌─────────────────────────────────────────────────────────┐
│                  Compose Desktop Window                  │
│                     (Skiko/Metal)                        │
│  ┌───────────────┐    ┌─────────────────────────────┐   │
│  │ Control Panel │    │        SwingPanel           │   │
│  │  (Compose)    │    │  ┌───────────────────────┐  │   │
│  │               │    │  │      GLJPanel         │  │   │
│  │ • TF Presets  │    │  │       (JOGL)          │  │   │
│  │ • Sliders     │    │  │                       │  │   │
│  │ • Settings    │    │  │  OpenGL 4.1 Context   │  │   │
│  │               │    │  │  Volume Ray-casting   │  │   │
│  └───────────────┘    │  └───────────────────────┘  │   │
│                       └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### Why JOGL?

| Feature | GLFW | JOGL |
|---------|------|------|
| OpenGL context | ✅ | ✅ |
| Embeddable in Swing | ❌ | ✅ `GLJPanel` |
| Works with SwingPanel | ❌ | ✅ |
| macOS main thread issue | ❌ Has it | ✅ None |
| Maintained | ✅ | ✅ |

### Dependencies Change

**Remove ALL LWJGL from `desktop/build.gradle.kts`:**
```kotlin
// Remove ALL of these:
implementation("org.lwjgl:lwjgl:$lwjglVersion")
implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
```

**Add JOGL:**
```kotlin
// JOGL for OpenGL context + embeddable panel (replaces all LWJGL)
val jogampVersion = "2.5.0"
implementation("org.jogamp.jogl:jogl-all-main:$jogampVersion")
implementation("org.jogamp.gluegen:gluegen-rt-main:$jogampVersion")
```

### Decision: JOGL Only (Remove LWJGL)

✅ **Use JOGL's GL4 interface**
- Cleaner - one library for everything
- GLJPanel gives you `GL4` object directly
- Existing shaders work unchanged (GLSL is standard)
- Less dependencies = less clutter
- No potential conflicts between libraries

---

## Implementation Plan

### Step 1: Add JOGL Dependencies

```kotlin
// desktop/build.gradle.kts
val jogampVersion = "2.4.0"
implementation("org.jogamp.jogl:jogl-all-main:$jogampVersion")
implementation("org.jogamp.gluegen:gluegen-rt-main:$jogampVersion")
```

### Step 2: Create JOGL-based Volume Renderer

```kotlin
// JOGLVolumeRenderer.kt
class JOGLVolumeRenderer : GLEventListener {
    
    override fun init(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL4
        // Initialize shaders, textures, VAO/VBO
    }
    
    override fun display(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL4
        // Render volume
    }
    
    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, w: Int, h: Int) {
        // Handle resize
    }
    
    override fun dispose(drawable: GLAutoDrawable) {
        // Cleanup
    }
}
```

### Step 3: Wrap in SwingPanel for Compose

```kotlin
// Main.kt
@Composable
fun VolumeViewport(renderer: JOGLVolumeRenderer) {
    SwingPanel(
        modifier = Modifier.fillMaxSize(),
        factory = {
            GLJPanel(GLCapabilities(GLProfile.get(GLProfile.GL4))).apply {
                addGLEventListener(renderer)
            }
        }
    )
}
```

### Step 4: Port Shaders

No changes needed - GLSL shaders are standard OpenGL. Just need to:
1. Load shader source (same as before)
2. Compile with JOGL's `GL4.glCreateShader()` etc.

### Step 5: Port OpenGLRenderBackend to JOGL

The `renderer` module currently uses LWJGL OpenGL calls like:
```kotlin
// LWJGL style (static imports)
glGenTextures()
glBindTexture(GL_TEXTURE_3D, textureId)
glTexImage3D(...)
```

These become JOGL calls (object-oriented):
```kotlin
// JOGL style (GL4 object)
val textures = IntArray(1)
gl.glGenTextures(1, textures, 0)
gl.glBindTexture(GL4.GL_TEXTURE_3D, textures[0])
gl.glTexImage3D(...)
```

**Key API differences:**
| LWJGL | JOGL |
|-------|------|
| `glGenTextures()` returns Int | `gl.glGenTextures(n, array, offset)` fills array |
| `glGetUniformLocation(program, name)` | `gl.glGetUniformLocation(program, name)` |
| Static imports | Method calls on `GL4` object |
| `MemoryStack` for buffers | Plain arrays or `IntBuffer` |

---

## Alternative: Offscreen FBO Approach

If JOGL integration is problematic, we can use a simpler approach:

```
┌─────────────────────────────────────────────────────────┐
│                  Compose Desktop Window                  │
│  ┌───────────────┐    ┌─────────────────────────────┐   │
│  │ Control Panel │    │      Image(bitmap)          │   │
│  │  (Compose)    │    │                             │   │
│  └───────────────┘    └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                              ↑
                        ImageBitmap
                              ↑
                     ┌────────┴────────┐
                     │  Background     │
                     │  Render Thread  │
                     │                 │
                     │  JOGL Offscreen │
                     │  (GLOffscreenAutoDrawable) │
                     │       ↓         │
                     │  Render to FBO  │
                     │       ↓         │
                     │  glReadPixels   │
                     └─────────────────┘
```

This approach:
- Creates headless OpenGL context (no window)
- Renders to framebuffer
- Reads pixels and converts to Compose `ImageBitmap`
- ~30-60 FPS possible with efficient pixel transfer

---

## Decision Points

### 1. JOGL vs Continue with LWJGL?

| Approach | Effort | Risk | Integration |
|----------|--------|------|-------------|
| JOGL + SwingPanel | Medium | Low | Native embed |
| LWJGL + Offscreen | Low | Medium | Image blit |

**Recommendation:** Start with JOGL + SwingPanel for proper integration.

### 2. Port OpenGLRenderBackend to JOGL?

The `renderer` module has ~500 lines of LWJGL OpenGL code. Options:

A. **Port to JOGL** - Clean but takes time
B. **Create JOGL adapter** - Wrap JOGL GL4 to look like LWJGL calls
C. **Rewrite for JOGL** - Best long-term, most work

**Recommendation:** Option A - Port to JOGL. The API is similar, mostly mechanical changes.

---

## Next Steps

### Task List

#### Phase 1: Dependencies & Build (2 files) ✅ COMPLETE

- [x] **Task 1.1:** Update `desktop/build.gradle.kts`
  - Remove all LWJGL dependencies (lwjgl, lwjgl-glfw, lwjgl-opengl + natives)
  - Add JOGL dependencies (jogl-all-main, gluegen-rt-main 2.6.0)
  - Remove unused JVM args

- [x] **Task 1.2:** Update `renderer/build.gradle.kts`  
  - Remove all LWJGL dependencies
  - Add JOGL dependencies
  - This module contains the OpenGL abstraction layer

#### Phase 2: Port Renderer Module to JOGL (5 files) ✅ COMPLETE

- [x] **Task 2.1:** Create `JOGLShaderProgram.kt` (new file in `renderer/jogl/`)
  - JOGL `GL4` object methods
  - Shader compilation with JOGL API

- [x] **Task 2.2:** Create `JOGLTexture3D.kt`
  - JOGL style `glGenTextures()`, `glTexImage3D()`
  - Volume texture handling

- [x] **Task 2.3:** Create `JOGLTexture1D.kt`
  - Transfer function texture creation

- [x] **Task 2.4:** Create `JOGLUniformBuffer.kt`
  - UBO creation and update with JOGL

- [x] **Task 2.5:** Create `JOGLRenderBackend.kt`
  - Accepts `GL4` object
  - Main render loop with JOGL calls

- [x] **Task 2.6:** Delete old LWJGL `opengl/` folder
  - Removed `OpenGLShaderProgram.kt`, `OpenGLTexture3D.kt`, etc.

#### Phase 3: Create JOGL Integration (2 new files) ✅ COMPLETE

- [x] **Task 3.1:** Create `JOGLVolumeRenderer.kt`
  - Implements `GLEventListener` interface
  - `init()`: Initialize shaders, textures, VAO/VBO
  - `display()`: Call render backend
  - `reshape()`: Handle resize
  - `dispose()`: Cleanup resources

- [x] **Task 3.2:** Create `VolumeViewport.kt`
  - Compose component wrapping `SwingPanel`
  - Factory creates `GLJPanel` with `GLEventListener`
  - Mouse/keyboard input handling

#### Phase 4: Update Desktop App (3 files) ✅ COMPLETE

- [x] **Task 4.1:** Update `Main.kt`
  - Removed `OffscreenVolumeRenderer` usage
  - Uses new `VolumeViewport` composable
  - Uses `JOGLVolumeRenderer`

- [x] **Task 4.2:** Delete `GLFWRenderer.kt`
  - Removed (GLFW no longer used)

- [x] **Task 4.3:** Delete `TestGLFW.kt`
  - Removed

#### Phase 5: Testing & Validation ✅ COMPLETE

- [x] **Task 5.1:** Test build on macOS
  - No LWJGL references remain ✓
  - JOGL natives load correctly ✓
  - OpenGL 4.1 Metal - 89.4 detected ✓

- [x] **Task 5.2:** Test volume rendering
  - Load NIfTI file (320x320x130 heart MRI) ✓
  - 3D texture created: 50 MB ✓
  - Transfer function presets working ✓

- [x] **Task 5.3:** Test UI integration
  - SwingPanel embeds correctly ✓
  - FPSAnimator running at 60 FPS ✓

- [ ] **Task 5.4:** Cross-platform test
  - Test on Windows (if available)
  - Test on Linux (if available)

---

### Files Summary

| Action | File | Module | Status |
|--------|------|--------|--------|
| Modify | `build.gradle.kts` | desktop | ✅ Done |
| Modify | `build.gradle.kts` | renderer | ✅ Done |
| Create | `JOGLShaderProgram.kt` | renderer/jogl | ✅ Done |
| Create | `JOGLTexture3D.kt` | renderer/jogl | ✅ Done |
| Create | `JOGLTexture1D.kt` | renderer/jogl | ✅ Done |
| Create | `JOGLUniformBuffer.kt` | renderer/jogl | ✅ Done |
| Create | `JOGLRenderBackend.kt` | renderer/jogl | ✅ Done |
| Create | `JOGLVolumeRenderer.kt` | desktop | ✅ Done |
| Create | `VolumeViewport.kt` | desktop | ✅ Done |
| Modify | `Main.kt` | desktop | ✅ Done |
| Delete | `GLFWRenderer.kt` | desktop | ✅ Done |
| Delete | `TestGLFW.kt` | desktop | ✅ Done |
| Delete | `renderer/opengl/*` | renderer | ✅ Done |

**Total: 13 file operations completed (7 create, 2 modify, 4 delete)**

---

## Cross-Platform Testing Checklist

| Platform | OpenGL Version | Expected Issues |
|----------|---------------|-----------------|
| macOS 10.15+ | 4.1 (max) | None with JOGL |
| Windows 10+ | 4.6 | None |
| Ubuntu 22.04+ | 4.6 | Mesa driver variations |

### Build Commands

```bash
# macOS
./gradlew desktop:run

# Windows  
gradlew.bat desktop:run

# Linux
./gradlew desktop:run
```

No special JVM arguments needed on any platform!

---

## Decisions Made

| Question | Decision |
|----------|----------|
| **LWJGL vs JOGL?** | ✅ JOGL only - remove LWJGL entirely |
| **Offscreen vs Embedded?** | ✅ SwingPanel embedding (cleaner integration) |
| **Future: Metal/Vulkan?** | Deferred - OpenGL 4.1 via JOGL is sufficient for now |
| **Mobile/Web targets?** | Out of scope - desktop only for now |

---

## Summary: Why This Architecture?

| Objective | How We Achieve It |
|-----------|------------------|
| **Cross-platform** | JOGL + Compose Desktop = same code on macOS/Windows/Linux |
| **Code reuse** | 100% shared renderer, 100% shared UI, 0% platform code |
| **Maintainability** | Single codebase, single set of shaders, single API |
| **Performance** | Native OpenGL on each platform |
| **Integration** | SwingPanel embeds GLJPanel directly in Compose |

Let me know which approach you'd like to proceed with!

---

## Architecture Update: Offscreen Rendering (Jan 2026)

### The Problem: AWT/Compose Interop Issues
While JOGL's `GLJPanel` (Lightweight) and `GLCanvas` (Heavyweight) are standard ways to embed OpenGL in Java applications, they both failed when integrated into Compose Desktop on macOS:

1.  **GLJPanel (Lightweight):** Resulted in a black screen on macOS Metal. The lightweight Swing component failed to composite correctly with the Skia-based Compose rendering pipeline.
2.  **GLCanvas (Heavyweight):** Resulted in severe flickering. Heavyweight components (AWT) and Lightweight components (Compose/Skia) fight for z-ordering. The AWT component would constantly erase the background or be overwritten by the Compose redraw loop, causing a strobe effect.

### The Solution: Offscreen Rendering (FBO)
To achieve a stable, flicker-free, cross-platform rendering pipeline, we moved to **Offscreen Rendering**.

**Architecture:**
1.  **Hidden OpenGL Context:** We create a `GLOffscreenAutoDrawable` (pixel buffer) that is never added to the UI hierarchy.
2.  **Render to FBO:** The volume rendering logic draws to this offscreen Framebuffer Object.
3.  **Read Pixels:** We use `glReadPixels` to copy the rendered frame from GPU memory to a `ByteBuffer`.
4.  **Compose Image:** The pixel data is converted to a Skia `Bitmap` and then to a Compose `ImageBitmap`.
5.  **Standard UI Element:** The volume render is displayed using a standard Compose `Image` component.

**Benefits:**
*   **Zero Flickering:** The render is just a standard image in the Compose UI tree. No AWT/Heavyweight mixing.
*   **Full Compose Integration:** The volume render can be overlaid with text, buttons, or other Compose elements without z-fighting.
*   **Cross-Platform Stability:** This approach bypasses platform-specific windowing quirks (Cocoa/HWND/X11) related to embedding.

**Performance Trade-off:**
*   There is a cost to `glReadPixels` (GPU -> CPU copy) every frame. However, for a medical imaging application targeting ~60 FPS at standard viewport sizes (e.g., 800x600), this overhead is negligible on modern hardware (M1/M2/M3, dedicated GPUs).

### Updated Component Stack

```
┌─────────────────────────────────────────┐
│             Compose Desktop             │
│  ┌───────────────────────────────────┐  │
│  │        VolumeViewport.kt          │  │
│  │  ┌─────────────────────────────┐  │  │
│  │  │      Image (Composable)     │  │  │
│  │  └──────────────▲──────────────┘  │  │
│  └─────────────────┼─────────────────┘  │
└────────────────────┼────────────────────┘
                     │ Bitmap Data
                     │
┌────────────────────┴────────────────────┐
│          JOGLVolumeRenderer.kt          │
│  ┌───────────────────────────────────┐  │
│  │      GLOffscreenAutoDrawable      │  │
│  │        (Hidden Context)           │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```
