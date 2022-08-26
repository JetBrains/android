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
    : clipboard_manager_(ServiceManager::GetServiceAsInterface(jni, "clipboard", "android/content/IClipboard", /*allow_null =*/ true)),
      set_extras_method_(),
      clipboard_listeners_(new vector<ClipboardListener*>()) {
  if (clipboard_manager_.IsNull()) {
    return;
  }
  package_name_ = JString(jni, PACKAGE_NAME).ToGlobal();

  clip_data_class_ = jni.GetClass("android/content/ClipData");
  new_plain_text_method_ = clip_data_class_.GetStaticMethodId(
      "newPlainText", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;");
  get_item_count_method_ = clip_data_class_.GetMethodId("getItemCount", "()I");
  get_item_at_method_ = clip_data_class_.GetMethodId("getItemAt", "(I)Landroid/content/ClipData$Item;");
  get_description_method_ = clip_data_class_.GetMethodId("getDescription", "()Landroid/content/ClipDescription;");
  clip_data_class_.MakeGlobal();

  JClass clip_data_item_class = jni.GetClass("android/content/ClipData$Item");
  get_text_method_ = clip_data_item_class.GetMethodId("getText", "()Ljava/lang/CharSequence;");

  int api_level = android_get_device_api_level();
  if (api_level >= 33) {
    // Initialize overlay_suppressor_ that is used to suppress clipboard change UI overlay on Android 13+.
    JClass clip_description_class = jni.GetClass("android/content/ClipDescription");
    set_extras_method_ = clip_description_class.GetMethodId("setExtras", "(Landroid/os/PersistableBundle;)V");

    JClass persistable_bundle_class = jni.GetClass("android/os/PersistableBundle");
    jmethodID persistable_bundle_constructor = persistable_bundle_class.GetConstructorId("(I)V");
    jmethodID put_boolean_method = persistable_bundle_class.GetMethodId("putBoolean", "(Ljava/lang/String;Z)V");
    overlay_suppressor_ = persistable_bundle_class.NewObject(persistable_bundle_constructor, 1);
    overlay_suppressor_.CallVoidMethod(put_boolean_method, JString(jni, "com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY").ref(), true);
    overlay_suppressor_.MakeGlobal();
  }

  JClass clipboard_listener_class = jni.GetClass("com/android/tools/screensharing/ClipboardListener");
  jmethodID constructor = clipboard_listener_class.GetConstructorId("()V");
  clipboard_listener_ = clipboard_listener_class.NewObject(constructor);
  clipboard_listener_.MakeGlobal();

  JClass clipboard_manager_class = clipboard_manager_.GetClass();
  number_of_extra_parameters_ = api_level >= 33 ? 2 : api_level >= 29 ? 1 : 0;
  if (api_level == 33) {
    // The IClipboard.getPrimaryClip method may have either 2 or 3 parameters in API 33.
    get_primary_clip_method_ =
        clipboard_manager_class.FindMethod("getPrimaryClip", "(Ljava/lang/String;Ljava/lang/String;I)Landroid/content/ClipData;");
    if (get_primary_clip_method_ == nullptr) {
      number_of_extra_parameters_ = 1;
    }
  }
  if (get_primary_clip_method_ == nullptr) {
    const char* signatures1[] = {
        "(Ljava/lang/String;)Landroid/content/ClipData;",
        "(Ljava/lang/String;I)Landroid/content/ClipData;",
        "(Ljava/lang/String;Ljava/lang/String;I)Landroid/content/ClipData;"
    };
    get_primary_clip_method_ = clipboard_manager_class.GetMethodId("getPrimaryClip", signatures1[number_of_extra_parameters_]);
  }
  const char* signatures2[] = {
      "(Landroid/content/ClipData;Ljava/lang/String;)V",
      "(Landroid/content/ClipData;Ljava/lang/String;I)V",
      "(Landroid/content/ClipData;Ljava/lang/String;Ljava/lang/String;I)V"
  };
  set_primary_clip_method_ = clipboard_manager_class.GetMethodId("setPrimaryClip", signatures2[number_of_extra_parameters_]);
  const char* signatures3[] = {
      "(Landroid/content/IOnPrimaryClipChangedListener;Ljava/lang/String;)V",
      "(Landroid/content/IOnPrimaryClipChangedListener;Ljava/lang/String;I)V",
      "(Landroid/content/IOnPrimaryClipChangedListener;Ljava/lang/String;Ljava/lang/String;I)V"
  };
  jmethodID add_listener_method =
      clipboard_manager_class.GetMethodId("addPrimaryClipChangedListener", signatures3[number_of_extra_parameters_]);
  if (number_of_extra_parameters_ == 0) {
    clipboard_manager_.CallVoidMethod(add_listener_method, clipboard_listener_.ref(), package_name_.ref());
  } else if (number_of_extra_parameters_ == 1) {
    clipboard_manager_.CallVoidMethod(add_listener_method, clipboard_listener_.ref(), package_name_.ref(), USER_ID);
  } else {
    clipboard_manager_.CallVoidMethod(
        add_listener_method, clipboard_listener_.ref(), package_name_.ref(), JString(jni, "ScreenSharing").ref(), USER_ID);
  }

  clipboard_manager_.MakeGlobal();
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

string ClipboardManager::GetText(Jni jni) const {
  JObject clip_data =
      number_of_extra_parameters_ == 0 ?
          clipboard_manager_.CallObjectMethod(jni, get_primary_clip_method_, package_name_.ref()) :
      number_of_extra_parameters_ == 1 ?
          clipboard_manager_.CallObjectMethod(jni, get_primary_clip_method_, package_name_.ref(), USER_ID) :
          clipboard_manager_.CallObjectMethod(
              jni, get_primary_clip_method_, package_name_.ref(), JString(jni, "ScreenSharing").ref(), USER_ID);
  if (clip_data.IsNull() || clip_data.CallIntMethod(get_item_count_method_) == 0) {
    return "";
  }
  JObject item = clip_data.CallObjectMethod(get_item_at_method_, 0);
  JObject text = item.CallObjectMethod(get_text_method_);
  if (text.IsNull()) {
    return "";
  }
  return text.ToString();
}

void ClipboardManager::SetText(Jni jni, const string& text) const {
  JString jtext = JString(jni, text.c_str());
  JObject clip_data = clip_data_class_.CallStaticObjectMethod(jni, new_plain_text_method_, jtext.ref(), jtext.ref());
  auto api_level = android_get_device_api_level();
  if (api_level >= 33) {
    // Suppress clipboard change UI overlay on Android 13+.
    JObject clip_description = clip_data.CallObjectMethod(jni, get_description_method_);
    clip_description.CallVoidMethod(set_extras_method_, overlay_suppressor_.ref());
  }
  if (number_of_extra_parameters_ == 0) {
    clipboard_manager_.CallObjectMethod(jni, set_primary_clip_method_, clip_data.ref(), package_name_.ref());
  } else if (number_of_extra_parameters_ == 1) {
    clipboard_manager_.CallObjectMethod(jni, set_primary_clip_method_, clip_data.ref(), package_name_.ref(), USER_ID);
  } else {
    clipboard_manager_.CallObjectMethod(
        jni, set_primary_clip_method_, clip_data.ref(), package_name_.ref(), JString(jni, "ScreenSharing").ref(), USER_ID);
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

extern "C"
JNIEXPORT void JNICALL
Java_com_android_tools_screensharing_ClipboardListener_dispatchPrimaryClipChanged(JNIEnv* env, jobject thiz) {
  Log::D("ClipboardListener.dispatchPrimaryClipChanged");
  if (clipboard_manager_instance != nullptr) {
    clipboard_manager_instance->OnPrimaryClipChanged();
  }
}

}  // namespace screensharing
