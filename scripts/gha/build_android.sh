#!/bin/bash

unset ANDROID_SDK_ROOT
export JAVA_HOME=$GITHUB_WORKSPACE/java
export ANDROID_HOME=$GITHUB_WORKSPACE/sdk
export PATH=$PATH:$JAVA_HOME/bin:$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/tools/bin

pushd android || exit 1

# SDL3 build has its own file names, so it doesn't replace the SDL2 one in the release
GRADLE_ARGS=
NAME_SUFFIX=
if [ "$GH_SDL3" == "true" ]; then
	GRADLE_ARGS=-Psdl3
	NAME_SUFFIX=-sdl3
fi

./gradlew assembleContinuous --no-daemon $GRADLE_ARGS || exit 1

popd || exit 1

mkdir -p artifacts/

mv android/app/build/outputs/apk/continuous/app-continuous.apk artifacts/xash3d-fwgs-android$NAME_SUFFIX.apk
tar -cJvf artifacts/xash3d-fwgs-android$NAME_SUFFIX-mappings.tar.zst -C android/app/build/outputs/mapping/continuous '.'

