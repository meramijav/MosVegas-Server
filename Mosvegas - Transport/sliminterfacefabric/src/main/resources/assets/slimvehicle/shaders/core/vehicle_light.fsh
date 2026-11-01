#version 150
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform sampler2D Lamps;
uniform sampler2D Shadows;
uniform sampler2D Lightmap;
uniform sampler2D Volume;
uniform vec3 VolumeOrigin;
uniform int VolumeReady;
uniform vec3 Eye;
uniform mat4 InverseViewProjection;
uniform mat4 ViewProjection;
uniform vec2 ScreenSize;
uniform int LampCount;
uniform int HazeCount;
uniform float Haze;

in vec2 texCoord;
out vec4 fragColor;

const ivec2 MAP = ivec2(256, 128);
const float RANGE = 48.0;
const vec2 SPAN_X = vec2(-1.0, 1.0);
const vec2 SPAN_Y = vec2(-0.6, 0.3);
const float HEADLIGHT = 4000.0;
const float AIR_LIGHT = 4400.0;
const float SMALL_LAMP = 2.6;
const float FLARE_HEADLIGHT = 3.0;
const float FLARE_LAMP = 2.5;
const float BLOOM_ANGLE = 0.012;
const float BLOOM_HEADLIGHT = 0.75;
const float BLOOM_LAMP = 0.42;
const float NEAR_LIGHT = 0.0025;
const float CEILING = 1.12;
const int HAZE_SAMPLES = 8;
const float HAZE_SIDE = 0.839;
const float HAZE_BELOW = 0.477;
const float HAZE_ABOVE = 0.354;
const float HAZE_REACH = 0.4;
const float HAZE_REACH_CAP = 6.0;
const float HAZE_FORWARD = 0.55;
const float HAZE_FORWARD_SHARE = 0.6;
const ivec3 VOLUME = ivec3(96, 32, 96);
const int TILES = 8;

vec4 lampData(int row, int k) {
    return texelFetch(Lamps, ivec2(k, row), 0);
}

bool inVolume(ivec3 cell) {
    return all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, VOLUME));
}

vec4 voxel(ivec3 cell) {
    return texelFetch(Volume, ivec2((cell.y % TILES) * VOLUME.x + cell.x, (cell.y / TILES) * VOLUME.z + cell.z), 0) * 255.0;
}

float solidFrom(int kind, float y0, float y1) {
    if (kind <= 16) {
        float h = float(kind) / 16.0;
        if (y0 < h) return 0.0;
        return y1 < h ? (y0 - h) / (y0 - y1) : -1.0;
    }
    float h = float(kind - 16) / 16.0;
    if (y0 > h) return 0.0;
    return y1 > h ? (h - y0) / (y1 - y0) : -1.0;
}

float traceBlocks(vec3 origin, vec3 dir, float limit) {
    vec3 p = origin - VolumeOrigin;
    ivec3 cell = ivec3(floor(p));
    ivec3 stepDir = ivec3(dir.x >= 0.0 ? 1 : -1, dir.y >= 0.0 ? 1 : -1, dir.z >= 0.0 ? 1 : -1);
    vec3 inv = 1.0 / max(abs(dir), vec3(1e-5));
    vec3 next = (vec3(stepDir) * (vec3(cell) - p) + vec3(stepDir) * 0.5 + 0.5) * inv;
    float t = 0.0;
    for (int i = 0; i < 128; i++) {
        float leave = min(min(next.x, min(next.y, next.z)), limit);
        if (inVolume(cell)) {
            int kind = int(voxel(cell).r + 0.5);
            if (kind > 0) {
                float hit = solidFrom(kind, p.y + dir.y * t - float(cell.y), p.y + dir.y * leave - float(cell.y));
                if (hit >= 0.0) return mix(t, leave, hit);
            }
        }
        if (leave >= limit) break;
        if (next.x < next.y && next.x < next.z) { t = next.x; next.x += inv.x; cell.x += stepDir.x; }
        else if (next.y < next.z) { t = next.y; next.y += inv.y; cell.y += stepDir.y; }
        else { t = next.z; next.z += inv.z; cell.z += stepDir.z; }
    }
    return limit;
}

float openPath(vec3 p, vec3 normal, vec3 origin, float sceneDistance) {
    if (VolumeReady == 0) return 1.0;
    vec3 start = p + normal * (0.03 + sceneDistance * 0.002);
    vec3 path = origin - start;
    float len = length(path) - 0.15;
    if (len <= 0.0) return 1.0;
    return traceBlocks(start, normalize(path), len) >= len - 1e-4 ? 1.0 : 0.0;
}

vec3 lightmap(vec2 levels) {
    return texture(Lightmap, clamp(levels / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
}

bool surfaceLevels(vec3 p, vec3 normal, out vec2 levels) {
    levels = vec2(0.0);
    if (VolumeReady == 0) return false;
    vec3 q = p + normal * 0.5 - VolumeOrigin - 0.5;
    ivec3 base = ivec3(floor(q));
    vec3 f = q - vec3(base);
    vec2 sum = vec2(0.0);
    float total = 0.0;
    for (int i = 0; i < 8; i++) {
        ivec3 corner = ivec3(i & 1, (i >> 1) & 1, i >> 2);
        ivec3 cell = base + corner;
        if (!inVolume(cell)) continue;
        vec4 v = voxel(cell);
        if (int(v.r + 0.5) == 16) continue;
        vec3 w = mix(1.0 - f, f, vec3(corner));
        float weight = w.x * w.y * w.z;
        sum += v.gb * weight;
        total += weight;
    }
    if (total < 0.05) return false;
    levels = sum / total;
    return true;
}

float gauss(float x) {
    return exp(-x * x);
}

float dippedBeam(vec3 local) {
    float h = degrees(atan(local.x, local.z)), t = -local.y / local.z;
    float cut = smoothstep(-0.012, 0.022, t);
    float core = pow(0.035 / max(t, 0.035), 2.4) * (1.0 - smoothstep(0.22, 0.5, t));
    float flood = 0.3 * pow(0.07 / max(t, 0.07), 2.4) * (1.0 - smoothstep(0.4, 0.8, t));
    float near = NEAR_LIGHT * smoothstep(0.05, 0.2, t) * (1.0 - smoothstep(0.9, 1.6, t));
    return cut * (core * gauss(h / 13.0) + flood * gauss(h / 30.0) + near * gauss(h / 34.0));
}

float hazeBeam(vec3 local) {
    float h = degrees(atan(local.x, local.z)), v = degrees(atan(local.y, local.z));
    return 0.5 * gauss(h / 16.0) * gauss((v + 3.0) / 9.0);
}

float bloom(vec3 view, vec3 origin, float dist, float radius) {
    vec3 axis = origin / dist;
    float facing = dot(view, axis);
    if (facing <= 0.0) return 0.0;
    vec3 offset = view * (dist / facing) - origin;
    float r = length(offset) / max(radius, dist * BLOOM_ANGLE);
    return exp(-r * r * 6.0) + 0.22 * exp(-r * 2.2);
}

vec3 positionAt(ivec2 pixel, float depth) {
    vec4 p = InverseViewProjection * vec4((vec2(pixel) + 0.5) / ScreenSize * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec3 positionAt(ivec2 pixel) {
    return positionAt(pixel, texelFetch(SceneDepth, pixel, 0).r);
}

vec3 normalAt(ivec2 pixel, vec3 p) {
    vec3 l = positionAt(pixel - ivec2(1, 0)), r = positionAt(pixel + ivec2(1, 0));
    vec3 d = positionAt(pixel - ivec2(0, 1)), u = positionAt(pixel + ivec2(0, 1));
    vec3 dx = length(r - p) < length(p - l) ? r - p : p - l;
    vec3 dy = length(u - p) < length(p - d) ? u - p : p - d;
    vec3 n = normalize(cross(dx, dy));
    if (dot(n, p - Eye) > 0.0) n = -n;
    vec3 a = abs(n);
    if (max(a.x, max(a.y, a.z)) > 0.92) n = a.x > a.y && a.x > a.z ? vec3(sign(n.x), 0.0, 0.0) : a.y > a.z ? vec3(0.0, sign(n.y), 0.0) : vec3(0.0, 0.0, sign(n.z));
    return n;
}

float faceShade(vec3 n) {
    if (n.y > 0.9) return 1.0;
    if (n.y < -0.9) return 0.5;
    if (abs(n.z) > 0.9) return 0.8;
    if (abs(n.x) > 0.9) return 0.6;
    return 0.85;
}

float mapDepth(int slot, ivec2 cell) {
    vec2 rg = texelFetch(Shadows, ivec2(cell.x, cell.y + slot * MAP.y), 0).rg;
    return dot(rg, vec2(65280.0, 255.0)) / 65535.0 * RANGE;
}

vec2 mapCoord(vec3 local) {
    vec2 t = local.xy / local.z;
    return (t - vec2(SPAN_X.x, SPAN_Y.x)) / vec2(SPAN_X.y - SPAN_X.x, SPAN_Y.y - SPAN_Y.x) * vec2(MAP) - 0.5;
}

float surfaceVisibility(int slot, vec3 delta, vec3 local, vec3 normal, vec3 forward, vec3 right, vec3 up) {
    vec2 coord = mapCoord(local);
    if (any(lessThan(coord, vec2(-0.5))) || any(greaterThan(coord, vec2(MAP) - 0.5))) return 1.0;
    ivec2 base = ivec2(floor(coord));
    vec2 w = fract(coord);
    float dist = length(delta), visible = 0.0;
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 cell = clamp(base + ivec2(x, y), ivec2(0), MAP - 1);
            vec2 t = mix(vec2(SPAN_X.x, SPAN_Y.x), vec2(SPAN_X.y, SPAN_Y.y), (vec2(cell) + 0.5) / vec2(MAP));
            vec3 ray = normalize(forward + right * t.x + up * t.y);
            float facing = dot(ray, normal);
            float expected = abs(facing) > 0.02 ? dot(delta, normal) / facing : dist;
            float spread = 1.5 + dist * 0.012 / max(abs(dot(delta, normal)) / dist, 0.01);
            expected = min(clamp(expected, dist - spread, dist + spread), RANGE - 0.3);
            float weight = (x == 0 ? 1.0 - w.x : w.x) * (y == 0 ? 1.0 - w.y : w.y);
            visible += weight * smoothstep(expected - 0.25, expected - 0.08, mapDepth(slot, cell));
        }
    }
    return visible;
}

float airVisibility(int slot, vec3 local, float dist) {
    vec2 coord = mapCoord(local) + 0.5;
    if (any(lessThan(coord, vec2(0.0))) || any(greaterThanEqual(coord, vec2(MAP)))) return 1.0;
    return smoothstep(dist - 0.6, dist, mapDepth(slot, ivec2(coord)));
}

vec2 clipSpan(vec2 span, vec3 view, vec3 origin, vec3 n) {
    float k = dot(n, view), c = dot(n, origin);
    if (abs(k) < 1e-6) return c <= 0.0 ? span : vec2(1.0, 0.0);
    return k > 0.0 ? vec2(max(span.x, c / k), span.y) : vec2(span.x, min(span.y, c / k));
}

float scattering(vec3 view, float maxT, vec3 origin, vec3 forward, vec3 right, vec3 up, int slot, float jitter) {
    float delta = dot(origin, view);
    float D = max(length(origin - view * delta), 0.25);
    float chord = RANGE * RANGE - dot(origin, origin) + delta * delta;
    if (chord <= 0.0) return 0.0;
    chord = sqrt(chord);
    vec2 span = vec2(max(0.0, delta - chord), min(maxT, delta + chord));
    span = clipSpan(span, view, origin, forward);
    span = clipSpan(span, view, origin, forward * HAZE_SIDE - right);
    span = clipSpan(span, view, origin, forward * HAZE_SIDE + right);
    span = clipSpan(span, view, origin, up + forward * HAZE_BELOW);
    span = clipSpan(span, view, origin, forward * HAZE_ABOVE - up);
    float a = span.x, b = span.y;
    if (b <= a) return 0.0;
    float thetaA = atan((a - delta) / D), thetaB = atan((b - delta) / D);
    float sum = 0.0;
    for (int k = 0; k < HAZE_SAMPLES; k++) {
        float t = delta + D * tan(mix(thetaA, thetaB, (float(k) + jitter) / float(HAZE_SAMPLES)));
        vec3 x = view * t - origin;
        float dist = length(x);
        vec3 dir = x / dist;
        vec3 local = vec3(dot(dir, right), dot(dir, up), dot(dir, forward));
        if (local.z <= 0.02 || dist > RANGE) continue;
        float beam = hazeBeam(local);
        if (beam < 1e-4) continue;
        float cosine = dot(dir, -view);
        float g = HAZE_FORWARD;
        float phase = 0.0796 * (HAZE_FORWARD_SHARE * (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cosine, 1.5) + 1.0 - HAZE_FORWARD_SHARE);
        float reach = 1.0 + min(dist, HAZE_REACH_CAP) * HAZE_REACH * (1.0 - cosine * cosine);
        float visible = slot >= 0 ? airVisibility(slot, local, dist) : 1.0;
        sum += beam * phase * reach * visible * (1.0 - smoothstep(RANGE * 0.6, RANGE, dist));
    }
    return sum * (thetaB - thetaA) / (D * float(HAZE_SAMPLES));
}

float lensVisibility(vec3 origin, float dist) {
    vec4 clip = ViewProjection * vec4(origin, 1.0);
    if (clip.w <= 0.05) return 0.0;
    vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return 0.0;
    float scene = length(positionAt(ivec2(uv * ScreenSize)) - Eye);
    return smoothstep(dist - 0.35, dist - 0.12, scene);
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 scene = texelFetch(SceneColor, pixel, 0);
    float depth = texelFetch(SceneDepth, pixel, 0).r;
    vec4 far = InverseViewProjection * vec4(texCoord * 2.0 - 1.0, 1.0, 1.0);
    vec3 view = normalize(far.xyz / far.w - Eye);
    bool surface = depth < 0.99999;
    vec3 p = surface ? positionAt(pixel, depth) : Eye + view * 1000.0;
    float sceneDistance = length(p - Eye);
    vec3 normal = vec3(0.0, 1.0, 0.0);
    bool normalReady = !surface;
    vec2 surfaceLight = vec2(0.0);
    bool surfaceKnown = false;
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));

    vec3 light = vec3(0.0), ambient = vec3(0.0), air = vec3(0.0);
    float weight = 0.0;
    for (int i = 0; i < LampCount; i++) {
        vec4 a = lampData(i, 0), b = lampData(i, 1), c = lampData(i, 2), d = lampData(i, 3);
        vec3 origin = a.xyz, forward = b.xyz, color = c.rgb, right = d.xyz, up = cross(right, forward);
        bool headlight = a.w <= 0.0;
        float range = headlight ? RANGE : a.w;
        int slot = int(b.w);
        bool omni = slot <= -2;

        if (surface) {
            vec3 delta = p - origin;
            float dist = length(delta);
            if (dist < range && dist > 0.05) {
                if (!normalReady) {
                    normal = normalAt(pixel, p);
                    normalReady = true;
                    surfaceKnown = surfaceLevels(p, normal, surfaceLight);
                }
                vec3 local = vec3(dot(delta, right), dot(delta, up), dot(delta, forward));
                float lambert = max(0.0, dot(normal, -delta / dist));
                float e = 0.0;
                if (headlight) {
                    if (local.z > 0.02 && lambert > 0.0) {
                        e = HEADLIGHT * dippedBeam(local) * lambert / (dist * dist + 1.0)
                                * smoothstep(0.35, 0.9, dist) * (1.0 - smoothstep(RANGE * 0.5, RANGE, dist));
                        if (e > 0.002) e *= slot >= 0 ? surfaceVisibility(slot, delta, local, normal, forward, right, up)
                                : openPath(p, normal, origin, sceneDistance);
                    }
                } else {
                    float lobe = omni ? 1.0 : pow(max(0.0, (local.z / dist + 0.45) / 1.45), 1.2);
                    e = SMALL_LAMP * lobe * lambert / (dist * dist + 0.35) * (1.0 - smoothstep(range * 0.45, range, dist));
                    if (e > 0.002 && !omni) e *= openPath(p, normal, origin, sceneDistance);
                }
                if (e > 0.0) {
                    int levels = int(d.w + 0.5);
                    float w = e * dot(color, vec3(0.3, 0.59, 0.11));
                    light += color * e;
                    ambient += lightmap(vec2(levels / 16, levels % 16)) * w;
                    weight += w;
                }
            }
        }

        vec3 seen = origin - Eye;
        if (headlight && i < HazeCount && Haze > 0.0)
            air += color * (AIR_LIGHT * Haze * scattering(view, min(sceneDistance, 96.0), seen, forward, right, up, slot, noise));

        float lampDistance = length(seen);
        if (lampDistance > 0.3 && !omni) {
            vec3 toCamera = -seen / lampDistance;
            vec3 local = vec3(dot(toCamera, right), dot(toCamera, up), dot(toCamera, forward));
            if (local.z > 0.0) {
                float toward = 0.35 * pow(local.z, 1.5) + (headlight ? dippedBeam(local) : 0.0);
                float strength = toward * (headlight ? FLARE_HEADLIGHT : FLARE_LAMP) / (1.0 + lampDistance * lampDistance / 144.0);
                float glow = bloom(view, seen, lampDistance, headlight ? BLOOM_HEADLIGHT : BLOOM_LAMP);
                if (strength * glow > 0.002) air += color * strength * glow * lensVisibility(origin, lampDistance);
            }
        }
    }

    vec3 result = scene.rgb;
    if (weight > 0.0) {
        vec3 base = max(surfaceKnown ? lightmap(surfaceLight) : ambient / weight, vec3(0.035));
        float room = max(CEILING - dot(base, vec3(0.3, 0.59, 0.11)), 0.25);
        vec3 albedo = clamp(scene.rgb / (base * faceShade(normal)), 0.0, 1.0);
        float energy = dot(light, vec3(0.3, 0.59, 0.11));
        vec3 gain = energy > 1e-4 ? light * (room * (1.0 - exp(-energy / room)) / energy) : vec3(0.0);
        float peak = max(gain.r, max(gain.g, gain.b));
        if (peak > room * 1.25) gain *= room * 1.25 / peak;
        result += albedo * gain;
    }
    result += air;
    result = mix(result, 1.0 - 0.2 * exp(-(result - 0.8) / 0.2), step(0.8, result));
    result += (noise - 0.5) / 255.0;
    fragColor = vec4(result, scene.a);
}
