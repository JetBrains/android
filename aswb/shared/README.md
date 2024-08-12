# Common plugin libraries

This directory contains libraries that are used across the bazel plugin, but
do not depend on IntelliJ itself (i.e. no dependencies on the JBRSDK). This is
to allow users to easily be run outside of the IDE.

No code inside here should depend on the plugin API.