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

// Ray marching parameters
uniform vec3 cameraPosition;
uniform vec3 bboxMin;
uniform vec3 bboxMax;
uniform float step;
uniform int steps;
uniform int debugMode; // 0=normal, 1=show density, 2=show coords

// Calculate gradient for normals using central differences
vec3 getNormal(vec3 pos) {
    vec3 sampleOffset = vec3(0.01);
    
    float gx = texture(volumeData, pos + vec3(sampleOffset.x, 0.0, 0.0)).r -
               texture(volumeData, pos - vec3(sampleOffset.x, 0.0, 0.0)).r;
    float gy = texture(volumeData, pos + vec3(0.0, sampleOffset.y, 0.0)).r -
               texture(volumeData, pos - vec3(0.0, sampleOffset.y, 0.0)).r;
    float gz = texture(volumeData, pos + vec3(0.0, 0.0, sampleOffset.z)).r -
               texture(volumeData, pos - vec3(0.0, 0.0, sampleOffset.z)).r;
    
    return normalize(vec3(gx, gy, gz));
}

// Phong lighting model
vec3 phong(vec3 N, vec3 V, vec3 L, vec3 baseColor) {
    // Ambient
    vec3 ambient = Ka * ambientLight;
    
    // Diffuse
    float diff = max(dot(N, L), 0.0);
    vec3 diffuse = Kd * diff * lightColor * baseColor;
    
    // Specular
    vec3 R = reflect(-L, N);
    float spec = pow(max(dot(R, V), 0.0), shininess);
    vec3 specular = Ks * spec * lightColor;
    
    return ambient + diffuse + specular;
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

    // Find where ray enters the volume defined by bboxMin..bboxMax (world-space)
    vec3 invRayDir = 1.0 / rayDir;
    vec3 tMin = (bboxMin - cameraPosition) * invRayDir;
    vec3 tMax = (bboxMax - cameraPosition) * invRayDir;
    
    vec3 t1 = min(tMin, tMax);
    vec3 t2 = max(tMin, tMax);
    
    float tNear = max(max(t1.x, t1.y), t1.z);
    float tFar = min(min(t2.x, t2.y), t2.z);
    
    // If ray misses the volume, discard
    if (tNear > tFar || tFar < 0.0) {
        discard;
    }
    
    // Ray entry point (clamp to world-space volume bounds)
    vec3 rayStart = cameraPosition + rayDir * max(tNear, 0.0);
    rayStart = clamp(rayStart, bboxMin, bboxMax);
    
    // March from entry to exit
    vec3 position = rayStart;
    vec4 finalColor = vec4(0.0);
    
    float stepSize = step;
    int maxSteps = steps;
    
    // Debug mode: show first hit density
    if (debugMode == 1) {
        for (int i = 0; i < maxSteps; ++i) {
            if (any(greaterThan(position, rayExit)) || 
                any(lessThan(position, bboxMin))) {
                break;
            }
            // Convert world-space position to texture coordinates [0,1]
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
        if (distance(position, rayStart) > distance(rayExit, rayStart) ||
            any(greaterThan(position, bboxMax)) || 
            any(lessThan(position, bboxMin))) {
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
            // Simple gradient-based shading (compute normal in texture space)
            vec3 N = getNormal(texPos);
            vec3 L = normalize(lightPosition - position);
            
            // Basic diffuse shading
            float diffuse = max(dot(N, L), 0.0);
            
            // Combine ambient + diffuse (simple model)
            // This ensures the color is always visible (ambient) and lit by the light (diffuse)
            float shading = 0.3 + 0.7 * diffuse;
            
            vec3 shadedColor = color.rgb * shading;
            
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
