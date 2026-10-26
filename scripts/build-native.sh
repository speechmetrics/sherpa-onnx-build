#!/usr/bin/env bash
# Builds the sherpa-onnx JNI library without text-to-speech, so GPL
# espeak-ng is never compiled in, and copies the libraries to
# natives/<platform>/.
#
# Usage: scripts/build-native.sh <osx-aarch64|osx-x64|win-x64> [sherpa-version]
set -euo pipefail

platform=${1:?platform required: osx-aarch64, osx-x64 or win-x64}
version=${2:-$(cat "$(dirname "$0")/../SHERPA_VERSION")}
root=$(cd "$(dirname "$0")/.." && pwd)
src=$root/work/sherpa-onnx-$version
build=$root/work/build-$platform
out=$root/natives/$platform

if [ ! -d "$src" ]; then
  git clone --depth 1 --branch "v$version" https://github.com/k2-fsa/sherpa-onnx.git "$src"
fi

# sherpa-onnx lists the TTS JNI functions in the macOS export list even
# when TTS is off, which fails the link with undefined symbols.
exp=$src/sherpa-onnx/jni/sherpa-onnx-symbols.exp
grep -v -E 'OfflineTts|GeneratedAudio' "$exp" > "$exp.tmp" && mv "$exp.tmp" "$exp"

cmake_args=(
  -D BUILD_SHARED_LIBS=ON
  -D CMAKE_BUILD_TYPE=Release
  -D SHERPA_ONNX_ENABLE_JNI=ON
  -D SHERPA_ONNX_ENABLE_TTS=OFF
  -D SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=ON
  -D SHERPA_ONNX_ENABLE_BINARY=OFF
  -D SHERPA_ONNX_ENABLE_C_API=OFF
  -D SHERPA_ONNX_ENABLE_PORTAUDIO=OFF
  -D SHERPA_ONNX_ENABLE_WEBSOCKET=OFF
  -D CMAKE_INSTALL_PREFIX="$build/install"
)
case $platform in
  osx-aarch64) cmake_args+=(-D CMAKE_OSX_ARCHITECTURES=arm64 -D CMAKE_OSX_DEPLOYMENT_TARGET=11.0) ;;
  osx-x64) cmake_args+=(-D CMAKE_OSX_ARCHITECTURES=x86_64 -D CMAKE_OSX_DEPLOYMENT_TARGET=10.15) ;;
  win-x64) cmake_args+=(-A x64) ;;
  *) echo "unknown platform: $platform" >&2; exit 2 ;;
esac

cmake -S "$src" -B "$build" "${cmake_args[@]}"
cmake --build "$build" --config Release --parallel
cmake --install "$build" --config Release

rm -rf "$out"
mkdir -p "$out"
case $platform in
  osx-*) cp "$build/install/lib/libsherpa-onnx-jni.dylib" "$build/install/lib/libonnxruntime.dylib" "$out/" ;;
  win-x64)
    # cmake --install places DLLs in lib/ or bin/ depending on the generator
    for f in sherpa-onnx-jni.dll onnxruntime.dll; do
      cp "$(find "$build/install" -name "$f" | head -1)" "$out/"
    done
    ;;
esac
ls -l "$out"
