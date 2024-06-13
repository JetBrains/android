/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "audio_manager.h"

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

constexpr int GET_DEVICES_INPUTS = 1;  // See https://developer.android.com/reference/android/media/AudioManager#GET_DEVICES_INPUTS

int32_t AudioManager::GetInputAudioDeviceId(Jni jni, int32_t device_type) {
  Log::D("AudioManager::GetInputAudioDeviceId(%d)", device_type);
  JClass audio_manager_class = jni.GetClass("android/media/AudioManager");
  jmethodID method = audio_manager_class.GetStaticMethod("getDevicesStatic", "(I)[Landroid/media/AudioDeviceInfo;");
  JObjectArray devices(audio_manager_class.CallStaticObjectMethod(method, GET_DEVICES_INPUTS));
  auto length = devices.GetLength();
  jmethodID get_type_method;
  jmethodID get_id_method;
  for (int i = 0; i < length; ++i) {
    JObject device = devices.GetElement(i);
    if (i == 0) {
      JClass audio_device_info_class = device.GetClass();
      get_type_method = audio_device_info_class.GetMethod("getType", "()I");
      get_id_method = audio_device_info_class.GetMethod("getId", "()I");
    }
    if (device.CallIntMethod(get_type_method) == device_type) {
      return device.CallIntMethod(get_id_method);
    }
  }
  return -1;
}

}  // namespace screensharing
