#!/bin/sh
# Generates the external.srcjar files. This is done manually so that the srcjar
# file can be checked in so that the rest of the tests can operate on a checked
# in srcjar; generating it at build time would change the test semantics.

cd $(dirname $0)

mkdir srcjar
cat > srcjar/ExternalKtInSrcJar1.kt << EOF
@file:JvmMultifileClass
@file:JvmName("ExternalKtInSrcJarUtils")
package com.example.external.withjvmname.srcjar

const val STRING1 = "TopLevelExternalKtInSrcJar1"
object ExternalKtInSrcJar1 {
  const val STRING: String = "ExternalKtInSrcJar"
}
EOF

cat > srcjar/ExternalKtInSrcJar2.kt << EOF
@file:JvmMultifileClass
@file:JvmName("ExternalKtInSrcJarUtils")
package com.example.external.withjvmname.srcjar

const val STRING2 = "TopLevelExternalKtInSrcJar2"
object ExternalKtInSrcJar2 {
  const val STRING: String = "ExternalKtInSrcJar"
}
EOF

jar -c -f external.srcjar -C ../../../.. com/example/external/withjvmname/srcjar/
rm -r srcjar