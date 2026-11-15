#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
// The toon settings, one pixel each (see ToonRendering): 0 = shadow colour + bands / 5,
// 1 = rim power / 10, rim intensity, specular power / 128, specular intensity.
uniform sampler2D ToonParams;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
layout(location = 5) in vec2 texCoord0;
layout(location = 6) in vec3 viewNormal;
layout(location = 7) in vec3 viewPosition;
layout(location = 8) in vec3 viewLight0;
layout(location = 9) in vec3 viewLight1;

layout(location = 0) out vec4 fragColor;

float saturate(float value) {
    return clamp(value, 0.0, 1.0);
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord0) * vertexColor;
#ifdef ALPHA_CUTOUT
    if (texColor.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    vec4 p0 = texelFetch(ToonParams, ivec2(0, 0), 0);
    vec4 p1 = texelFetch(ToonParams, ivec2(1, 0), 0);
    vec3 shadowSetting = p0.rgb;
    float bands = max(floor(p0.a * 5.0 + 0.5), 2.0);
    float rimPower = max(p1.r * 10.0, 0.1);
    float rimIntensity = p1.g;
    float specularPower = max(p1.b * 128.0, 1.0);
    float specularIntensity = p1.a;

    vec3 albedo = texColor.rgb;
    vec3 normal = normalize(gl_FrontFacing ? viewNormal : -viewNormal);
    vec3 light0 = normalize(viewLight0);
    vec3 light1 = normalize(viewLight1);
    vec3 viewDir = normalize(-viewPosition);

    // The stronger of the two world lights decides the band.
    float NdotL = max(dot(normal, light0), dot(normal, light1));
    vec3 lightDir = dot(normal, light0) >= dot(normal, light1) ? light0 : light1;
    float halfLambert = NdotL * 0.5 + 0.5;

    float edgeSoftness = mix(0.08, 0.04, clamp((bands - 2.0) / 3.0, 0.0, 1.0));
    float toonRamp;
    if (bands <= 2.0) {
        toonRamp = smoothstep(0.5 - edgeSoftness, 0.5 + edgeSoftness, halfLambert);
    } else {
        float scaled = halfLambert * (bands - 1.0);
        float s = floor(scaled);
        float f = fract(scaled);
        float blend = smoothstep(0.5 - edgeSoftness, 0.5 + edgeSoftness, f);
        toonRamp = saturate((s + blend) / (bands - 1.0));
    }

    vec3 shadowColor = albedo * shadowSetting;
    float shadowLum = dot(shadowColor, vec3(0.299, 0.587, 0.114));
    vec3 coolShift = vec3(shadowLum * 0.85, shadowLum * 0.88, shadowLum * 1.1);
    vec3 shadowTint = mix(shadowColor, coolShift, 0.2);
    vec3 finalColor = mix(shadowTint, albedo, toonRamp);
    finalColor = max(finalColor, albedo * 0.1);

    vec3 halfDir = normalize(lightDir + viewDir);
    float specular = pow(saturate(dot(normal, halfDir)), specularPower);
    float specThreshold = mix(0.82, 0.96, clamp(specularPower / 96.0, 0.0, 1.0));
    float specBand = smoothstep(specThreshold - 0.02, specThreshold + 0.02, specular);
    finalColor += vec3(1.0) * specBand * specularIntensity * step(0.5, halfLambert) * 0.5;

    float fresnel = 1.0 - saturate(dot(viewDir, normal));
    float rimEdge = smoothstep(0.7, 0.9, pow(fresnel, rimPower));
    finalColor += albedo * rimEdge * rimIntensity * (1.0 - toonRamp) * 0.35;

    vec4 color = vec4(finalColor, texColor.a);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    color *= ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
