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

#pragma once

#include <future>
#include <mutex>
#include <thread>

#include "jvm.h"

namespace screensharing {

// Provides access to android.os.Looper.
class Looper : public JObject {
public:
  using JObject::JObject;
  explicit Looper(JObject&& looper);

  void Quit() const;

  static void SetMainLooper(Jni jni, const Looper& looper);
  static Looper GetMainLooper(Jni jni);
  static Looper Create(Jni jni);
  static void Loop();

private:
  static void InitializeStatics(Jni jni);

  static JClass looper_class_;
  static jmethodID prepare_method_;
  static jmethodID my_looper_method_;
  static jmethodID get_main_looper_method_;
  static jmethodID loop_method_;
  static jmethodID quit_method_;
  static jfieldID main_looper_field_;
};

class Handler : public JObject {
public:
  using JObject::JObject;
  explicit Handler(Jni jni, const Looper& looper);

private:
  static JObject Create(Jni jni, const Looper& looper);
  static void InitializeStatics(Jni jni);

  static JClass handler_class_;
  static jmethodID constructor_;
};

}  // namespace screensharing
