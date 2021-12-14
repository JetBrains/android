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

#include "service_manager.h"

#include <string>

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

ServiceManager::ServiceManager(Jni jni)
    : service_manager_class_() {
  service_manager_class_ = jni.GetClass("android/os/ServiceManager");
  get_service_method_ = service_manager_class_.GetStaticMethodId("getService", "(Ljava/lang/String;)Landroid/os/IBinder;");
  service_manager_class_.MakeGlobal();
}

ServiceManager& ServiceManager::GetInstance(Jni jni) {
  if (instance_ == nullptr) {
    instance_ = new ServiceManager(jni);
  }
  return *instance_;
}

JObject ServiceManager::GetServiceAsInterface(Jni jni, const char* name, const char* type) {
  ServiceManager& manager = GetInstance(jni);
  JString java_name = jni.NewStringUtf(name);
  JObject binder = manager.service_manager_class_.CallStaticObjectMethod(jni, manager.get_service_method_, java_name.ref());
  if (binder.IsNull()) {
    Log::Fatal("Unable to find the \"%s\" service", name);
  }
  string stub_class_name = string(type) + "$Stub";
  JClass stub_class = jni.GetClass(stub_class_name.c_str());
  string method_signature = string("(Landroid/os/IBinder;)L") + type + ";";
  jmethodID as_interface_method = stub_class.GetStaticMethodId("asInterface", method_signature.c_str());
  auto service = stub_class.CallStaticObjectMethod(as_interface_method, binder.ref());
  if (service.IsNull()) {
    auto last_slash = strrchr(type, '/');
    auto type_name = last_slash == nullptr ? type : last_slash + 1;
    Log::Fatal("Unable to get the \"%s\" service object", type_name);
  }
  return service;
}

ServiceManager* ServiceManager::instance_ = nullptr;

}  // namespace screensharing
