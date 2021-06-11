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
package com.android.tools.profilers

object PathUtils {
  /**
   * Shorten a path such as "a/b/c/d/e" to "a/.../e"
   */
  @JvmStatic
  fun abbreviate(path: String) = path.split('/').filterNot{it.isEmpty()}.let { parts ->
    when {
      parts.size > 4 -> "/${parts[0]}/${parts[1]}/.../${parts[parts.size - 2]}/${parts.last()}"
      else -> path
    }
  }
}