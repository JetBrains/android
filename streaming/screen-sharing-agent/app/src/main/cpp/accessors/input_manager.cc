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

#include "input_manager.h"

#include <mutex>

#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void InputManager::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);
  if (input_manager_.IsNull()) {
    input_manager_ = ServiceManager::GetServiceAsInterface(jni, "input", "android/hardware/input/IInputManager");
    JClass input_manager_class = input_manager_.GetClass();
    inject_input_event_method_ = input_manager_class.GetMethod("injectInputEvent", "(Landroid/view/InputEvent;I)Z");
    add_port_association_method_ = input_manager_class.GetMethod("addPortAssociation", "(Ljava/lang/String;I)V");
    remove_port_association_method_ = input_manager_class.GetMethod("removePortAssociation", "(Ljava/lang/String;)V");
    input_manager_.MakeGlobal();
  }
}

void InputManager::InjectInputEvent(Jni jni, const JObject& input_event, InputEventInjectionSync mode) {
  InitializeStatics(jni);
  if (!input_manager_.CallBooleanMethod(jni, inject_input_event_method_, input_event.ref(), static_cast<jint>(mode))) {
    string event_text = input_event.ToString();
    if (event_text.empty()) {
      event_text = input_event.GetClass(jni).GetName(jni);
    }
    Log::E("Unable to inject an input event %s", event_text.c_str());
  }
}

void InputManager::AddPortAssociation(Jni jni, const string& input_port, int32_t display_id) {
  InitializeStatics(jni);
  input_manager_.CallVoidMethod(jni, add_port_association_method_, JString(jni, input_port).ref(), display_id);
}

void InputManager::RemovePortAssociation(Jni jni, const string& input_port) {
  InitializeStatics(jni);
  input_manager_.CallVoidMethod(jni, remove_port_association_method_, JString(jni, input_port).ref());
}

JObject InputManager::input_manager_;
jmethodID InputManager::inject_input_event_method_;
jmethodID InputManager::add_port_association_method_;
jmethodID InputManager::remove_port_association_method_;

}  // namespace screensharing
