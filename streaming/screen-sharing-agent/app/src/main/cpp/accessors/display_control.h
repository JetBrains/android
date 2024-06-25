/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <vector>

#include "common.h"
#include "jvm.h"

namespace screensharing {

// Provides access to few methods of the com.android.server.display.DisplayControl class.
// Can only be used by the thread that created the object.
class DisplayControl {
public:
  static std::vector<int64_t> GetPhysicalDisplayIds(Jni jni);
  static JObject GetPhysicalDisplayToken(Jni jni, int64_t physical_display_id);

private:
  static void InitializeStatics(Jni jni);

  static JClass class_;
  static jmethodID get_physical_display_ids_method_;
  static jmethodID get_physical_display_token_method_;

  DISALLOW_COPY_AND_ASSIGN(DisplayControl);
};

}  // namespace screensharing
