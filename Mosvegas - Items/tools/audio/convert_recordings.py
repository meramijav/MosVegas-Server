"""Convert the credited CC0 Freesound recordings into the bundled item sounds."""

import argparse
import hashlib
import json
from pathlib import Path
import tempfile
import urllib.request

import numpy as np
import soundfile as sf

RATE = 44_100
SOURCES = {
    "can_cvgre.mp3": ("https://cdn.freesound.org/previews/754/754896_14780064-hq.mp3",
                      "84c3da08810eeed34ab8e28e17f9bde6ccb3aa72afa12e51ff15bde02af67de5"),
    "swallow_soundcollectah.mp3": ("https://cdn.freesound.org/previews/158/158479_1868848-hq.mp3",
                                "17244b5fd8957aa23f39b43858a175caa6d1da9fc8adc6229d3dcb0a09813213"),
    "swallow_lucaslara.mp3": ("https://cdn.freesound.org/previews/154/154452_2592491-hq.mp3",
                            "e3362aca3affe1984ec53e2deadea813990421dd669ab023e01d97fc102b5976"),
}
CLIPS = {
    "can_open": ("can_cvgre.mp3", 0.00, 0.55, 0.60, 0.14),
    "drink_1": ("swallow_soundcollectah.mp3", 0.00, 0.45, 0.42, 0.055),
    "drink_2": ("swallow_soundcollectah.mp3", 0.54, 0.96, 0.42, 0.055),
    "drink_3": ("swallow_lucaslara.mp3", 3.53, 4.03, 0.42, 0.055),
}


def convert(source, start, end, peak_limit, rms_target):
    audio, rate = sf.read(source)
    if audio.ndim > 1:
        audio = np.mean(audio, axis=1)
    audio = audio[round(start * rate):round(end * rate)].copy()
    if not len(audio):
        raise ValueError(f"Empty excerpt: {source}")
    audio -= np.mean(audio)
    frequencies = np.fft.rfftfreq(len(audio), 1 / rate)
    # Remove low handling rumble and soften inaudible high-frequency noise.
    # All audible material remains the real recording; no synthesized layers.
    response = frequencies**2 / (frequencies**2 + 60**2)
    response /= np.sqrt(1 + (frequencies / 10_500) ** 8)
    audio = np.fft.irfft(np.fft.rfft(audio) * response, n=len(audio))
    if rate != RATE:
        audio = np.interp(np.arange(round(len(audio) * RATE / rate)) / RATE,
                          np.arange(len(audio)) / rate, audio)
    fade_in, fade_out = min(round(0.004 * RATE), len(audio)), min(round(0.035 * RATE), len(audio))
    audio[:fade_in] *= np.sin(np.linspace(0, np.pi / 2, fade_in)) ** 2
    audio[-fade_out:] *= np.sin(np.linspace(np.pi / 2, 0, fade_out)) ** 2
    peak, rms = float(np.max(np.abs(audio))), float(np.sqrt(np.mean(audio**2)))
    return audio * min(peak_limit / max(peak, 1e-10), rms_target / max(rms, 1e-10))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=Path(tempfile.gettempdir()) / "mosvegas-real-audio-sources")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] / "src/main/resources/assets/mosvegas_items/sounds")
    parser.add_argument("--preview-dir", type=Path)
    args = parser.parse_args()
    args.source_dir.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    for name, (url, expected_hash) in SOURCES.items():
        source = args.source_dir / name
        if not source.exists():
            request = urllib.request.Request(url, headers={"User-Agent": "MosVegasAudioAssetConversion/1.0"})
            with urllib.request.urlopen(request, timeout=30) as response:
                source.write_bytes(response.read())
        if hashlib.sha256(source.read_bytes()).hexdigest() != expected_hash:
            raise ValueError(f"Source changed; check its author/license before updating the hash: {source}")
    decoded_clips, report = [], []
    for name, (source, start, end, peak, rms) in CLIPS.items():
        target = args.output / f"{name}.ogg"
        sf.write(target, convert(args.source_dir / source, start, end, peak, rms), RATE,
                 format="OGG", subtype="VORBIS", compression_level=0.68)
        decoded, rate = sf.read(target)
        info = sf.info(target)
        decoded_peak = float(np.max(np.abs(decoded)))
        if rate != RATE or info.channels != 1 or info.subtype != "VORBIS" or not np.isfinite(decoded).all() or decoded_peak >= 0.98:
            raise RuntimeError(f"Invalid or clipped Ogg: {target}")
        decoded_clips.append(decoded)
        report.append({"file": target.name, "bytes": target.stat().st_size, "duration": round(info.duration, 3),
                       "rate": rate, "channels": info.channels, "codec": info.subtype,
                       "peak_db": round(20 * np.log10(max(decoded_peak, 1e-10)), 2)})
        if args.preview_dir:
            args.preview_dir.mkdir(parents=True, exist_ok=True)
            sf.write(args.preview_dir / f"{name}.wav", decoded, rate, subtype="PCM_16")
    if args.preview_dir:
        sequence = np.concatenate([np.concatenate((clip, np.zeros(round(0.22 * RATE)))) for clip in decoded_clips])
        sf.write(args.preview_dir / "real_recordings_preview.wav", sequence, RATE, subtype="PCM_16")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
