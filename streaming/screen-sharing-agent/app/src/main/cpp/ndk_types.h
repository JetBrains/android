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

#include <android/native_window.h>
#include <media/NdkMediaCodec.h>

#include "ndk_ptr.h"

namespace screensharing {

// Smart pointers for NDK types.

template<>
inline void NdkDelete<ANativeWindow>(ANativeWindow* ptr) { ANativeWindow_release(ptr); }

typedef NdkPtr<ANativeWindow> NativeWindow;

template<>
inline void NdkDelete<AMediaFormat>(AMediaFormat* ptr) { AMediaFormat_delete(ptr); }

typedef NdkPtr<AMediaFormat> MediaFormat;

template<>
inline void NdkDelete<AMediaCodec>(AMediaCodec* ptr) { AMediaCodec_delete(ptr); }

typedef NdkPtr<AMediaCodec> MediaCodec;

}  // namespace screensharing
