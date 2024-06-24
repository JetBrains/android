/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "display_control.h"

#include <mutex>

#include "agent.h"
#include "log.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex; // Protects initialization of static fields.

void DisplayControl::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);

  if (class_.IsNull()) {
    if (Agent::feature_level() >= 34) {
      JClass class_loader_class = jni.GetClass("java/lang/ClassLoader");
      jmethodID get_system_class_loader_method = class_loader_class.GetStaticMethod("getSystemClassLoader", "()Ljava/lang/ClassLoader;");
      JObject system_class_loader = class_loader_class.CallStaticObjectMethod(get_system_class_loader_method);
      jmethodID load_class_method = class_loader_class.GetMethod("loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
      JClass class_loader_factory_class = jni.GetClass("com/android/internal/os/ClassLoaderFactory");
      jmethodID create_class_loader_method = class_loader_factory_class.GetStaticMethod(
          "createClassLoader",
          "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;IZLjava/lang/String;)Ljava/lang/ClassLoader;");
      JObject class_loader = class_loader_factory_class.CallStaticObjectMethod(
          create_class_loader_method, JString(jni, "/system/framework/services.jar").ref(), nullptr, nullptr, system_class_loader.ref(),
          0, JNI_TRUE, nullptr);
      JClass display_control_class =
          JClass(class_loader.CallObjectMethod(load_class_method, JString(jni, "com.android.server.display.DisplayControl").ref()));

      JClass runtime_class = jni.GetClass("java/lang/Runtime");
      jmethodID get_runtime_method = runtime_class.GetStaticMethod("getRuntime", "()Ljava/lang/Runtime;");
      JObject runtime = runtime_class.CallStaticObjectMethod(get_runtime_method);
      jmethodID load_library0_method = runtime_class.GetMethod("loadLibrary0", "(Ljava/lang/Class;Ljava/lang/String;)V");
      runtime.CallVoidMethod(load_library0_method, display_control_class.ref(), JString(jni, "android_servers").ref());
      class_ = std::move(display_control_class);
    } else {
      // Before API 34 getPhysicalDisplayIds and getPhysicalDisplayToken used to be part of SurfaceControl.
      class_ = jni.GetClass("android/view/SurfaceControl");
    }
    get_physical_display_ids_method_ = class_.GetStaticMethod("getPhysicalDisplayIds", "()[J");
    get_physical_display_token_method_ = class_.GetStaticMethod("getPhysicalDisplayToken", "(J)Landroid/os/IBinder;");
    class_.MakeGlobal();
  }
}

vector<int64_t> DisplayControl::GetPhysicalDisplayIds(Jni jni) {
  InitializeStatics(jni);
  JObject obj = class_.CallStaticObjectMethod(jni, get_physical_display_ids_method_);
  return jni.GetElements(static_cast<jlongArray>(obj.ref()));
  return vector<int64_t>();
}

JObject DisplayControl::GetPhysicalDisplayToken(Jni jni, int64_t physical_display_id) {
  InitializeStatics(jni);
  return class_.CallStaticObjectMethod(jni, get_physical_display_token_method_, physical_display_id);
}

JClass DisplayControl::class_;
jmethodID DisplayControl::get_physical_display_ids_method_ = nullptr;
jmethodID DisplayControl::get_physical_display_token_method_ = nullptr;

} // namespace screensharing