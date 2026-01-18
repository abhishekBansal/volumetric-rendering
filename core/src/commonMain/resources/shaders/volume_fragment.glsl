#version 410 core

// Input from vertex shader
in vec3 texCoord;
in vec3 worldPos;

// Output
out vec4 FragColor;

// Uniforms in UBOs (binding set in code for OpenGL 4.1)
layout(std140) uniform Matrices {
    mat4 modelMatrix;
    mat4 viewMatrix;
    mat4 projectionMatrix;
};

layout(std140) uniform Material {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float shininess;
};

layout(std140) uniform Lighting {
    vec3 lightColor;
    vec3 ambientLight;
    vec3 lightPosition;
};

// Textures (bindings set in code for OpenGL 4.1)
uniform sampler3D volumeData;
uniform sampler1D transferFunction;

// Volume parameters
uniform vec3 texelSize;

// Ray marching parameters
uniform vec3 cameraPosition;
uniform vec3 bboxMin;
uniform vec3 bboxMax;
uniform float step;
uniform int steps;
uniform int debugMode; // 0=normal, 1=show density, 2=show coords

// Slicing parameters (normalized 0.0 to 1.0)
uniform vec3 sliceMin;
uniform vec3 sliceMax;

// Calculate gradient for normals using central differences
vec3 getNormal(vec3 pos) {
    vec3 s = texelSize;
    if (length(s) < 1e-6) s = vec3(0.01);

    float gx = texture(volumeData, pos + vec3(s.x, 0.0, 0.0)).r -
               texture(volumeData, pos - vec3(s.x, 0.0, 0.0)).r;
    float gy = texture(volumeData, pos + vec3(0.0, s.y, 0.0)).r -
               texture(volumeData, pos - vec3(0.0, s.y, 0.0)).r;
    float gz = texture(volumeData, pos + vec3(0.0, 0.0, s.z)).r -
               texture(volumeData, pos - vec3(0.0, 0.0, s.z)).r;
    
    vec3 g = vec3(gx, gy, gz);
    if (dot(g, g) < 1e-10) return vec3(0.0);
    return normalize(-g);
}

// Calculate lighting using Phong/Blinn-Phong model
vec3 calculateLighting(vec3 position, vec3 N, vec3 V, vec3 baseColor) {
    vec3 L = normalize(lightPosition - position); // Vector to light
    vec3 H = normalize(L + V);                    // Half vector (Blinn-Phong)
    
    // Fallback constants in case UBO values are zero/failed
    float ambientStrength = length(Ka * ambientLight);
    vec3 effectiveAmbient = (ambientStrength < 0.01) ? vec3(0.5) : (Ka * ambientLight);
    
    float diffuseStrength = length(Kd * lightColor);
    vec3 effectiveDiffuse = (diffuseStrength < 0.01) ? vec3(0.5) : (Kd * lightColor);
    
    // Diffuse - Half-Lambert (Wrapped) to prevent harsh black shadows
    // range [0.0, 1.0]
    float ndotl = dot(N, L);
    float diff = ndotl * 0.5 + 0.5;
    
    // Specular - Blinn-Phong
    // Use max(dot(N, H), 0.0) for specular highlight
    float specAngle = max(dot(N, H), 0.0);
    float spec = pow(specAngle, max(shininess, 10.0));
    vec3 specular = Ks * spec * lightColor;
    
    // (Ambient + Diffuse) * Color + Specular
    return (effectiveAmbient + effectiveDiffuse * diff) * baseColor + specular;
}

void main() {
    // Debug mode: show texture coordinates
    if (debugMode == 2) {
        FragColor = vec4(texCoord, 1.0);
        return;
    }
    
    // worldPos is the back face (exit point) in world-space
    vec3 rayExit = worldPos;

    // Calculate ray entry point (front face) by tracing from camera (world-space)
    vec3 rayDir = normalize(rayExit - cameraPosition);

    // Calculate actual slicing bounds in world space
    vec3 volumeSize = bboxMax - bboxMin;
    vec3 actualSliceMin = bboxMin + volumeSize * sliceMin;
    vec3 actualSliceMax = bboxMin + volumeSize * sliceMax;

    // Find where ray enters the volume defined by SPLICED BOUNDS (world-space)
    vec3 invRayDir = 1.0 / rayDir;
    vec3 tMin = (actualSliceMin - cameraPosition) * invRayDir;
    vec3 tMax = (actualSliceMax - cameraPosition) * invRayDir;
    
    vec3 t1 = min(tMin, tMax);
    vec3 t2 = max(tMin, tMax);
    
    float tNear = max(max(t1.x, t1.y), t1.z);
    float tFar = min(min(t2.x, t2.y), t2.z);
    
    // If ray misses the SPLICED volume, discard
    if (tNear > tFar || tFar < 0.0) {
        discard;
    }
    
    // Ray entry point
    vec3 rayStart = cameraPosition + rayDir * max(tNear, 0.0);
    rayStart = clamp(rayStart, actualSliceMin, actualSliceMax);
    
    // Ray exit point (limit marching to the sliced bounds)
    vec3 effectiveRayExit = cameraPosition + rayDir * tFar;
    effectiveRayExit = clamp(effectiveRayExit, actualSliceMin, actualSliceMax);

    // March from entry to exit
    vec3 position = rayStart;
    vec4 finalColor = vec4(0.0);
    
    float stepSize = step;
    int maxSteps = steps;
    
    // Debug mode: show first hit density
    if (debugMode == 1) {
        for (int i = 0; i < maxSteps; ++i) {
            // Check against effective exit point and sliced bounds
            if (distance(position, rayStart) > distance(effectiveRayExit, rayStart) ||
                any(greaterThan(position, actualSliceMax)) || 
                any(lessThan(position, actualSliceMin))) {
                break;
            }
            // Convert world-space position to texture coordinates [0,1]
            // Note: Texture coordinates are still relative to the FULL volume bounds (bboxMin/Max)
            vec3 texPos = (position - bboxMin) / (bboxMax - bboxMin);
            float density = texture(volumeData, texPos).r;
            if (density > 0.01) {
                FragColor = vec4(vec3(density), 1.0);
                return;
            }
            position += stepSize * rayDir;
        }
        FragColor = vec4(1.0, 0.0, 0.0, 1.0); // Red if nothing found
        return;
    }
    
    // Main ray marching loop - accumulate volume samples
    for (int i = 0; i < maxSteps; ++i) {
        // Check if we've reached the exit point or left the volume
        if (distance(position, rayStart) > distance(effectiveRayExit, rayStart) ||
            any(greaterThan(position, actualSliceMax)) || 
            any(lessThan(position, actualSliceMin))) {
            break;
        }
        
        // Sample volume: convert world-space position to texture coordinates
        vec3 texPos = (position - bboxMin) / (bboxMax - bboxMin);
        float density = texture(volumeData, texPos).r;
        
        // Transfer function lookup
        vec4 color = texture(transferFunction, density);
        
        // Apply opacity correction for step size
        // Clamp alpha to avoid NaN in pow (if alpha > 1.0 due to filtering)
        float alpha = clamp(color.a, 0.0, 0.999);
        color.a = 1.0 - pow(1.0 - alpha, stepSize * 150.0);
        
        // Low threshold to keep details but avoid zero-alpha calculations
        if (color.a > 0.0001) {
            vec3 N = getNormal(texPos);
            vec3 V = -rayDir;
            
            vec3 shadedColor = calculateLighting(position, N, V, color.rgb);
            
            // Front-to-back compositing
            finalColor.rgb += (1.0 - finalColor.a) * color.a * shadedColor;
            finalColor.a += (1.0 - finalColor.a) * color.a;
            
            // Early termination
            if (finalColor.a > 0.99) {
                break;
            }
        }
        
        position += stepSize * rayDir;
    }
    
    // If nothing was hit, show a subtle background
    if (finalColor.a < 0.01) {
        finalColor = vec4(texCoord * 0.1, 0.3);
    }
    
    FragColor = finalColor;
}
