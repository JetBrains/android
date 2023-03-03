/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "display_listener_dispatcher.h"

#include "accessors/display_manager.h"
#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

using namespace std;

namespace {

// Constants copied from the android.hardware.display.DisplayManager class.
constexpr int64_t EVENT_FLAG_DISPLAY_ADDED = 1L << 0;
constexpr int64_t EVENT_FLAG_DISPLAY_REMOVED = 1L << 1;
constexpr int64_t  EVENT_FLAG_DISPLAY_CHANGED = 1L << 2;

}

DisplayListenerDispatcher::~DisplayListenerDispatcher() {
  Stop();
}

void DisplayListenerDispatcher::Start() {
  unique_lock lock(mutex_);
  looper_promise_ = promise<JObject>();
  thread_ = thread([this]() {
    jni_ = Jvm::AttachCurrentThread("DisplayListenerDispatcher");
    Run();
    Jvm::DetachCurrentThread();
  });
}

void DisplayListenerDispatcher::Run() {
  JClass looper_class = jni_.GetClass("android/os/Looper");
  looper_class.CallStaticVoidMethod(looper_class.GetStaticMethodId("prepare", "()V"));

  // Store the looper so that it can be stopped later.
  JObject looper = looper_class.CallStaticObjectMethod(looper_class.GetStaticMethodId("myLooper", "()Landroid/os/Looper;"));
  looper.MakeGlobal();
  looper_promise_.set_value(std::move(looper));

  auto api_level = android_get_device_api_level();
  const char* signature = api_level >= 31 ?
      "(Landroid/hardware/display/DisplayManager$DisplayListener;Landroid/os/Handler;J)V" :
      "(Landroid/hardware/display/DisplayManager$DisplayListener;Landroid/os/Handler;)V";
  jmethodID register_display_listener_method =
      DisplayManager::display_manager_global_class_.GetMethodId(jni_, "registerDisplayListener", signature);
  JClass display_listener_class = jni_.GetClass("com/android/tools/screensharing/DisplayListener");
  JObject listener = display_listener_class.NewObject(display_listener_class.GetConstructorId("()V"));
  if (api_level >= 31) {
    DisplayManager::display_manager_global_.CallVoidMethod(
        jni_, register_display_listener_method, listener.ref(), nullptr,
        EVENT_FLAG_DISPLAY_ADDED | EVENT_FLAG_DISPLAY_REMOVED | EVENT_FLAG_DISPLAY_CHANGED);
  } else {
    DisplayManager::display_manager_global_.CallVoidMethod(jni_, register_display_listener_method, listener.ref(), nullptr);
  }
  looper_class.CallStaticVoidMethod(looper_class.GetStaticMethodId("loop", "()V"));
}

void DisplayListenerDispatcher::Stop() {
  scoped_lock lock(mutex_);
  if (thread_.joinable()) {
    Jni jni = Jvm::GetJni();
    JObject looper = looper_promise_.get_future().get();
    Log::V("%s:%d", __FILE__, __LINE__);
    looper.CallVoidMethod(jni, looper.GetClass(jni).GetMethodId("quit", "()V"));
    thread_.join();
  }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_DisplayListener_onDisplayAdded(JNIEnv* jni_env, jobject thiz, jint display_id) {
  Log::D("DisplayListenerDispatcher.onDisplayAdded %d", display_id);
  DisplayManager::OnDisplayAdded(jni_env, display_id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_DisplayListener_onDisplayRemoved(JNIEnv* jni_env, jobject thiz, jint display_id) {
  Log::D("DisplayListenerDispatcher.onDisplayRemoved %d", display_id);
  DisplayManager::OnDisplayRemoved(jni_env, display_id);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_DisplayListener_onDisplayChanged(JNIEnv* jni_env, jobject thiz, jint display_id) {
  Log::D("DisplayListenerDispatcher.onDisplayChanged %d", display_id);
  DisplayManager::OnDisplayChanged(jni_env, display_id);
}

}  // namespace screensharing
