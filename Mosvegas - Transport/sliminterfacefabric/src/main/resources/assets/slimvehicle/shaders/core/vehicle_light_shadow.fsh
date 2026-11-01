#version 150
uniform sampler2D Lamps;
uniform sampler2D Volume;
uniform vec3 VolumeOrigin;
uniform int VolumeReady;
uniform int ShadowCount;
uniform int OccluderCount;

out vec4 fragColor;

const ivec3 VOLUME = ivec3(96, 32, 96);
const int TILES = 8;
const ivec2 MAP = ivec2(256, 128);
const float RANGE = 48.0;
const vec2 SPAN_X = vec2(-1.0, 1.0);
const vec2 SPAN_Y = vec2(-0.6, 0.3);
const int OCCLUDER_ROW = 32;

vec4 lampData(int row, int k) {
    return texelFetch(Lamps, ivec2(k, row), 0);
}

int blockAt(ivec3 cell) {
    ivec2 texel = ivec2((cell.y % TILES) * VOLUME.x + cell.x, (cell.y / TILES) * VOLUME.z + cell.z);
    return int(texelFetch(Volume, texel, 0).r * 255.0 + 0.5);
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
        if (all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, VOLUME))) {
            int kind = blockAt(cell);
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

float traceCars(vec3 origin, vec3 dir, float car, float nearest) {
    for (int i = 0; i < OccluderCount; i++) {
        vec4 box = lampData(OCCLUDER_ROW + i, 0), size = lampData(OCCLUDER_ROW + i, 1);
        if (size.w == car) continue;
        float c = cos(box.w), s = sin(box.w);
        vec3 d = origin - box.xyz;
        vec3 o = vec3(c * d.x + s * d.z, d.y, -s * d.x + c * d.z);
        vec3 r = vec3(c * dir.x + s * dir.z, dir.y, -s * dir.x + c * dir.z);
        r = mix(r, vec3(1e-6), lessThan(abs(r), vec3(1e-6)));
        vec3 t0 = (-size.xyz - o) / r, t1 = (size.xyz - o) / r;
        vec3 lo = min(t0, t1), hi = max(t0, t1);
        float enter = max(max(lo.x, lo.y), lo.z), leave = min(min(hi.x, hi.y), hi.z);
        if (leave > max(enter, 0.0) && enter < nearest) nearest = max(enter, 0.0);
    }
    return nearest;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    int slot = pixel.y / MAP.y;
    if (slot >= ShadowCount) {
        fragColor = vec4(1.0, 1.0, 0.0, 1.0);
        return;
    }
    vec3 origin = lampData(slot, 0).xyz, forward = lampData(slot, 1).xyz, right = lampData(slot, 3).xyz;
    vec3 up = cross(right, forward);
    vec2 f = (vec2(pixel.x, pixel.y - slot * MAP.y) + 0.5) / vec2(MAP);
    vec3 dir = normalize(forward + right * mix(SPAN_X.x, SPAN_X.y, f.x) + up * mix(SPAN_Y.x, SPAN_Y.y, f.y));
    float free = traceCars(origin, dir, lampData(slot, 2).w, VolumeReady == 0 ? RANGE : traceBlocks(origin, dir, RANGE));
    float depth = floor(clamp(free / RANGE, 0.0, 1.0) * 65535.0);
    fragColor = vec4(floor(depth / 256.0) / 255.0, mod(depth, 256.0) / 255.0, 0.0, 1.0);
}
