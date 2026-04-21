#version 150

// Post-process desaturation gated by a territory mask. Pixels where the
// mask is 0 (outside the player's owned territory) get desaturated +
// slightly dimmed; pixels inside the territory pass through untouched.

uniform sampler2D DiffuseSampler;    // main scene color from the previous frame target
uniform sampler2D TerritoryMask;     // grayscale mask; 1.0 = owned, 0.0 = unowned

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    float owned = texture(TerritoryMask, texCoord).r;
    // Smooth ramp so border isn't a hard line.
    float desat = smoothstep(0.55, 0.45, owned);

    // Perceptual luma (Rec. 601).
    float luma = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    vec3 grey  = vec3(luma) * 0.65;           // slightly darker than true grayscale
    c.rgb = mix(c.rgb, grey, desat);

    fragColor = c;
}
