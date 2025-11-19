#!/bin/sh
# Generates the external.srcjar files. This is done manually so that the srcjar
# file can be checked in so that the rest of the tests can operate on a checked
# in srcjar; generating it at build time would change the test semantics.

cd $(dirname $0)

cat > ExternalKtInSrcJar.kt << EOF
package com.example.external

object ExternalKtInSrcJar {
  const val STRING: String = "ExternalKtInSrcJar"
}
EOF

jar -c -f external.srcjar -C ../../.. com/example/external/ExternalKtInSrcJar.kt
rm ExternalKtInSrcJar.kt