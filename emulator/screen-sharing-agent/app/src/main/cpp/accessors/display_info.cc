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

#include "display_info.h"

#include "string_printf.h"

namespace screensharing {

using namespace std;

DisplayInfo::DisplayInfo()
    : logical_size { 0, 0 },
      rotation(),
      layer_stack(),
      flags() {
}

DisplayInfo::DisplayInfo(int32_t logical_width, int32_t logical_height, int32_t rotation, int32_t layer_stack, int32_t flags)
    : logical_size { logical_width, logical_height },
      rotation(rotation),
      layer_stack(layer_stack),
      flags(flags) {
}

string DisplayInfo::ToDebugString() const {
  return StringPrintf("logical_size:%dx%d display_rotation:%d layer_stack:%d flags:0x%x",
                      logical_size.width, logical_size.height, rotation, layer_stack, flags);
}

}  // namespace screensharing