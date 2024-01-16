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
package com.android.tools.idea.streaming.device

import org.bytedeco.ffmpeg.global.avutil.AV_ERROR_MAX_STRING_SIZE
import org.bytedeco.ffmpeg.global.avutil.av_make_error_string

internal fun describeAvError(error: Int): String {
  val buf = ByteArray(AV_ERROR_MAX_STRING_SIZE)
  av_make_error_string(buf, buf.size.toLong(), error)
  return String(buf)
}
