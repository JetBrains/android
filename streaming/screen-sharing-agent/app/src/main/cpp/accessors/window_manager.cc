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

#include "window_manager.h"

#include "accessors/display_manager.h"
#include "accessors/service_manager.h"
#include "agent.h"
#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

namespace {

WindowManager* window_manager_instance = nullptr;

}  // namespace

WindowManager::WindowManager(Jni jni)
    : window_manager_(ServiceManager::GetServiceAsInterface(jni, "window", "android/view/IWindowManager")),
      rotation_(),
      rotation_watchers_(new set<RotationWatcher*>()) {
  Log::V("%s:%d", __FILE__, __LINE__);
  JClass window_manager_class(window_manager_.GetClass());
  // The getDefaultDisplayRotation method was called getRotation before API 26.
  // See https://android.googlesource.com/platform/frameworks/base/+/5406e7ade87c33f70c83a283781dcc48fb67cdb9%5E%21/#F2.
  const char* method_name = Agent::api_level() >= 26 ? "getDefaultDisplayRotation" : "getRotation";
  get_default_display_rotation_method_ = window_manager_class.GetMethod(method_name, "()I");
  freeze_rotation_method_ = window_manager_class.GetMethod("freezeRotation", "(I)V");
  thaw_rotation_method_ = window_manager_class.GetMethod("thawRotation", "()V");
  is_rotation_frozen_method_ = window_manager_class.GetMethod("isRotationFrozen", "()Z");
  // The second parameter was added in API 26.
  // See https://android.googlesource.com/platform/frameworks/base/+/35fa3c26adcb5f6577849fd0df5228b1f67cf2c6%5E%21/#F4.
  const char* signature = Agent::api_level() >= 26 ? "(Landroid/view/IRotationWatcher;I)I" : "(Landroid/view/IRotationWatcher;)I";
  jmethodID watch_rotation_method_ = window_manager_class.GetMethod("watchRotation", signature);
  JClass rotation_watcher_class = jni.GetClass("com/android/tools/screensharing/RotationWatcher");
  jmethodID rotation_watcher_constructor = rotation_watcher_class.GetConstructor("()V");
  watcher_object_ = rotation_watcher_class.NewObject(rotation_watcher_constructor);
  rotation_ = Agent::api_level() >= 26 ?
      window_manager_.CallIntMethod(watch_rotation_method_, watcher_object_.ref(), DEFAULT_DISPLAY) :
      window_manager_.CallIntMethod(watch_rotation_method_, watcher_object_.ref());
  window_manager_.MakeGlobal();
  watcher_object_.MakeGlobal();
}

WindowManager::~WindowManager() {
  Jni jni = Jvm::GetJni();
  Log::V("%s:%d", __FILE__, __LINE__);
  JClass window_manager_class(window_manager_.GetClass(jni));
  jmethodID remove_rotation_watcher_method =
      window_manager_class.GetMethod("removeRotationWatcher", "(Landroid/view/IRotationWatcher;)V");
  window_manager_.CallVoidMethod(jni, remove_rotation_watcher_method);
}

WindowManager& WindowManager::GetInstance(Jni jni) {
  if (window_manager_instance == nullptr) {
    window_manager_instance = new WindowManager(jni);
  }
  return *window_manager_instance;
}

int WindowManager::GetDefaultDisplayRotation(Jni jni) {
  WindowManager& instance = GetInstance(jni);
  return instance.window_manager_.CallIntMethod(jni, instance.get_default_display_rotation_method_);
}

void WindowManager::FreezeRotation(Jni jni, int32_t rotation) {
  WindowManager& instance = GetInstance(jni);
  Log::D("WindowManager::FreezeRotation: setting display orientation to %d", rotation);
  instance.window_manager_.CallVoidMethod(jni, instance.freeze_rotation_method_, rotation);
}

void WindowManager::ThawRotation(Jni jni) {
  WindowManager& instance = GetInstance(jni);
  instance.window_manager_.CallVoidMethod(jni, instance.thaw_rotation_method_);
}

bool WindowManager::IsRotationFrozen(Jni jni) {
  WindowManager& instance = GetInstance(jni);
  return instance.window_manager_.CallBooleanMethod(jni, instance.is_rotation_frozen_method_);
}

int32_t WindowManager::WatchRotation(Jni jni, RotationWatcher* watcher) {
  WindowManager& instance = GetInstance(jni);
  for (;;) {
    auto old_watchers = instance.rotation_watchers_.load();
    auto new_watchers = new set<RotationWatcher*>(*old_watchers);
    if (new_watchers->insert(watcher).second && instance.rotation_watchers_.compare_exchange_strong(old_watchers, new_watchers)) {
      delete old_watchers;
      break;
    }
    delete new_watchers;
  }
  return instance.rotation_;
}

void WindowManager::RemoveRotationWatcher(Jni jni, RotationWatcher* watcher) {
  WindowManager& instance = GetInstance(jni);
  for (;;) {
    auto old_watchers = instance.rotation_watchers_.load();
    auto new_watchers = new set<RotationWatcher*>(*old_watchers);
    if (new_watchers->erase(watcher) != 0 && instance.rotation_watchers_.compare_exchange_strong(old_watchers, new_watchers)) {
      delete old_watchers;
      break;
    }
    delete new_watchers;
  }
}

void WindowManager::OnRotationChanged(int32_t rotation) {
  rotation_ = rotation;
  for (auto watcher : *rotation_watchers_.load()) {
    watcher->OnRotationChanged(rotation);
  }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_RotationWatcher_onRotationChanged(JNIEnv* jni_env, jobject thiz, jint rotation) {
  Log::D("RotationWatcher.onRotationChanged");
  if (window_manager_instance != nullptr) {
    window_manager_instance->OnRotationChanged(rotation);
  }
}

}  // namespace screensharing
