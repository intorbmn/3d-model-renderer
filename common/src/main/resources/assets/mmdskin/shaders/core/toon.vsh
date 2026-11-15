#version 330
#extension GL_ARB_separate_shader_objects : require

// Toon (cel) shading for MMD models, ported from the original mod's OpenGL shader to the 26.3 pipeline
// layout. Same inputs and uniform blocks as minecraft:core/entity, so the world's lightmap, fog and the
// red hurt overlay still apply.

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
layout(location = 5) out vec2 texCoord0;
layout(location = 6) out vec3 viewNormal;
layout(location = 7) out vec3 viewPosition;
layout(location = 8) out vec3 viewLight0;
layout(location = 9) out vec3 viewLight1;

void main() {
    vec4 position = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * position;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    // Normals and the light directions share a space (vanilla dots them directly); the view matrix
    // turns both, and the vertex position, into view space for rim and specular light.
    mat3 view = mat3(ModelViewMat);
    viewNormal = view * Normal;
    viewPosition = position.xyz;
    viewLight0 = view * Light0_Direction;
    viewLight1 = view * Light1_Direction;

    vertexColor = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
