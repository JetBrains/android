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

#include "device_state_manager.h"

#include <cctype>
#include <mutex>

#include "agent.h"
#include "jvm.h"
#include "log.h"
#include "service_manager.h"
#include "shell_command_executor.h"
#include "string_printf.h"
#include "string_util.h"

namespace screensharing {

using namespace std;

bool DeviceStateManager::InitializeStatics(Jni jni) {
  if (Agent::feature_level() < 31) {
    return false;  // Support for device states was introduced in API 31.
  }

  {
    unique_lock lock(static_initialization_mutex_);

    if (!initialized_) {
      initialized_ = true;
      device_state_manager_ =
          ServiceManager::GetServiceAsInterface(jni, "device_state", "android/hardware/devicestate/IDeviceStateManager", true);
      if (device_state_manager_.IsNotNull()) {
        JClass device_state_manager_class = device_state_manager_.GetClass();
        jmethodID register_callback_method =
            device_state_manager_class.GetMethod("registerCallback", "(Landroid/hardware/devicestate/IDeviceStateManagerCallback;)V");
        request_state_method_ = device_state_manager_class.GetMethod("requestState", "(Landroid/os/IBinder;II)V");
        if (Agent::feature_level() >= 33) {
          cancel_state_request_method_ = device_state_manager_class.GetMethod("cancelStateRequest", "()V");
        }
        get_device_state_info_method_ =
            device_state_manager_class.GetMethod("getDeviceStateInfo", "()Landroid/hardware/devicestate/DeviceStateInfo;");

        JClass device_state_info_class = jni.GetClass("android/hardware/devicestate/DeviceStateInfo");
        base_state_field_ = device_state_info_class.GetFieldId("baseState", "I");
        current_state_field_ = device_state_info_class.GetFieldId("currentState", "I");

        binder_class_ = jni.GetClass("android/os/Binder");
        binder_constructor_ = binder_class_.GetConstructor("()V");
        binder_class_.MakeGlobal();
        device_state_manager_.MakeGlobal();

        // Instantiate DeviceStateManagerCallback and call IDeviceStateManager.registerCallback passing it as the parameter.
        JClass device_state_manager_callback_class = jni.GetClass("com/android/tools/screensharing/DeviceStateManagerCallback");
        JObject callback = device_state_manager_callback_class.NewObject(device_state_manager_callback_class.GetConstructor("()V"));
        device_state_manager_.CallVoidMethod(jni, register_callback_method, callback.ref());
      }
    }
  }
  if (device_state_manager_.IsNull()) {
    return false;
  }

  unique_lock lock(state_mutex_);
  if (current_state_ < 0) {
    JObject state_info = device_state_manager_.CallObjectMethod(jni, get_device_state_info_method_);
    if (state_info.IsNull()) {
      // May happen if the initial state hasn't been committed.
      Log::W(jni.GetAndClearException(), "Device state is not available");
      return true;
    }
    current_base_state_ = state_info.GetIntField(base_state_field_);
    current_state_ = state_info.GetIntField(current_state_field_);
    Log::D("DeviceStateManager::InitializeStatics: baseState=%d currentState=%d", current_base_state_, current_state_);
  }
  return true;
}

string DeviceStateManager::GetSupportedStates() {
  if (Agent::feature_level() < 31) {
    return "";  // 'cmd device_state print-states' was introduced in API 31.
  }
  return ExecuteShellCommand("cmd device_state print-states");
}

int32_t DeviceStateManager::GetDeviceState(Jni jni) {
  if (!InitializeStatics(jni)) {
    return -1;
  }
  return current_state_;
}

void DeviceStateManager::RequestState(Jni jni, int32_t state_id, int32_t flags) {
  if (!InitializeStatics(jni)) {
    return;
  }
  JObject token = binder_class_.NewObject(jni, binder_constructor_);
  Log::D("DeviceStateManager::RequestState: requesting state: %d", state_id);
  // Call IDeviceStateManager.requestState.
  device_state_manager_.CallVoidMethod(jni, request_state_method_, token.ref(), state_id, flags);
  {
    unique_lock lock(state_mutex_);
    state_overridden_ = true;
  }
}

void DeviceStateManager::AddDeviceStateListener(DeviceStateListener* listener) {
  device_state_listeners_.Add(listener);
  unique_lock lock(state_mutex_);
  if (current_state_ >= 0) {
    listener->OnDeviceStateChanged(current_state_);
  }
}

void DeviceStateManager::RemoveDeviceStateListener(DeviceStateListener* listener) {
  device_state_listeners_.Remove(listener);
}

void DeviceStateManager::OnDeviceStateChanged(Jni jni, jobject device_state_info) {
  auto base_state = jni->GetIntField(device_state_info, base_state_field_);
  auto current_state = jni->GetIntField(device_state_info, current_state_field_);
  Log::D("DeviceStateManager::OnDeviceStateChanged: base_state=%d, current_state=%d", base_state, current_state);
  bool cancel_state_override;
  bool state_changed;
  {
    unique_lock lock(state_mutex_);
    cancel_state_override = state_overridden_ && base_state != current_base_state_;
    state_changed = current_state != current_state_;
    current_base_state_ = base_state;
    current_state_ = current_state;
  }

  if (state_changed) {
    NotifyListeners(current_state);
  }

  if (cancel_state_override && cancel_state_request_method_ != nullptr) {
    // Call IDeviceStateManager.cancelStateRequest.
    device_state_manager_.CallVoidMethod(jni, cancel_state_request_method_);
  }
}

void DeviceStateManager::NotifyListeners(int32_t device_state) {
  device_state_listeners_.ForEach([device_state](auto listener) {
    listener->OnDeviceStateChanged(device_state);
  });
}

mutex DeviceStateManager::static_initialization_mutex_;
bool DeviceStateManager::initialized_ = false;
JObject DeviceStateManager::device_state_manager_;
jmethodID DeviceStateManager::get_device_state_info_method_ = nullptr;
jmethodID DeviceStateManager::request_state_method_ = nullptr;
jmethodID DeviceStateManager::cancel_state_request_method_ = nullptr;
jfieldID DeviceStateManager::base_state_field_ = nullptr;
jfieldID DeviceStateManager::current_state_field_ = nullptr;
JClass DeviceStateManager::binder_class_;
jmethodID DeviceStateManager::binder_constructor_ = nullptr;
ConcurrentList<DeviceStateManager::DeviceStateListener> DeviceStateManager::device_state_listeners_;
mutex DeviceStateManager::state_mutex_;
int32_t DeviceStateManager::current_base_state_ = -1;
int32_t DeviceStateManager::current_state_ = -1;
bool DeviceStateManager::state_overridden_ = false;

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_DeviceStateManagerCallback_onDeviceStateInfoChanged(
    JNIEnv* jni_env, jobject thiz, jobject info) {
  DeviceStateManager::OnDeviceStateChanged(jni_env, info);
}

}  // namespace screensharing
