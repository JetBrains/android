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
#include "shell_command_executor.h"
#include "string_util.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void XrSimulatedInputManager::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);

  if (xr_simulated_input_manager_.IsNull()) {
    xr_simulated_input_manager_ = ServiceManager::GetServiceAsInterface(
        jni, "xrsimulatedinputmanager", "android/services/xr/simulatedinputmanager/IXrSimulatedInputManager", false, true);
    if (xr_simulated_input_manager_.IsNull()) {
      string value =
          RTrim(ExecuteShellCommand("getprop persist.device_config.com_android_xr.com.android.xr.flags.enable_xr_simulated_env"));
      if (value != "true" && value != "1") {
        Log::Fatal(XR_DEVICE_IS_NOT_CONFIGURED_FOR_MIRRORING,
                   "The property persist.device_config.com_android_xr.com.android.xr.flags.enable_xr_simulated_env is not set to true");
      }
      xr_simulated_input_manager_ = ServiceManager::GetServiceAsInterface(
          jni, "xrsimulatedinputmanager", "android/services/xr/simulatedinputmanager/IXrSimulatedInputManager");
    }
    JClass clazz = xr_simulated_input_manager_.GetClass();
    inject_head_rotation_method_ = clazz.GetMethod("injectHeadRotation", "([F)V");
    inject_head_movement_method_ = clazz.GetMethod("injectHeadMovement", "([F)V");
    inject_head_angular_velocity_method_ = clazz.GetMethod("injectHeadAngularVelocity", "([F)V");
    inject_head_movement_velocity_method_ = clazz.GetMethod("injectHeadMovementVelocity", "([F)V");
    recenter_method_ = clazz.GetMethod("recenter", "()V");
    set_passthrough_coefficient_method_ = clazz.GetMethod("setPassthroughCoefficient", "(F)V");
    set_environment_method_ = clazz.GetMethod("setEnvironment", "(B)V");
    jmethodID register_listener_method = clazz.GetMethod("registerListener", "(Landroid/services/xr/simulatedinputmanager/IXrSimulatedInputStateCallback;)V");
    JClass callback_class = jni.GetClass("com/android/tools/screensharing/XrSimulatedInputStateCallback");
    JObject callback = callback_class.NewObject(callback_class.GetConstructor("()V"));
    xr_simulated_input_manager_.CallVoidMethod(jni, register_listener_method, callback.ref());
    // Obtain a fresh passthrough coefficient and environment after setting up the callback.
    passthrough_coefficient_ = xr_simulated_input_manager_.CallFloatMethod(jni, clazz.GetMethod("getPassthroughCoefficient", "()F"));
    environment_ = xr_simulated_input_manager_.CallByteMethod(jni, clazz.GetMethod("getEnvironment", "()B"));
    Log::D("XrSimulatedInputManager::InitializeStatics: passthrough_coefficient_=%.3g environment=%d", passthrough_coefficient_, environment_);
    xr_simulated_input_manager_.MakeGlobal();
  }
}

void XrSimulatedInputManager::InjectHeadRotation(Jni jni, const float data[3]) {
  Log::D("XrSimulatedInputManager::InjectHeadRotation([%f, %f, %f])", data[0], data[1], data[2]);
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_rotation_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::InjectHeadMovement(Jni jni, const float data[3]) {
  Log::D("XrSimulatedInputManager::InjectHeadMovement([%f, %f, %f])", data[0], data[1], data[2]);
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_movement_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::InjectHeadAngularVelocity(Jni jni, const float data[3]) {
  Log::D("XrSimulatedInputManager::InjectHeadAngularVelocity([%f, %f, %f])", data[0], data[1], data[2]);
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_angular_velocity_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::InjectHeadMovementVelocity(Jni jni, const float data[3]) {
  Log::D("XrSimulatedInputManager::InjectHeadMovementVelocity([%f, %f, %f])", data[0], data[1], data[2]);
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, inject_head_movement_velocity_method_, JFloatArray::Create(jni, 3, data).ref());
}

void XrSimulatedInputManager::Recenter(Jni jni) {
  Log::D("XrSimulatedInputManager::Recenter");
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, recenter_method_);
}

void XrSimulatedInputManager::SetPassthroughCoefficient(Jni jni, float passthrough) {
  Log::D("XrSimulatedInputManager::SetPassthroughCoefficient(%.3g)", passthrough);
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, set_passthrough_coefficient_method_, passthrough);
}

void XrSimulatedInputManager::SetEnvironment(Jni jni, int32_t environment) {
  Log::D("XrSimulatedInputManager::SetEnvironment(%d)", environment);
  InitializeStatics(jni);
  xr_simulated_input_manager_.CallVoidMethod(jni, set_environment_method_, environment);
}

void XrSimulatedInputManager::AddEnvironmentListener(Jni jni, EnvironmentListener* listener) {
  Log::D("XrSimulatedInputManager::AddEnvironmentListener(%p)", listener);
  InitializeStatics(jni);
  environment_listeners_.Add(listener);
  unique_lock lock(environment_mutex_);
  if (passthrough_coefficient_ >= 0) {
    listener->OnPassthroughCoefficientChanged(passthrough_coefficient_);
  }
  if (environment_ != 0xFF) {
    listener->OnEnvironmentChanged(environment_);
  }
}

void XrSimulatedInputManager::RemoveEnvironmentListener(EnvironmentListener* listener) {
  Log::D("XrSimulatedInputManager::RemoveEnvironmentListener(%p)", listener);
  environment_listeners_.Remove(listener);
}

void XrSimulatedInputManager::OnPassthroughCoefficientChanged(float passthrough_coefficient) {
  unique_lock lock(environment_mutex_);
  if (passthrough_coefficient_ != passthrough_coefficient) {
    passthrough_coefficient_ = passthrough_coefficient;
    environment_listeners_.ForEach([passthrough_coefficient](auto listener) {
      listener->OnPassthroughCoefficientChanged(passthrough_coefficient);
    });
  }
}

void XrSimulatedInputManager::OnEnvironmentChanged(int32_t environment) {
  unique_lock lock(environment_mutex_);
  if (environment_ != environment) {
    environment_ = environment;
    environment_listeners_.ForEach([environment](auto listener) {
      listener->OnEnvironmentChanged(environment);
    });
  }
}

JObject XrSimulatedInputManager::xr_simulated_input_manager_;
jmethodID XrSimulatedInputManager::inject_head_rotation_method_ = nullptr;
jmethodID XrSimulatedInputManager::inject_head_movement_method_ = nullptr;
jmethodID XrSimulatedInputManager::inject_head_angular_velocity_method_ = nullptr;
jmethodID XrSimulatedInputManager::inject_head_movement_velocity_method_ = nullptr;
jmethodID XrSimulatedInputManager::recenter_method_ = nullptr;
jmethodID XrSimulatedInputManager::set_passthrough_coefficient_method_ = nullptr;
jmethodID XrSimulatedInputManager::set_environment_method_ = nullptr;
ConcurrentList<XrSimulatedInputManager::EnvironmentListener> XrSimulatedInputManager::environment_listeners_;
mutex XrSimulatedInputManager::environment_mutex_;
float XrSimulatedInputManager::passthrough_coefficient_ = UNKNOWN_PASSTHROUGH_COEFFICIENT;
int32_t XrSimulatedInputManager::environment_ = UNKNOWN_ENVIRONMENT;

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_XrSimulatedInputStateCallback_onPassthroughCoefficientChange(
    JNIEnv* jni_env, jobject thiz, jfloat passthrough_coefficient) {
  Log::D("XrSimulatedInputStateCallback.onPassthroughCoefficientChange(%.3g)", passthrough_coefficient);
  if (passthrough_coefficient >= 0 && passthrough_coefficient <= 1) {
    XrSimulatedInputManager::OnPassthroughCoefficientChanged(passthrough_coefficient);
  }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_XrSimulatedInputStateCallback_onEnvironmentChange(JNIEnv* jni_env, jobject thiz, jbyte environment) {
  Log::D("XrSimulatedInputStateCallback.onEnvironmentChange(%d)", environment);
  if (environment >= 0) {
    XrSimulatedInputManager::OnEnvironmentChanged(environment);
  }
}

}  // namespace screensharing
