/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "xr_simulated_input_manager.h"

#include "agent.h"
#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void XrSimulatedInputManager::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);

  if (xr_simulated_input_manager_.IsNull()) {
    xr_simulated_input_manager_ = ServiceManager::GetServiceAsInterface(
        jni, "xrsimulatedinputmanager", "android/services/xr/simulatedinputmanager/IXrSimulatedInputManager");
    JClass xr_simulated_input_manager_class = xr_simulated_input_manager_.GetClass();
    inject_head_rotation_method_ = xr_simulated_input_manager_class.GetMethod("injectHeadRotation", "([f)V");
    inject_head_movement_method_ = xr_simulated_input_manager_class.GetMethod("injectHeadMovement", "([f)V");
    inject_head_angular_velocity_method_ = xr_simulated_input_manager_class.GetMethod("injectHeadAngularVelocity", "([f)V");
    inject_head_movement_velocity_method_ = xr_simulated_input_manager_class.GetMethod("injectHeadMovementVelocity", "([f)V");
    xr_simulated_input_manager_.MakeGlobal();
  }
}

void XrSimulatedInputManager::InjectHeadRotation(Jni jni, const float data[3]) {
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_rotation_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::InjectHeadMovement(Jni jni, const float data[3]) {
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_movement_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::InjectHeadAngularVelocity(Jni jni, const float data[3]) {
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_angular_velocity_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::InjectHeadMovementVelocity(Jni jni, const float data[3]) {
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_movement_velocity_method_, JFloatArray::Create(jni, 3, data).ref());
}

JObject XrSimulatedInputManager::xr_simulated_input_manager_;
jmethodID XrSimulatedInputManager::inject_head_rotation_method_ = nullptr;
jmethodID XrSimulatedInputManager::inject_head_movement_method_ = nullptr;
jmethodID XrSimulatedInputManager::inject_head_angular_velocity_method_ = nullptr;
jmethodID XrSimulatedInputManager::inject_head_movement_velocity_method_ = nullptr;

}  // namespace screensharing
