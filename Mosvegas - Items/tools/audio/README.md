# Real recorded item sounds

The assets are trimmed from real Freesound recordings licensed CC0 1.0. Authors, source pages, preview downloads and exact excerpt boundaries are documented in the project root `AUDIO_CREDITS.md`.

To reproduce the conversion, install `numpy` and `soundfile`, then run `python tools/audio/convert_recordings.py --preview-dir build/audio-preview`. The script downloads only the three credited audio files into a temporary directory, verifies their SHA-256 hashes and converts four excerpts into mono Ogg Vorbis at 44.1 kHz. Use `--source-dir PATH` for already-downloaded MP3 files.

The conversion removes DC/low rumble, resamples, adds short boundary fades and adjusts volume. It adds no synthesized audio or pitch changes. Decoded Ogg files are checked for codec, sample rate, channels, finite samples and clipping. Python packages, source MP3s and optional WAV previews are not needed to build or play the mod and are excluded from the distributable assets.
