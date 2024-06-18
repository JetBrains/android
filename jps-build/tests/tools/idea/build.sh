#!/bin/sh
echo Downloading!
find $JPS_WORKSPACE

if [[ -z "${BUILD_WORKSPACE_DIRECTORY}" ]]; then
    # Running 'bazel build'
    if ! [ -f build_downloads/download.txt ]; then
        # File does not exist, fail.
        echo Cannot download the file
        exit 1
    else
      echo File to dowload found
    fi
else
    # Running 'bazel run', download
    mkdir -p build_downloads
    touch build_downloads/download.txt
fi

echo Building!
mkdir -p out/studio/classes/module
cp $JPS_WORKSPACE/Test.class out/studio/classes/module/Test.class
mkdir -p out/studio/artifacts/module-tests
echo tools/idea/out/studio/classes/module > out/studio/artifacts/module-tests/module.to.test.classpath.txt
