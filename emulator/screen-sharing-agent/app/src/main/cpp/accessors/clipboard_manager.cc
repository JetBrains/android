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

#include "agent.h"
#include "jvm.h"
#include "log.h"
#include "service_manager.h"

namespace screensharing {

using namespace std;

namespace {

constexpr int USER_ID = 0;
constexpr char PACKAGE_NAME[] = "com.android.shell";

ClipboardManager* clipboard_manager_instance = nullptr;

}  // namespace

ClipboardManager::ClipboardManager(Jni jni)
    : jni_(jni),
      clipboard_manager_(ServiceManager::GetServiceAsInterface(jni, "clipboard", "android/content/IClipboard", /*allow_null =*/ true)),
      clipboard_listeners_(new vector<ClipboardListener*>()) {
  if (clipboard_manager_.IsNull()) {
    return;
  }
  package_name_ = JString(jni, PACKAGE_NAME).ToGlobal();
  int api_level = android_get_device_api_level();
  JClass clipboard_manager_class = clipboard_manager_.GetClass();
  const char* signature = api_level >= 29 ?
      "(Ljava/lang/String;I)Landroid/content/ClipData;" : "(Ljava/lang/String;)Landroid/content/ClipData;";
  get_primary_clip_method_ = clipboard_manager_class.GetMethodId("getPrimaryClip", signature);
  signature = api_level >= 29 ?
      "(Landroid/content/ClipData;Ljava/lang/String;I)V" : "(Landroid/content/ClipData;Ljava/lang/String;)V";
  set_primary_clip_method_ = clipboard_manager_class.GetMethodId("setPrimaryClip", signature);
  signature = api_level >= 29 ?
      "(Landroid/content/IOnPrimaryClipChangedListener;Ljava/lang/String;I)V" :
      "(Landroid/content/IOnPrimaryClipChangedListener;Ljava/lang/String;)V";
  add_primary_clip_changed_listener_method_ = clipboard_manager_class.GetMethodId("addPrimaryClipChangedListener", signature);
  clipboard_manager_.MakeGlobal();

  clip_data_class_ = jni.GetClass("android/content/ClipData");
  new_plain_text_method_ = clip_data_class_.GetStaticMethodId(
      "newPlainText", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;");
  get_item_count_method_ = clip_data_class_.GetMethodId("getItemCount", "()I");
  get_item_at_method_ = clip_data_class_.GetMethodId("getItemAt", "(I)Landroid/content/ClipData$Item;");
  clip_data_class_.MakeGlobal();

  JClass clip_data_item_class = jni.GetClass("android/content/ClipData$Item");
  get_text_method_ = clip_data_item_class.GetMethodId("getText", "()Ljava/lang/CharSequence;");
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
  JObject clip_data = android_get_device_api_level() >= 29 ?
      clipboard_manager_.CallObjectMethod(jni_, get_primary_clip_method_, package_name_.ref(), USER_ID) :
      clipboard_manager_.CallObjectMethod(jni_, get_primary_clip_method_, package_name_.ref());
  if (clip_data.IsNull() || clip_data.CallIntMethod(get_item_count_method_) == 0) {
    return "";
  }
  JObject item = clip_data.CallObjectMethod(get_item_at_method_, 0);
  return item.CallObjectMethod(get_text_method_).ToString();
}

void ClipboardManager::SetText(const string& text) const {
  JString jtext = JString(jni_, text.c_str());
  JObject clip_data = clip_data_class_.CallStaticObjectMethod(jni_, new_plain_text_method_, jtext.ref(), jtext.ref());
  if (android_get_device_api_level() >= 29) {
    clipboard_manager_.CallObjectMethod(jni_, set_primary_clip_method_, clip_data.ref(), package_name_.ref(), USER_ID);
  } else {
    clipboard_manager_.CallObjectMethod(jni_, set_primary_clip_method_, clip_data.ref(), package_name_.ref());
  }
}

void ClipboardManager::AddClipboardListener(ClipboardListener* listener) {
  for (;;) {
    auto old_listeners = clipboard_listeners_.load();
    auto new_listeners = new vector<ClipboardListener*>(*old_listeners);
    new_listeners->push_back(listener);
    if (clipboard_listeners_.compare_exchange_strong(old_listeners, new_listeners)) {
      delete old_listeners;
      break;
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
        delete old_listeners;
        break;
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

}  // namespace screensharing

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_ClipboardListener_onPrimaryClipChanged(JNIEnv* jni_env, jobject thiz) {
  if (screensharing::clipboard_manager_instance != nullptr) {
    screensharing::clipboard_manager_instance->OnPrimaryClipChanged();
  }
  // TODO: implement onPrimaryClipChanged()
}