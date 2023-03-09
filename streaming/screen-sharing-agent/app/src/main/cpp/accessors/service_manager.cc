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
  // The waitForService method was introduced only in API 30. Fall back to getService on earlier versions.
  const char* method_name = android_get_device_api_level() >= 30 ? "waitForService" : "getService";
  wait_for_service_method_ = service_manager_class_.GetStaticMethod(method_name, "(Ljava/lang/String;)Landroid/os/IBinder;");
  service_manager_class_.MakeGlobal();
}

ServiceManager& ServiceManager::GetInstance(Jni jni) {
  static ServiceManager instance(jni);
  return instance;
}

JObject ServiceManager::GetServiceAsInterface(Jni jni, const char* name, const char* type, bool allow_null) {
  JObject binder = GetService(jni, name, allow_null);
  if (binder.IsNull()) {
    return binder;
  }
  string stub_class_name = string(type) + "$Stub";
  JClass stub_class = jni.GetClass(stub_class_name.c_str());
  string method_signature = string("(Landroid/os/IBinder;)L") + type + ";";
  jmethodID as_interface_method = stub_class.GetStaticMethod("asInterface", method_signature.c_str());
  auto service = stub_class.CallStaticObjectMethod(as_interface_method, binder.ref());
  if (service.IsNull() && !allow_null) {
    auto last_slash = strrchr(type, '/');
    auto type_name = last_slash == nullptr ? type : last_slash + 1;
    Log::Fatal("Unable to get the \"%s\" service object", type_name);
  }
  return service;
}

JObject ServiceManager::WaitForService(Jni jni, const char* name, bool allow_null) {
  Log::D("WaitForService(\"%s\")", name);
  JObject binder = service_manager_class_.CallStaticObjectMethod(jni, wait_for_service_method_, JString(jni, name).ref());
  if (binder.IsNull() && !allow_null) {
    Log::Fatal("Unable to find the \"%s\" service", name);
  }
  return binder;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_android_tools_screensharing_ServiceManager_getServiceAsInterface(
    JNIEnv* jni_env, jclass clazz, jstring name, jstring type, jboolean allow_null) {
  Jni jni(jni_env);
  return ServiceManager::GetServiceAsInterface(
      jni, JString(jni, name).GetValue().c_str(), JString(jni, type).GetValue().c_str(), allow_null).Release();
}

}  // namespace screensharing
