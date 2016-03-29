#!/bin/sh

# This script assumes a standard android studio repo checkout and refers to files from other repositories

absname() {
  echo "$(cd $1 && pwd)"
}

JAVA_BASE=$(absname "../../../../../..")
TOOLS_ROOT=$(absname "$JAVA_BASE/../../../..")
GPU_BASE=$(absname  "$TOOLS_ROOT/gpu/src/android.googlesource.com/platform/tools/gpu")

# First generate all the proto based files
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/api/snippets/snippets.proto
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/framework/log/log.proto
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/gfxapi/gfxapi.proto
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/memory/memory.proto
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/service/service.proto
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/service/path/path.proto
protoc --proto_path $GPU_BASE --java_out $JAVA_BASE $GPU_BASE/gapid/vertex/vertex.proto

# And now run codergen for the serializers
cd $GPU_BASE
codergen --java $TOOLS_ROOT ./...