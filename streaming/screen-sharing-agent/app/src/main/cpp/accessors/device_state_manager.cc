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
#include <regex>
#include <string>

#include "agent.h"
#include "log.h"
#include "service_manager.h"
#include "shell_command_executor.h"
#include "string_printf.h"
#include "string_util.h"

namespace screensharing {

using namespace std;

class DeviceStateInfo {
public:
  static void InitializeStatics(const JObject& device_state_info);
  static vector<DeviceState> GetSupportedStates(const JObject& device_state_info);
  static vector <DeviceState> GetSupportedStatesUsingPrintStatesCommand();
  static int32_t GetBaseStateIdentifier(const JObject& device_state_info);
  static int32_t GetCurrentStateIdentifier(const JObject& device_state_info);

private:
  static mutex static_initialization_mutex_;
  static bool statics_initialized_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID supported_states_field_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID base_state_field_;  // GUARDED_BY(static_initialization_mutex_)
  static jfieldID current_state_field_;  // GUARDED_BY(static_initialization_mutex_)
};

void DeviceStateInfo::InitializeStatics(const JObject& device_state_info) {
  unique_lock lock(static_initialization_mutex_);
  if (!statics_initialized_) {
    JClass clazz = device_state_info.GetClass();
    if (Agent::feature_level() >= 35) {
      supported_states_field_ = clazz.GetFieldId("supportedStates", "Ljava/util/ArrayList;");
      base_state_field_ = clazz.GetFieldId("baseState", "Landroid/hardware/devicestate/DeviceState;");
      current_state_field_ = clazz.GetFieldId("currentState", "Landroid/hardware/devicestate/DeviceState;");
    } else {
      base_state_field_ = clazz.GetFieldId("baseState", "I");
      current_state_field_ = clazz.GetFieldId("currentState", "I");
    }
    statics_initialized_ = true;
  }
}

vector<DeviceState> DeviceStateInfo::GetSupportedStates(const JObject& device_state_info) {
  if (Agent::feature_level() >= 35) {
    vector<DeviceState> supported_states;
    JIterable states = JIterable(device_state_info.GetObjectField(supported_states_field_));
    for (JIterator iterator = states.Iterator(); iterator.HasNext();) {
      supported_states.emplace_back(iterator.Next());
    }
    return supported_states;
  } else {
    return GetSupportedStatesUsingPrintStatesCommand();
  }
}

vector<DeviceState> DeviceStateInfo::GetSupportedStatesUsingPrintStatesCommand() {
  vector<DeviceState> supported_states;
  string states = ExecuteShellCommand("cmd device_state print-states");
  if (states.empty()) {
    return supported_states;
  }
  basic_regex states_regex(R"(DeviceState\{identifier=(\d+), name='(\w+)'(.*)\})");
  basic_regex properties_regex(R"(, (\w+)=(\w+))");
  auto states_begin = sregex_iterator(states.begin(), states.end(), states_regex);
  auto states_end = sregex_iterator();
  for (sregex_iterator i = states_begin; i != states_end; ++i) {
    const smatch& state = *i;
    int32_t id = ParseInt(state[1], -1);
    string state_name = state[2];
    const string& prop = state[3];
    uint32_t properties = 0;
    auto properties_begin = sregex_iterator(prop.begin(), prop.end(), properties_regex);
    auto properties_end = sregex_iterator();
    for (sregex_iterator j = properties_begin; j != properties_end; ++j) {
      const smatch& property = *j;
      string name = property[1];
      string value = property[2];
      if (name == "app_accessible") {
        if (value == "false") {
          properties |= static_cast<uint32_t>(DeviceState::PROPERTY_APP_INACCESSIBLE);
        }
      } else if (name == "cancel_when_requester_not_on_top" && value == "true") {
        properties |= static_cast<uint32_t>(DeviceState::PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP);
      }
    }

    Log::D("DeviceStateInfo::GetSupportedStatesUsingPrintStatesCommand id=%d state_name=\"%s\" properties=%u",
           id, state_name.c_str(), properties);
    supported_states.emplace_back(id, state_name, properties, 0);
  }
  return supported_states;
}

int32_t DeviceStateInfo::GetBaseStateIdentifier(const JObject& device_state_info) {
  if (Agent::feature_level() >= 35) {
    return DeviceState::GetIdentifier(device_state_info.GetObjectField(base_state_field_));
  } else {
    return device_state_info.GetIntField(base_state_field_);
  }
}

int32_t DeviceStateInfo::GetCurrentStateIdentifier(const JObject& device_state_info) {
  if (Agent::feature_level() >= 35) {
    return DeviceState::GetIdentifier(device_state_info.GetObjectField(current_state_field_));
  } else {
    return device_state_info.GetIntField(current_state_field_);
  }
}

mutex DeviceStateInfo::static_initialization_mutex_;
bool DeviceStateInfo::statics_initialized_ = false;
jfieldID DeviceStateInfo::supported_states_field_ = nullptr;
jfieldID DeviceStateInfo::base_state_field_ = nullptr;
jfieldID DeviceStateInfo::current_state_field_ = nullptr;

bool DeviceStateManager::InitializeStatics(Jni jni) {
  if (Agent::feature_level() < 31) {
    return false;  // Support for device states was introduced in API 31.
  }

  {
    unique_lock lock(static_initialization_mutex_);
    if (!statics_initialized_) {
      statics_initialized_ = true;
      device_state_manager_ =
          ServiceManager::GetServiceAsInterface(jni, "device_state", "android/hardware/devicestate/IDeviceStateManager", true);
      if (device_state_manager_.IsNull()) {
        return false;
      }
      JClass clazz = device_state_manager_.GetClass();
      get_device_state_info_method_ = clazz.GetMethod("getDeviceStateInfo", "()Landroid/hardware/devicestate/DeviceStateInfo;");

      JObject device_state_info = device_state_manager_.CallObjectMethod(get_device_state_info_method_);
      if (device_state_info.IsNull()) {
        // May happen if the initial state hasn't been committed.
        Log::W(jni.GetAndClearException(), "Device state is not available");
        device_state_manager_.MakeGlobal();
        return true;
      }
      DeviceStateInfo::InitializeStatics((device_state_info));
      supported_device_states_ = DeviceStateInfo::GetSupportedStates(device_state_info);
      if (supported_device_states_.size() == 1) {
        supported_device_states_.clear();  // A single device state is treated the same as no states.
      }
      if (!supported_device_states_.empty()) {
        jmethodID register_callback_method =
            clazz.GetMethod("registerCallback", "(Landroid/hardware/devicestate/IDeviceStateManagerCallback;)V");
        request_state_method_ = clazz.GetMethod("requestState", "(Landroid/os/IBinder;II)V");
        if (Agent::feature_level() >= 33) {
          cancel_state_request_method_ = clazz.GetMethod("cancelStateRequest", "()V");
        }

        binder_class_ = jni.GetClass("android/os/Binder");
        binder_constructor_ = binder_class_.GetConstructor("()V");

        binder_class_.MakeGlobal();
        device_state_manager_.MakeGlobal();

        // Instantiate DeviceStateManagerCallback and call IDeviceStateManager.registerCallback passing it as the parameter.
        clazz = jni.GetClass("com/android/tools/screensharing/DeviceStateManagerCallback");
        JObject callback = clazz.NewObject(clazz.GetConstructor("()V"));
        device_state_manager_.CallVoidMethod(jni, register_callback_method, callback.ref());

        // Obtain a fresh device state info after setting up the callback.
        JObject device_state_info2 = device_state_manager_.CallObjectMethod(jni, get_device_state_info_method_);
        if (device_state_info2.IsNull()) {
          device_state_info2 = std::move(device_state_info);
        }
        unique_lock state_lock(state_mutex_);
        base_state_identifier_ = DeviceStateInfo::GetBaseStateIdentifier(device_state_info2);
        current_state_identifier_ = DeviceStateInfo::GetCurrentStateIdentifier(device_state_info2);
        Log::D("DeviceStateManager::InitializeStatics: base_state_identifier_=%d current_state_identifier_=%d",
               base_state_identifier_, current_state_identifier_);
      }
    }
  }

  return true;
}

const std::vector<DeviceState>& DeviceStateManager::GetSupportedDeviceStates(Jni jni) {
  InitializeStatics(jni);
  return supported_device_states_;
}

int32_t DeviceStateManager::GetDeviceStateIdentifier(Jni jni) {
  InitializeStatics(jni);
  return current_state_identifier_;
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
  if (current_state_identifier_ >= 0) {
    listener->OnDeviceStateChanged(current_state_identifier_);
  }
}

void DeviceStateManager::RemoveDeviceStateListener(DeviceStateListener* listener) {
  device_state_listeners_.Remove(listener);
}

void DeviceStateManager::OnDeviceStateChanged(Jni jni, const JObject& device_state_info) {
  auto base_state = DeviceStateInfo::GetBaseStateIdentifier(device_state_info);
  auto current_state = DeviceStateInfo::GetCurrentStateIdentifier(device_state_info);
  Log::D("DeviceStateManager::OnDeviceStateChanged: base_state=%d, current_state=%d", base_state, current_state);
  bool cancel_state_override;
  bool state_changed;
  {
    unique_lock lock(state_mutex_);
    cancel_state_override = state_overridden_ && base_state != base_state_identifier_;
    state_changed = current_state != current_state_identifier_;
    base_state_identifier_ = base_state;
    current_state_identifier_ = current_state;
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
bool DeviceStateManager::statics_initialized_ = false;
JObject DeviceStateManager::device_state_manager_;
jmethodID DeviceStateManager::get_device_state_info_method_ = nullptr;
jmethodID DeviceStateManager::request_state_method_ = nullptr;
jmethodID DeviceStateManager::cancel_state_request_method_ = nullptr;
JClass DeviceStateManager::binder_class_;
jmethodID DeviceStateManager::binder_constructor_ = nullptr;
ConcurrentList<DeviceStateManager::DeviceStateListener> DeviceStateManager::device_state_listeners_;
vector<DeviceState> DeviceStateManager::supported_device_states_;
mutex DeviceStateManager::state_mutex_;
int32_t DeviceStateManager::base_state_identifier_ = INVALID_DEVICE_STATE_IDENTIFIER;
int32_t DeviceStateManager::current_state_identifier_ = INVALID_DEVICE_STATE_IDENTIFIER;
bool DeviceStateManager::state_overridden_ = false;

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_DeviceStateManagerCallback_onDeviceStateInfoChanged(
    JNIEnv* jni_env, jobject thiz, jobject info) {
  Jni jni = Jni(jni_env);
  JObject device_state_info(jni, std::move(info));
  DeviceStateManager::OnDeviceStateChanged(jni, device_state_info);
  device_state_info.Release();
}

}  // namespace screensharing
