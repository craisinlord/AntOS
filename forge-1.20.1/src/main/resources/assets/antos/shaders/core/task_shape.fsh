#version 150

uniform int SideCount;
uniform float ShapeSize;
uniform float BorderWidth;
uniform float HoverWidth;
uniform vec4 FillColor;
uniform vec4 BorderColor;
uniform vec4 HoverColor;
uniform vec4 ColorModulator;
uniform vec2 Vertex0;
uniform vec2 Vertex1;
uniform vec2 Vertex2;
uniform vec2 Vertex3;
uniform vec2 Vertex4;
uniform vec2 Vertex5;
uniform vec2 Vertex6;
uniform vec2 Vertex7;

in vec2 shapePosition;
out vec4 fragColor;

float shapeDistance(vec2 p) {
    if (SideCount == 0) return length(p - vec2(0.5)) - 0.5;
    vec2 vertices[8] = vec2[8](Vertex0, Vertex1, Vertex2, Vertex3, Vertex4, Vertex5, Vertex6, Vertex7);
    float distanceSquared = 4.0;
    bool inside = true;
    for (int i = 0; i < 8; i++) {
        if (i >= SideCount) break;
        vec2 a = vertices[i];
        vec2 edge = vertices[(i + 1) % SideCount] - a;
        vec2 delta = p - a;
        vec2 nearest = delta - edge * clamp(dot(delta, edge) / dot(edge, edge), 0.0, 1.0);
        distanceSquared = min(distanceSquared, dot(nearest, nearest));
        inside = inside && edge.x * delta.y - edge.y * delta.x >= 0.0;
    }
    return sqrt(distanceSquared) * (inside ? -1.0 : 1.0);
}

void main() {
    float distance = shapeDistance(shapePosition) * ShapeSize;
    float antialias = max(length(vec2(dFdx(distance), dFdy(distance))), 0.0001);
    float outerCoverage = 1.0 - smoothstep(-antialias * 0.5, antialias * 0.5, distance - HoverWidth);
    float borderCoverage = 1.0 - smoothstep(-antialias * 0.5, antialias * 0.5, distance);
    float fillCoverage = 1.0 - smoothstep(-antialias * 0.5, antialias * 0.5, distance + BorderWidth);
    vec4 color = mix(HoverColor, BorderColor, borderCoverage);
    color = mix(color, FillColor, fillCoverage);
    fragColor = vec4(color.rgb, color.a * outerCoverage) * ColorModulator;
}
