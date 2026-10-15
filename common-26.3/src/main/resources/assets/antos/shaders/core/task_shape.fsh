#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in float edgeDistance;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float antialias = max(length(vec2(dFdx(edgeDistance), dFdy(edgeDistance))), 0.0001);
    float coverage = 1.0 - smoothstep(-antialias * 0.5, antialias * 0.5, edgeDistance);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
