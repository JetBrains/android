#!/bin/bash
#
# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This script packs all the classes needed to test [ViewTreeLifecycleTransformTest] into a jar file.
LIFECYCLE_PATH="androidx/lifecycle"
SAVEDSTATE_PATH="androidx/savedstate"
FAKE_REGISTRY_PATH="_layoutlib_/_internal_/androidx/lifecycle"

# Compiling the dependencies from the paths
javac $SAVEDSTATE_PATH/*.java $LIFECYCLE_PATH/*.java $FAKE_REGISTRY_PATH/*.java android/view/View.java
# Generate the JAR
jar -cf viewtreelifecycleowner.jar $LIFECYCLE_PATH/*.class $SAVEDSTATE_PATH/*.class $FAKE_REGISTRY_PATH/*.class android/view/View.class
# Clean up the classes
rm $LIFECYCLE_PATH/*.class
rm $SAVEDSTATE_PATH/*.class
rm $FAKE_REGISTRY_PATH/*.class
rm android/view/View.class
