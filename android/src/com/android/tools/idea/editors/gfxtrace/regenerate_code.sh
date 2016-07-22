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

GRPC_VERSION=${GRPC_VERSION:-0.13.2}
GRPC=${GRPC:-$MAVEN_ROOT/io/grpc/protoc-gen-grpc-java/$GRPC_VERSION/protoc-gen-grpc-java-$GRPC_VERSION-$HOST_OS-x86_64.exe}

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

gen_proto() {
  INPUT=$1
  OUTPUT=$2
  $PROTOC --proto_path $GPU_BASE --java_out $JAVA_BASE $INPUT

  # Studio currently uses proto2, but the profiler team has jarjar'ed up proto3.
  # Update the generate code to use the jarjar'ed version in a hakish but efficient way.
  if [ -f $OUTPUT ]; then
    sed -i 's/com\.google\.protobuf/com.google.protobuf3jarjar/g' $OUTPUT
  fi
}

gen_grpc() {
  INPUT=$1
  $PROTOC --proto_path $GPU_BASE --plugin=protoc-gen-grpc-java=$GRPC --grpc-java_out=$JAVA_BASE $INPUT
}

# First generate all the proto based files
echo "Generating protos..."
SERVICE=$JAVA_BASE/com/android/tools/idea/editors/gfxtrace/service
gen_proto $GPU_BASE/api/snippets/snippets.proto $SERVICE/snippets/SnippetsProtos.java
gen_proto $GPU_BASE/framework/log/log.proto $SERVICE/log/LogProtos.java
gen_proto $GPU_BASE/gapid/gfxapi/gfxapi.proto $SERVICE/gfxapi/GfxAPIProtos.java
gen_proto $GPU_BASE/gapid/memory/memory.proto $SERVICE/memory/MemoryProtos.java
gen_proto $GPU_BASE/gapid/service/service.proto $SERVICE/ServiceProtos.java
gen_proto $GPU_BASE/gapid/service/path/path.proto $SERVICE/path/PathProtos.java
gen_proto $GPU_BASE/gapid/vertex/vertex.proto $SERVICE/vertex/VertexProtos.java

gen_grpc $GPU_BASE/gapid/service/service.proto

# And now run codergen for the serializers
echo "Running codergen..."
pushd $GPU_BASE >/dev/null
$CODERGEN --java $REPO_ROOT/tools ./...
popd >/dev/null
echo "All done"
