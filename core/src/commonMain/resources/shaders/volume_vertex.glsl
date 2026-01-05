#version 410 core

// Input
layout(location = 0) in vec3 position;

// Output to fragment shader
out vec3 texCoord;
out vec3 worldPos;

// Uniforms in UBOs (binding set in code, not in shader for 4.1)
layout(std140) uniform Matrices {
    mat4 modelMatrix;
    mat4 viewMatrix;
    mat4 projectionMatrix;
};

void main() {
    texCoord = position;
    worldPos = (modelMatrix * vec4(position, 1.0)).xyz;
    gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
}
