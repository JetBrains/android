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
      clipboard_adapter_class_(jni.GetClass("com/android/tools/screensharing/ClipboardAdapter")) {
  get_text_method_ = clipboard_adapter_class_.GetStaticMethod("getText", "()Ljava/lang/String;");
  set_text_method_ = clipboard_adapter_class_.GetStaticMethod("setText", "(Ljava/lang/String;)V");
  enable_primary_clip_changed_listener_method_ = clipboard_adapter_class_.GetStaticMethod("enablePrimaryClipChangedListener", "()V");
  disable_primary_clip_changed_listener_method_ = clipboard_adapter_class_.GetStaticMethod("disablePrimaryClipChangedListener", "()V");
  clipboard_adapter_class_.MakeGlobal();
}

ClipboardManager::~ClipboardManager() = default;

ClipboardManager* ClipboardManager::GetInstance(Jni jni) {
  if (clipboard_manager_instance == nullptr) {
    clipboard_manager_instance = new ClipboardManager(jni);
  }
  return clipboard_manager_instance;
}

string ClipboardManager::GetText() const {
  Log::D("ClipboardManager::GetText");
  JObject text = clipboard_adapter_class_.CallStaticObjectMethod(jni_, get_text_method_);
  if (text.IsNull()) {
    Log::W(jni_.GetAndClearException(), "Unable to obtain clipboard text");
    return "";
  }
  return text.GetStringValue();
}

void ClipboardManager::SetText(const string& text) const {
  Log::D("ClipboardManager::SetText");
  JString jtext = JString(jni_, text.c_str());
  clipboard_adapter_class_.CallStaticVoidMethod(jni_, set_text_method_, jtext.ref(), jtext.ref());
  JThrowable exception = jni_.GetAndClearException();
  if (exception.IsNotNull()) {
    Log::W(std::move(exception), "Unable to set clipboard text");
  }
}

void ClipboardManager::AddClipboardListener(ClipboardListener* listener) {
  Log::D("ClipboardManager::AddClipboardListener");
  if (clipboard_listeners_.Add(listener) == 1) {
    clipboard_adapter_class_.CallStaticVoidMethod(jni_, enable_primary_clip_changed_listener_method_);
  }
}

void ClipboardManager::RemoveClipboardListener(ClipboardListener* listener) {
  Log::D("ClipboardManager::RemoveClipboardListener");
  if (clipboard_listeners_.Remove(listener) == 0) {
    clipboard_adapter_class_.CallStaticVoidMethod(jni_, disable_primary_clip_changed_listener_method_);
  }
}

void ClipboardManager::OnPrimaryClipChanged() {
  Log::D("ClipboardManager::OnPrimaryClipChanged");
  clipboard_listeners_.ForEach([](auto listener) {
    listener->OnPrimaryClipChanged();
  });
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
