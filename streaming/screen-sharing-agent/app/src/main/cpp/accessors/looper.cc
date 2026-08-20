/*
 * Copyright (C) 2026 The Android Open Source Project
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

#include "accessors/looper.h"

#include <mutex>

#include "jvm.h"
#include "log.h"
#include "thread_handle.h"

namespace screensharing {

using namespace std;

static mutex static_initialization_mutex;  // Protects initialization of static fields.

Looper::Looper(JObject&& looper)
    : JObject(std::move(looper)) {
}

void Looper::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);
  if (looper_class_.IsNull()) {
    looper_class_ = jni.GetClass("android/os/Looper");
    prepare_method_ = looper_class_.GetStaticMethod(jni, "prepare", "()V");
    my_looper_method_ = looper_class_.GetStaticMethod(jni, "myLooper", "()Landroid/os/Looper;");
    loop_method_ = looper_class_.GetStaticMethod(jni, "loop", "()V");
    quit_method_ = looper_class_.GetMethod(jni, "quit", "()V");
    main_looper_field_ = looper_class_.GetStaticFieldId(jni, "sMainLooper", "Landroid/os/Looper;");
    looper_class_.MakeGlobal();
  }
}

void Looper::SetMainLooper(Jni jni, const Looper& looper) {
  InitializeStatics(jni);
  jni->SetStaticObjectField(looper_class_.ref(), main_looper_field_, looper.ref());
}

Looper Looper::GetMainLooper(Jni jni) {
  InitializeStatics(jni);
  return Looper(JObject(jni, jni->GetStaticObjectField(looper_class_.ref(), main_looper_field_)));
}

Looper Looper::Create(Jni jni) {
  InitializeStatics(jni);
  looper_class_.CallStaticVoidMethod(jni, prepare_method_);

  Looper looper(looper_class_.CallStaticObjectMethod(jni, my_looper_method_));
  looper.MakeGlobal();
  return looper;
}

void Looper::Loop() {
  Jni jni = Jvm::GetJni();
  InitializeStatics(jni);
  looper_class_.CallStaticVoidMethod(jni, loop_method_);
}

void Looper::Quit() const {
  CallVoidMethod(Jvm::GetJni(), quit_method_);
}

JClass Looper::looper_class_;
jmethodID Looper::prepare_method_ = nullptr;
jmethodID Looper::my_looper_method_ = nullptr;
jmethodID Looper::loop_method_ = nullptr;
jmethodID Looper::quit_method_ = nullptr;
jfieldID Looper::main_looper_field_ = nullptr;

Handler::Handler(Jni jni, const Looper& looper)
    : JObject(Create(jni, looper)) {
}

void Handler::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);
  if (handler_class_.IsNull()) {
    handler_class_ = jni.GetClass("android/os/Handler");
    constructor_ = handler_class_.GetConstructor(jni, "(Landroid/os/Looper;)V");
    handler_class_.MakeGlobal();
  }
}

JObject Handler::Create(Jni jni, const Looper& looper) {
  InitializeStatics(jni);
  return handler_class_.NewObject(jni, constructor_, looper.ref());
}

JClass Handler::handler_class_;
jmethodID Handler::constructor_ = nullptr;

}  // namespace screensharing
