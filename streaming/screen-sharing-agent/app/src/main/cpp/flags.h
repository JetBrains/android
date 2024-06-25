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

namespace screensharing {

constexpr int32_t START_VIDEO_STREAM = 0x01;
constexpr int32_t TURN_OFF_DISPLAY_WHILE_MIRRORING = 0x02;
constexpr int32_t STREAM_AUDIO = 0x04;
constexpr int32_t USE_UINPUT = 0x08;
constexpr int32_t AUTO_RESET_UI_SETTINGS = 0x10;
constexpr int32_t DEBUG_LAYOUT_UI_SETTINGS = 0x20;
constexpr int32_t GESTURE_NAVIGATION_UI_SETTINGS = 0x40;

}  // namespace screensharing
