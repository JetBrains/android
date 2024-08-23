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
mkdir -p out/studio/classes/test/module.to.test
mkdir -p out/studio/artifacts/module-tests
mv $JPS_WORKSPACE/com out/studio/classes/test/module.to.test
cd out/studio/classes/test/module.to.test
zip -r ../../../artifacts/example.jar com
cd -
echo tools/idea/out/studio/classes/test/module.to.test > out/studio/artifacts/module-tests/module.to.test.classpath.txt
echo tools/idea/out/studio/artifacts/example.jar >> out/studio/artifacts/module-tests/module.to.test.classpath.txt
