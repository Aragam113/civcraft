#version 330

// Territorial lens shader. MaskSampler is a screen-space RGBA mask
// painted on the CPU: black texels mean "desaturate this pixel", any
// non-black texel means "keep with a tint equal to the texel's RGB".
//
// Tint is applied as a moderate-strength multiplicative cast so the
// original block texture detail stays legible while the civ / city
// colour cleanly shifts the overall hue.

uniform sampler2D InSampler;
uniform sampler2D MaskSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 scene = texture(InSampler, texCoord);
    vec4 mask  = texture(MaskSampler, texCoord);
    float keep = step(0.01, max(mask.r, max(mask.g, mask.b)));
    vec3 tint  = mask.rgb;
    vec3 tinted = scene.rgb * mix(vec3(1.0), tint, 0.6);
    float grey = dot(scene.rgb, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(mix(vec3(grey), tinted, keep), 1.0);
}
