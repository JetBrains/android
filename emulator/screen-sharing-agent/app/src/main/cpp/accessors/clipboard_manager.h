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

#pragma once

#include <android/input.h>

#include <memory>
#include <vector>

#include "common.h"
#include "jvm.h"

namespace screensharing {

// Provides access to the Android clipboard.
class ClipboardManager {
public:
  struct ClipboardListener {
    virtual void OnPrimaryClipChanged() = 0;
  };

  static ClipboardManager* GetInstance(Jni jni);

  ~ClipboardManager();

  std::string GetText() const;
  void SetText(const std::string& text) const;
  void AddClipboardListener(ClipboardListener* listener);
  void RemoveClipboardListener(ClipboardListener* listener);

  void OnPrimaryClipChanged() const;

private:
  ClipboardManager(Jni jni);

  Jni jni_;
  // com.android.tools.screensharing.ClipboardAdapter class.
  JClass clipboard_adapter_class_;
  jmethodID get_text_method_;
  jmethodID set_text_method_;
  jmethodID enable_primary_clip_changed_listener_method_;
  jmethodID disable_primary_clip_changed_listener_method_;
  // Copy-on-write set of clipboard listeners.
  std::atomic<std::vector<ClipboardListener*>*> clipboard_listeners_;

  DISALLOW_COPY_AND_ASSIGN(ClipboardManager);
};

}  // namespace screensharing
