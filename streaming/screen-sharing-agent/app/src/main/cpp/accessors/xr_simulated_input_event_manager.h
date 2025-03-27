/*
 * Copyright (C) 2025 The Android Open Source Project
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

#include "jvm.h"

namespace screensharing {

// Provides access to the android.services.xr.simulatedinputeventmanager.IXRSimulatedInputEventManager class.
class XrSimulatedInputEventManager {
public:
  // Injects an input event. May throw a Java exception if it fails.
  static void InjectXrSimulatedMotionEvent(Jni jni, const JObject& input_event);

private:
  static void InitializeStatics(Jni jni);

  static JObject xr_simulated_input_event_manager_;
  static jmethodID inject_xr_simulated_motion_event_method_;
};

}  // namespace screensharing
