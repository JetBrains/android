#!/bin/sh
set -e

# This script assumes a standard android studio repo checkout and refers to files from other repositories

absname() {
  echo "$(cd $1 && pwd)"
}

SCRIPT_DIR=$(absname "$(dirname "${BASH_SOURCE[0]}")")
JAVA_BASE=$(absname "$SCRIPT_DIR/../../../../../..")
REPO_ROOT=$(absname "$JAVA_BASE/../../../../..")
GPU_BASE=$(absname "$REPO_ROOT/tools/gpu/src/android.googlesource.com/platform/tools/gpu")
MAVEN_ROOT=$(absname "$REPO_ROOT/prebuilts/tools/common/m2/repository")
HOST_OS=$(uname | tr A-Z a-z | sed -e "s/darwin/osx/g")

PROTO_VERSION=${PROTO_VERSION:-3.0.0-beta-2}
PROTOC=${PROTOC:-$MAVEN_ROOT/com/google/protobuf/protoc/$PROTO_VERSION/protoc-$PROTO_VERSION-$HOST_OS-x86_64.exe}

CODERGEN=${CODERGEN:-${GAPID_OUT:-${BUILDS:-~/gapid}}/release/bin/codergen}

if [ ! -x $CODERGEN ]
then
  echo "Build codergen in $CODERGEN from $GPU_BASE first."
  exit 1
fi

command -v $PROTOC >/dev/null 2>&1 || {
  echo "$PROTOC not found in $PATH"
  exit 1
}

# First generate all the proto based files
echo "Generating protos..."
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/api/snippets/snippets.proto
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/framework/log/log.proto
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/gfxapi/gfxapi.proto
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/memory/memory.proto
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/service/service.proto
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/service/path/path.proto
$PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/vertex/vertex.proto

# And now run codergen for the serializers
echo "Running codergen..."
pushd $GPU_BASE >/dev/null
$CODERGEN --java $REPO_ROOT/tools ./...
popd >/dev/null
echo "All done"
