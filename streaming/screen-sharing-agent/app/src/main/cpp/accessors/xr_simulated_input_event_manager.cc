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

#include "xr_simulated_input_event_manager.h"

#include "service_manager.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void XrSimulatedInputEventManager::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);

  if (xr_simulated_input_event_manager_.IsNull()) {
    xr_simulated_input_event_manager_ = ServiceManager::GetServiceAsInterface(
        jni, "xrsimulatedinputeventmanagerservice", "android/xr/libxrinput/IXRSimulatedInputEventManager");
    JClass manager_class = xr_simulated_input_event_manager_.GetClass();
    inject_xr_simulated_motion_event_method_ = manager_class.GetMethod("injectXRSimulatedMotionEvent", "(Landroid/view/MotionEvent;)V");
    xr_simulated_input_event_manager_.MakeGlobal();
  }
}

void XrSimulatedInputEventManager::InjectXrSimulatedMotionEvent(Jni jni, const JObject& input_event) {
  InitializeStatics(jni);
  xr_simulated_input_event_manager_.CallVoidMethod(jni, inject_xr_simulated_motion_event_method_, input_event.ref());
}

JObject XrSimulatedInputEventManager::xr_simulated_input_event_manager_;
jmethodID XrSimulatedInputEventManager::inject_xr_simulated_motion_event_method_ = nullptr;

}  // namespace screensharing
