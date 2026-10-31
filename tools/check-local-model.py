#!/usr/bin/env python3
"""Optional real-model smoke test: pip install sherpa-onnx==1.12.40 numpy.

Usage: python tools/check-local-model.py /path/to/model /path/to/16khz-mono.wav
This measures the current host, NOT Android latency or transcription quality.
"""
import hashlib
import json
from pathlib import Path
import re
import sys
import time
import wave

import numpy as np
import sherpa_onnx

model, audio = map(Path, sys.argv[1:])
source = (Path(__file__).parent.parent / "app/src/main/java/helium314/keyboard/latin/voice/ModelFiles.kt").read_text()
specs = re.findall(r'Spec\("([^"]+)", (\d+), "([0-9a-f]{64})"\)', source)
assert len(specs) == 4
for name, size, checksum in specs:
    path = model / name
    assert path.stat().st_size == int(size), name
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            digest.update(chunk)
    assert digest.hexdigest() == checksum, name
with wave.open(str(audio)) as wav:
    assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (16000, 1, 2)
    samples = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32) / 32768
start = time.monotonic()
recognizer = sherpa_onnx.OfflineRecognizer.from_transducer(
    encoder=str(model / "encoder.int8.onnx"),
    decoder=str(model / "decoder.int8.onnx"),
    joiner=str(model / "joiner.int8.onnx"),
    tokens=str(model / "tokens.txt"),
    model_type="nemo_transducer", num_threads=2, provider="cpu", debug=False,
)
loaded = time.monotonic()
stream = recognizer.create_stream()
stream.accept_waveform(16000, samples)
recognizer.decode_stream(stream)
finished = time.monotonic()
text = stream.result.text
assert text.strip(), "Empty transcript"
print(json.dumps(dict(audio_seconds=len(samples)/16000, load_seconds=loaded-start,
    decode_seconds=finished-loaded, text=text, platform="Linux VM, not Android"), ensure_ascii=False, indent=2))
