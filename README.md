# sherpa-onnx-build

Builds [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for Java
(JNI) **without text-to-speech**, and publishes the jars to GitHub
Packages for Phon (and later SpeechCatcher).

## Why

The upstream prebuilt sherpa-onnx libraries include text-to-speech,
which statically links [espeak-ng](https://github.com/espeak-ng/espeak-ng)
(GPL-3). Phon uses only speech recognition, voice activity detection and
speaker diarization. Building with `-DSHERPA_ONNX_ENABLE_TTS=OFF` leaves
espeak-ng out entirely; everything that remains is under permissive
licences (see `NOTICE`).

## Artifacts

Group `ca.speechmetrics`, version `<sherpa-version>-notts`
(for example `1.13.8-notts`):

| Artifact | Contents |
|---|---|
| `sherpa-onnx-jvm` | Java API compiled from the same sherpa-onnx tag; `Automatic-Module-Name: com.k2fsa.sherpa.onnx` |
| `sherpa-onnx-native-osx-aarch64` | `libsherpa-onnx-jni.dylib`, `libonnxruntime.dylib` |
| `sherpa-onnx-native-osx-x64` | the same, Intel |
| `sherpa-onnx-native-win-x64` | `sherpa-onnx-jni.dll`, `onnxruntime.dll` |

The native jars use the layout sherpa-onnx's `LibraryUtils` loads from:
`sherpa-onnx/native/<platform>/`.

Consumers resolve them from `https://maven.pkg.github.com/speechmetrics/*`
(or `.../speechmetrics/sherpa-onnx-build`).

## espeak-ng check

`./gradlew checkNoEspeak` scans every native library for byte patterns
that only exist when espeak-ng or piper-phonemize is linked in
(`espeak_`, `phonemize_eSpeak`, `espeak-ng`, `piper_phonemize`,
`piper-phonemize`). Packaging and every publish depend on it. A plain
`espeak` search is not used: it matches `OfflineSpeakerDiarization`.

## Building locally (macOS)

```
scripts/build-native.sh osx-aarch64
scripts/build-native.sh osx-x64        # cross-compiled on Apple silicon
./gradlew checkNoEspeak assemble publishToMavenLocal
```

`win-x64` builds on Windows with Visual Studio 2022 and Git Bash
(`scripts/build-native.sh win-x64`). The sherpa-onnx source is cloned
into `work/`, the libraries land in `natives/<platform>/`.

`publishToMavenLocal` needs all three platforms; with only some, run
`./gradlew checkNoEspeak nativeJar-osx-aarch64`.

## CI and publishing

`.github/workflows/build.yml` builds the three platforms, checks them
and packages the jars on every push and pull request. Publishing runs
only from **Actions → Build → Run workflow** with *Publish* ticked.

## Updating sherpa-onnx

1. Change `SHERPA_VERSION`.
2. Push; check the build is green.
3. Run the workflow with *Publish*.

## Patch applied to sherpa-onnx

`scripts/build-native.sh` removes the `OfflineTts`/`GeneratedAudio`
entries from `sherpa-onnx/jni/sherpa-onnx-symbols.exp`. Upstream lists
those JNI functions in the macOS export list even when TTS is off, which
fails the link.

## Licence

Build scripts: Apache-2.0 (`LICENSE`). The built artifacts carry the
licences listed in `NOTICE`.
