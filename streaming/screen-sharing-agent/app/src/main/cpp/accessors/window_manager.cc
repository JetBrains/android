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

static mutex static_initialization_mutex; // Protects initialization of static fields.

void WindowManager::InitializeStatics(Jni jni) {
  unique_lock lock(static_initialization_mutex);

  if (window_manager_.IsNull()) {
    window_manager_ = ServiceManager::GetServiceAsInterface(jni, "window", "android/view/IWindowManager");
    window_manager_class_ = window_manager_.GetClass();
    if (Agent::feature_level() >= 29) {
      if (Agent::feature_level() >= 35) {
        freeze_display_rotation_method_ = window_manager_class_.GetMethod("freezeDisplayRotation", "(IILjava/lang/String;)V");
        thaw_display_rotation_method_ = window_manager_class_.GetMethod("thawDisplayRotation", "(ILjava/lang/String;)V");
        freeze_display_rotation_method_requires_attribution_tag_ = true;
      } else if (Agent::feature_level() >= 34) {
        freeze_display_rotation_method_ = window_manager_class_.FindMethod("freezeDisplayRotation", "(IILjava/lang/String;)V");
        if (freeze_display_rotation_method_ == nullptr) {
          freeze_display_rotation_method_ = window_manager_class_.GetMethod("freezeDisplayRotation", "(II)V");
          thaw_display_rotation_method_ = window_manager_class_.GetMethod("thawDisplayRotation", "(I)V");
        } else {
          thaw_display_rotation_method_ = window_manager_class_.GetMethod("thawDisplayRotation", "(ILjava/lang/String;)V");
          freeze_display_rotation_method_requires_attribution_tag_ = true;
        }
      } else {
        freeze_display_rotation_method_ = window_manager_class_.GetMethod("freezeDisplayRotation", "(II)V");
        thaw_display_rotation_method_ = window_manager_class_.GetMethod("thawDisplayRotation", "(I)V");
      }
      is_display_rotation_frozen_method_ = window_manager_class_.GetMethod("isDisplayRotationFrozen", "(I)Z");
    } else {
      freeze_display_rotation_method_ = window_manager_class_.FindMethod("freezeRotation", "(I)V");
      thaw_display_rotation_method_ = window_manager_class_.GetMethod("thawRotation", "()V");
      is_display_rotation_frozen_method_ = window_manager_class_.GetMethod("isRotationFrozen", "()Z");
    }
    // The second parameter was added in API 26.
    // See https://android.googlesource.com/platform/frameworks/base/+/35fa3c26adcb5f6577849fd0df5228b1f67cf2c6%5E%21/#F4.
    const char* signature = Agent::feature_level() >= 26 ? "(Landroid/view/IRotationWatcher;I)I" : "(Landroid/view/IRotationWatcher;)I";
    watch_rotation_method_ = window_manager_class_.GetMethod("watchRotation", signature);

    rotation_watcher_class_ = jni.GetClass("com/android/tools/screensharing/RotationWatcher");
    rotation_watcher_constructor_ = rotation_watcher_class_.GetConstructor("(I)V");

    window_manager_.MakeGlobal();
    window_manager_class_.MakeGlobal();
    rotation_watcher_class_.MakeGlobal();
  }
}

void WindowManager::FreezeRotation(Jni jni, int32_t display_id, int32_t rotation) {
  Log::D("WindowManager::FreezeRotation(%d, %d)", display_id, rotation);
  InitializeStatics(jni);
  if (freeze_display_rotation_method_requires_attribution_tag_) {
    window_manager_.CallVoidMethod(jni, freeze_display_rotation_method_, display_id, rotation, JString(jni, ATTRIBUTION_TAG).ref());
  } else if (Agent::feature_level() >= 29) {
    window_manager_.CallVoidMethod(jni, freeze_display_rotation_method_, display_id, rotation);
  } else {
    window_manager_.CallVoidMethod(jni, freeze_display_rotation_method_, rotation);
  }
}

void WindowManager::ThawRotation(Jni jni, int32_t display_id) {
  Log::D("WindowManager::ThawRotation(%d)", display_id);
  InitializeStatics(jni);
  if (freeze_display_rotation_method_requires_attribution_tag_) {
    window_manager_.CallVoidMethod(jni, thaw_display_rotation_method_, display_id, JString(jni, ATTRIBUTION_TAG).ref());
  } else if (Agent::feature_level() >= 29) {
    window_manager_.CallVoidMethod(jni, thaw_display_rotation_method_, display_id);
  } else {
    window_manager_.CallVoidMethod(jni, thaw_display_rotation_method_);
  }
}

bool WindowManager::IsRotationFrozen(Jni jni, int32_t display_id) {
  InitializeStatics(jni);
  if (Agent::feature_level() >= 29) {
    return window_manager_.CallBooleanMethod(jni, is_display_rotation_frozen_method_, display_id);
  } else {
    return window_manager_.CallBooleanMethod(jni, is_display_rotation_frozen_method_);
  }
}

int32_t WindowManager::WatchRotation(Jni jni, int32_t display_id, RotationWatcher* watcher) {
  InitializeStatics(jni);
  unique_lock lock(mutex_);
  auto res = rotation_trackers_.try_emplace(display_id);
  auto& tracker = res.first->second;
  if (res.second) {
    tracker.watcher_adapter = rotation_watcher_class_.NewObject(jni, rotation_watcher_constructor_, display_id);
    tracker.rotation = Agent::feature_level() >= 26 ?
                window_manager_.CallIntMethod(jni, watch_rotation_method_, tracker.watcher_adapter.ref(), display_id) :
                window_manager_.CallIntMethod(jni, watch_rotation_method_, tracker.watcher_adapter.ref());
    tracker.watcher_adapter.MakeGlobal();
  }
  tracker.rotation_watchers.Add(watcher);
  return tracker.rotation;
}

void WindowManager::RemoveRotationWatcher(Jni jni, int32_t display_id, RotationWatcher* watcher) {
  {
    unique_lock lock(static_initialization_mutex);
    if (window_manager_.IsNull()) {
      return;
    }
  }

  unique_lock lock(mutex_);
  auto pos = rotation_trackers_.find(display_id);
  if (pos == rotation_trackers_.end()) {
    return;
  }
  DisplayRotationTracker& tracker = pos->second;
  if (tracker.rotation_watchers.Remove(watcher) == 0) {
    jmethodID remove_rotation_watcher_method =
        window_manager_class_.GetMethod(jni, "removeRotationWatcher", "(Landroid/view/IRotationWatcher;)V");
    window_manager_.CallVoidMethod(jni, remove_rotation_watcher_method, tracker.watcher_adapter.ref());
    rotation_trackers_.erase(pos);
  }
}

void WindowManager::OnRotationChanged(int32_t display_id, int32_t rotation) {
  Log::D("WindowManager::OnRotationChanged(%d, %d)", display_id, rotation);
  auto tracker = rotation_trackers_.find(display_id);
  if (tracker != rotation_trackers_.end()) {
    int i = 0;
    tracker->second.rotation_watchers.ForEach([rotation, &i](auto watcher) {
      Log::D("WindowManager::OnRotationChanged: calling watcher %d", i++);
      watcher->OnRotationChanged(rotation);
    });
  }
}

WindowManager::DisplayRotationTracker::DisplayRotationTracker() = default;

JObject WindowManager::window_manager_;
JClass WindowManager::window_manager_class_;
jmethodID WindowManager::freeze_display_rotation_method_;
bool WindowManager::freeze_display_rotation_method_requires_attribution_tag_ = false;
jmethodID WindowManager::thaw_display_rotation_method_;
jmethodID WindowManager::is_display_rotation_frozen_method_;
JClass WindowManager::rotation_watcher_class_;
jmethodID WindowManager::rotation_watcher_constructor_;
jmethodID WindowManager::watch_rotation_method_;
mutex WindowManager::mutex_;
map<int32_t, WindowManager::DisplayRotationTracker> WindowManager::rotation_trackers_;

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_RotationWatcher_onRotationChanged(JNIEnv* jni_env, jobject thiz, jint display_id, jint rotation) {
  Log::D("RotationWatcher.onRotationChanged(%d, %d)", display_id, rotation);
  WindowManager::OnRotationChanged(display_id, rotation);
}

}  // namespace screensharing
