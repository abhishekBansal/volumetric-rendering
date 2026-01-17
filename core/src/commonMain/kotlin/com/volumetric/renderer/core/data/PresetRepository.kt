package com.volumetric.renderer.core.data

/**
 * Repository for standard transfer function presets.
 */
object PresetRepository {
    val presets = listOf(
        // CT Anatomy (Based on user analysis)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0.0f, 0.79f, 0.88f, 0.82f, 0.0f),      // -770 HU (Air)
                GradientStop(0.2f, 1.0f, 0.78f, 0.7f, 0.0f),        // -144 HU (Lung)
                GradientStop(0.26f, 0.73f, 0.0f, 0.03f, 0.0f),      // 62 HU (Blood)
                GradientStop(0.27f, 0.81f, 0.04f, 0.07f, 0.0f),     // 90 HU (Opacity Start)
                GradientStop(0.29f, 1.0f, 0.14f, 0.17f, 0.07f),     // 158 HU (Soft Tissue)
                GradientStop(0.30f, 1.0f, 0.35f, 0.17f, 0.12f),     // 210 HU (Muscle)
                GradientStop(0.31f, 1.0f, 0.46f, 0.15f, 0.14f),     // 228 HU (Soft Tissue 2)
                GradientStop(0.32f, 1.0f, 0.64f, 0.11f, 0.21f),     // 259 HU (Dense Tissue)
                GradientStop(0.33f, 0.98f, 0.73f, 0.32f, 0.39f),    // 330 HU (Bone Start)
                GradientStop(0.37f, 0.94f, 0.92f, 0.73f, 0.83f),    // 466 HU (Bone Marrow)
                GradientStop(0.38f, 0.94f, 0.92f, 0.73f, 0.94f),    // 499 HU (Bone)
                GradientStop(0.75f, 1.0f, 1.0f, 1.0f, 0.94f),       // 2001 HU (Cortical Bone)
                GradientStop(1.0f, 1.0f, 0.96f, 0.99f, 1.0f)        // 3071 HU (Dense Bone)
            ),
            name = "CT Anatomy"
        ),
        // Cardiac MRI (Optimized for la_003)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                GradientStop(0.2f, 0.0f, 0.0f, 0.0f, 0.0f),         // Cut off noise up to 20%
                GradientStop(0.25f, 1.0f, 0.14f, 0.17f, 0.05f),     // Myocardium (Low opacity)
                GradientStop(0.35f, 1.0f, 0.35f, 0.17f, 0.2f),      // Muscle
                GradientStop(0.5f, 1.0f, 0.64f, 0.11f, 0.5f),       // Dense
                GradientStop(0.7f, 0.94f, 0.92f, 0.73f, 0.8f),      // Blood Pool / Contrast
                GradientStop(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
            ),
            name = "Cardiac MRI"
        ),
        // Cardiac MRI (Blood Pool)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                GradientStop(0.4f, 0.0f, 0.0f, 0.0f, 0.0f),         // Aggressive cutoff
                GradientStop(0.45f, 0.8f, 0.0f, 0.0f, 0.1f),        // Transition
                GradientStop(0.55f, 1.0f, 0.2f, 0.2f, 0.6f),        // Blood Pool edge
                GradientStop(0.7f, 1.0f, 0.8f, 0.8f, 0.9f),         // Blood Pool center
                GradientStop(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
            ),
            name = "Cardiac MRI (Blood Pool)"
        ),
        // High contrast for seeing structures
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.05f, 0.1f, 0.1f, 0.1f, 0.0f),
                GradientStop(0.2f, 0.3f, 0.3f, 0.3f, 0.3f),
                GradientStop(0.4f, 0.6f, 0.6f, 0.6f, 0.6f),
                GradientStop(0.6f, 0.85f, 0.85f, 0.85f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "High Contrast"
        ),
        // Maximum visibility
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.1f, 0.2f, 0.2f, 0.25f, 0.4f),
                GradientStop(0.3f, 0.5f, 0.5f, 0.6f, 0.7f),
                GradientStop(0.5f, 0.8f, 0.8f, 0.9f, 0.9f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Maximum"
        ),
        // Soft tissue
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.15f, 0.3f, 0.2f, 0.15f, 0.2f),
                GradientStop(0.35f, 0.7f, 0.5f, 0.4f, 0.5f),
                GradientStop(0.6f, 1.0f, 0.8f, 0.7f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Soft Tissue"
        ),
        // Vascular (red tint)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.2f, 0.3f, 0.05f, 0.05f, 0.3f),
                GradientStop(0.4f, 0.8f, 0.2f, 0.15f, 0.6f),
                GradientStop(0.7f, 1.0f, 0.5f, 0.3f, 0.9f),
                GradientStop(1f, 1f, 0.8f, 0.6f, 1f)
            ),
            name = "Vascular"
        ),
        TransferFunction.bonePreset(),
        TransferFunction.hotMetalPreset(),
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.5f, 0.3f, 0.6f, 0.9f, 0.5f),
                GradientStop(1f, 0.9f, 0.3f, 0.1f, 1f)
            ),
            name = "Purple-Gold"
        )
    )
}
