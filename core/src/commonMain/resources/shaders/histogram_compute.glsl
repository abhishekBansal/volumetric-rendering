#version 410 core

// Compute histogram of 3D volume data
// Note: OpenGL 4.1 has limited compute shader support, so this uses a simplified approach

layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;

uniform sampler3D volumeData;
uniform int numBins;

// Shared memory for local histogram
shared uint localHistogram[256];

// Output buffer (using image buffer as OpenGL 4.1 doesn't have SSBOs)
layout(rgba32ui) uniform uimage2D histogramImage;

void main() {
    // Initialize local histogram
    uint localIndex = gl_LocalInvocationIndex;
    if (localIndex < numBins) {
        localHistogram[localIndex] = 0u;
    }
    barrier();
    
    // Calculate texture coordinates
    ivec3 volumeSize = textureSize(volumeData, 0);
    ivec3 globalID = ivec3(gl_GlobalInvocationID);
    
    if (globalID.x < volumeSize.x && globalID.y < volumeSize.y && globalID.z < volumeSize.z) {
        // Sample volume
        vec3 texCoord = (vec3(globalID) + 0.5) / vec3(volumeSize);
        float density = texture(volumeData, texCoord).r;
        
        // Calculate bin index
        uint binIndex = uint(clamp(density, 0.0, 0.9999) * float(numBins));
        
        // Atomically increment local histogram
        atomicAdd(localHistogram[binIndex], 1u);
    }
    
    barrier();
    
    // Write local histogram to global
    if (localIndex < numBins) {
        uint count = localHistogram[localIndex];
        if (count > 0u) {
            ivec2 coord = ivec2(localIndex % 16, localIndex / 16);
            imageAtomicAdd(histogramImage, coord, count);
        }
    }
}
