#!/bin/bash

# Run script for Kotlin Volumetric Renderer
# Ensures we're always in the correct directory

cd /Users/abhishekbansal/Downloads/GpuRayCasting_Shading_TF_16Bit_Endian/GpuRayCasting/kotlin-volumetric-renderer

echo "Current directory: $(pwd)"
echo "Running gradle desktop:run..."
echo ""

./gradlew desktop:run --no-daemon
