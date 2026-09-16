#!/bin/bash
#
# Copyright (C) 2026 The Android Open Source Project
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

# This script collects all the class needed to test [LocalNavigationEventTransformTest] int a jar file.
NAVIGATIONEVENT_PATH="androidx/navigationevent"
NAVIGATIONEVENT_COMPOSE_PATH="$NAVIGATIONEVENT_PATH/compose"
COMPOSE_RUNTIME="androidx/compose/runtime"
DESIGNER_SCENE="com/android/tools/idea/compose/preview/scene"

OUTPUT_JAR="localnavigationevent.jar"
# Compiling the dependencies from the paths and Generate the JAR
kotlinc $NAVIGATIONEVENT_PATH/*.kt $NAVIGATIONEVENT_COMPOSE_PATH/*.kt $COMPOSE_RUNTIME/*.kt $DESIGNER_SCENE/*.kt -d $OUTPUT_JAR
