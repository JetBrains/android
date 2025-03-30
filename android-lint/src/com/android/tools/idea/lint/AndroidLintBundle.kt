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
package com.android.tools.idea.lint

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.AndroidLintBundle"

class AndroidLintBundle private constructor() {
  companion object {
    private var ourBundle = DynamicBundle(AndroidLintBundle::class.java, BUNDLE_NAME)

    @JvmStatic
    fun message(
      @PropertyKey(resourceBundle = BUNDLE_NAME) key: String,
      vararg params: Any?,
    ): String {
      return ourBundle.getMessage(key, *params)
    }
  }
}
