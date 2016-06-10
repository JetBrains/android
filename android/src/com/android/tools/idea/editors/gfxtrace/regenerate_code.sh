#!/bin/sh
set -e

# This script assumes a standard android studio repo checkout and refers to files from other repositories

absname() {
  echo "$(cd $1 && pwd)"
}

PROTOC=${PROTOC:-protoc}

SCRIPT_DIR=$(absname "$(dirname "${BASH_SOURCE[0]}")")
JAVA_BASE=$(absname "$SCRIPT_DIR/../../../../../..")
TOOLS_ROOT=$(absname "$JAVA_BASE/../../../..")
GPU_BASE=$(absname "$TOOLS_ROOT/gpu/src/android.googlesource.com/platform/tools/gpu")

if [ ! -x $TOOLS_ROOT/gpu/bin/codergen ]
then
  echo "Build codergen in $TOOLS_ROOT/gpu/bin from $GPU_BASE first."
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
$TOOLS_ROOT/gpu/bin/codergen --java $TOOLS_ROOT ./...
popd >/dev/null
echo "All done"
