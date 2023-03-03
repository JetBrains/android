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

#include "clipboard_manager.h"

#include <atomic>

#include "jvm.h"
#include "log.h"

namespace screensharing {

using namespace std;

namespace {

atomic<ClipboardManager*> clipboard_manager_instance = nullptr;

}  // namespace

ClipboardManager::ClipboardManager(Jni jni)
    : jni_(jni),
      clipboard_adapter_class_(jni.GetClass("com/android/tools/screensharing/ClipboardAdapter")),
      clipboard_listeners_(new vector<ClipboardListener*>()) {
  get_text_method_ = clipboard_adapter_class_.GetStaticMethod("getText", "()Ljava/lang/String;");
  set_text_method_ = clipboard_adapter_class_.GetStaticMethod("setText", "(Ljava/lang/String;)V");
  enable_primary_clip_changed_listener_method_ = clipboard_adapter_class_.GetStaticMethod("enablePrimaryClipChangedListener", "()V");
  disable_primary_clip_changed_listener_method_ = clipboard_adapter_class_.GetStaticMethod("disablePrimaryClipChangedListener", "()V");
  clipboard_adapter_class_.MakeGlobal();
}

ClipboardManager::~ClipboardManager() {
  delete clipboard_listeners_;
}

ClipboardManager* ClipboardManager::GetInstance(Jni jni) {
  if (clipboard_manager_instance == nullptr) {
    clipboard_manager_instance = new ClipboardManager(jni);
  }
  return clipboard_manager_instance;
}

string ClipboardManager::GetText() const {
  Log::V("%s:%d", __FILE__, __LINE__);
  JObject text = clipboard_adapter_class_.CallStaticObjectMethod(jni_, get_text_method_);
  Log::V("%s:%d", __FILE__, __LINE__);
  if (text.IsNull()) {
    JObject exception = jni_.GetAndClearException();
    if (!exception.IsNull()) {
      Log::W("Unable to obtain clipboard text - %s", exception.ToString().c_str());
    }

    Log::V("%s:%d", __FILE__, __LINE__);
    return "";
  }
  Log::V("%s:%d", __FILE__, __LINE__);
  return text.ToString();
}

void ClipboardManager::SetText(const string& text) const {
  JString jtext = JString(jni_, text.c_str());
  clipboard_adapter_class_.CallStaticVoidMethod(jni_, set_text_method_, jtext.ref(), jtext.ref());
}

void ClipboardManager::AddClipboardListener(ClipboardListener* listener) {
  for (;;) {
    auto old_listeners = clipboard_listeners_.load();
    auto new_listeners = new vector<ClipboardListener*>(*old_listeners);
    new_listeners->push_back(listener);
    if (clipboard_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
      if (old_listeners->empty()) {
        clipboard_adapter_class_.CallStaticVoidMethod(jni_, enable_primary_clip_changed_listener_method_);
      }
      delete old_listeners;
      return;
    }
    delete new_listeners;
  }
}

void ClipboardManager::RemoveClipboardListener(ClipboardListener* listener) {
  for (;;) {
    auto old_listeners = clipboard_listeners_.load();
    auto new_listeners = new vector<ClipboardListener*>(*old_listeners);
    auto pos = std::find(new_listeners->begin(), new_listeners->end(), listener);
    if (pos != new_listeners->end()) {
      new_listeners->erase(pos);
      if (clipboard_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
        if (new_listeners->empty()) {
          clipboard_adapter_class_.CallStaticVoidMethod(jni_, disable_primary_clip_changed_listener_method_);
        }
        delete old_listeners;
        return;
      }
    }
    delete new_listeners;
  }
}

void ClipboardManager::OnPrimaryClipChanged() const {
  for (auto listener : *clipboard_listeners_.load()) {
    listener->OnPrimaryClipChanged();
  }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_ClipboardListener_dispatchPrimaryClipChanged(JNIEnv* env, jobject thiz) {
  ClipboardManager* clipboard_manager = clipboard_manager_instance;
  if (clipboard_manager != nullptr) {
    clipboard_manager->OnPrimaryClipChanged();
  }
}

}  // namespace screensharing
