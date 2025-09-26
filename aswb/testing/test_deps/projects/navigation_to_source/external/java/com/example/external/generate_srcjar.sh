#!/bin/sh
# Generates the external.srcjar files. This is done manually so that the srcjar
# file can be checked in so that the rest of the tests can operate on a checked
# in srcjar; generating it at build time would change the test semantics.

cd $(dirname $0)

cat > ExternalJavaInSrcJar.java << EOF
package com.example.external;

public class ExternalJavaInSrcJar {

  public static final String STRING = "ExternalJavaInSrcJar";

}
EOF

jar -c -f external.srcjar -C ../../.. com/example/external/ExternalJavaInSrcJar.java
rm ExternalJavaInSrcJar.java