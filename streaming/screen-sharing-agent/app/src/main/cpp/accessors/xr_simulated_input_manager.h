/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include "common.h"
#include "jvm.h"

namespace screensharing {

// Provides access to few methods of the android.services.xr.simulatedinputmanager.IXrSimulatedInputManager class.
// Can only be used by the thread that created the object.
class XrSimulatedInputManager {
public:
  static void InjectHeadRotation(Jni jni, const float data[3]);
  static void InjectHeadMovement(Jni jni, const float data[3]);
  static void InjectHeadAngularVelocity(Jni jni, const float data[3]);
  static void InjectHeadMovementVelocity(Jni jni, const float data[3]);

private:
  static void InitializeStatics(Jni jni);

  static JObject xr_simulated_input_manager_;
  static jmethodID inject_head_rotation_method_;
  static jmethodID inject_head_movement_method_;
  static jmethodID inject_head_angular_velocity_method_;
  static jmethodID inject_head_movement_velocity_method_;

  DISALLOW_COPY_AND_ASSIGN(XrSimulatedInputManager);
};

}  // namespace screensharing
