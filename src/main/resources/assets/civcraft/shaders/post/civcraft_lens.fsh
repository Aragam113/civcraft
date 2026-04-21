#version 330

uniform sampler2D InSampler;
uniform sampler2D TerritorySampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

void main() {
    vec4 c = texture(InSampler, texCoord);
    float owned = texture(TerritorySampler, texCoord).r;
    float desat = smoothstep(0.55, 0.45, owned);
    float luma = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 grey = vec3(luma) * 0.65;
    c.rgb = mix(c.rgb, grey, desat);
    fragColor = vec4(c.rgb, 1.0);
}
