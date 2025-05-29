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

#pragma once

#include "common.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the android.os.ServiceManager.getService method.
class ServiceManager {
public:
  static JObject GetServiceAsInterface(Jni jni, const char* name, const char* type, bool wait_if_necessary = true, bool allow_null = false);
  static JObject GetService(Jni jni, const char* name, bool wait_if_necessary = true, bool allow_null = false) {
    return GetInstance(jni).GetServiceInternal(jni, name, wait_if_necessary, allow_null);
  }

private:
  explicit ServiceManager(Jni jni);
  static ServiceManager& GetInstance(Jni jni);
  JObject GetServiceInternal(Jni jni, const char* name, bool wait_if_necessary, bool allow_null);

  JClass service_manager_class_;
  jmethodID get_service_method_ = nullptr;
  jmethodID wait_for_service_method_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(ServiceManager);
};

}  // namespace screensharing
