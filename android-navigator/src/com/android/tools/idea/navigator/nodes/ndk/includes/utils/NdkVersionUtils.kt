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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils

import com.android.repository.Revision

/**
 * Convert NDK revision like 19.2.5345600 to standard NDK release name like r19c
 */
fun getNdkVersionName(version : String) : String {
  val revision = Revision.parseRevision(version)
  return if (revision.minor == 0) {
    // Don't show 'a' in the NDK version. It should be r20 not r20a
    " r${revision.major}"
  }
  else {
    val minor = ('a'.code + revision.minor).toChar()
    " r${revision.major}$minor"
  }
}