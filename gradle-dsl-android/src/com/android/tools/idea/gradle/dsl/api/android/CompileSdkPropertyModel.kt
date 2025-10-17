/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.android

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel

interface CompileSdkPropertyModel : ResolvedPropertyModel {
  companion object {
    // support added in the earliest 8.13
    const val COMPILE_SDK_BLOCK_VERSION = "8.13.0-alpha01"
    const val COMPILE_SDK_INTRODUCED_VERSION = "4.1.0"
  }

  /**
   * @return the compile SDK config model referenced to by this property.
   */
  fun toCompileSdkConfig(): CompileSdkBlockModel?
}