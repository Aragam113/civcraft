#version 330

// Color-keyed desaturation test: everything rendered turns grayscale except
// pixels whose color closely matches oak log, oak planks, or cobblestone —
// the three "building block" palettes. No mask/uniforms required; everything
// is decided per-pixel from the scene color itself.

uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

out vec4 fragColor;

// Returns 1.0 if rgb is close (within `tol` per channel) to the given palette color.
float matches(vec3 rgb, vec3 palette, float tol) {
    vec3 d = abs(rgb - palette);
    return step(max(max(d.r, d.g), d.b), tol);
}

void main() {
    vec4 c = texture(InSampler, texCoord);

    // Vanilla reference colors (approximate, eyeballed from the textures).
    // Oak log bark — medium warm brown.
    vec3 OAK_LOG_BARK  = vec3(0.37, 0.29, 0.18);
    // Oak log top — cream ring.
    vec3 OAK_LOG_TOP   = vec3(0.76, 0.62, 0.40);
    // Oak planks — lighter sandy tan.
    vec3 OAK_PLANKS    = vec3(0.69, 0.56, 0.34);
    // Cobblestone — neutral gray.
    vec3 COBBLESTONE   = vec3(0.50, 0.50, 0.50);

    float keep = 0.0;
    keep = max(keep, matches(c.rgb, OAK_LOG_BARK, 0.10));
    keep = max(keep, matches(c.rgb, OAK_LOG_TOP,  0.10));
    keep = max(keep, matches(c.rgb, OAK_PLANKS,   0.10));
    // Cobblestone: loose tolerance since gray covers the whole stone family.
    // Instead of palette matching, detect low saturation + mid luminance.
    float maxC = max(c.r, max(c.g, c.b));
    float minC = min(c.r, min(c.g, c.b));
    float sat  = maxC - minC;
    float luma = (maxC + minC) * 0.5;
    float grayLike = step(sat, 0.08) * step(0.32, luma) * step(luma, 0.68);
    keep = max(keep, grayLike);

    // Desaturate when not kept.
    float greyValue = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    c.rgb = mix(vec3(greyValue), c.rgb, keep);

    fragColor = vec4(c.rgb, 1.0);
}
