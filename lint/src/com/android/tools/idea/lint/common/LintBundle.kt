/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.intellij.CommonBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.ResourceBundle

@NonNls private const val BUNDLE_NAME = "messages.LintBundle"

class LintBundle private constructor() {
  companion object {
    private var ourBundle: Reference<ResourceBundle?>? = null

    private fun getBundle(): ResourceBundle {
      var bundle: ResourceBundle? = ourBundle?.get()
      if (bundle == null) {
        bundle = DynamicBundle.getResourceBundle(LintBundle::class.java.classLoader, BUNDLE_NAME)
        ourBundle = SoftReference(bundle)
      }
      return bundle
    }

    @JvmStatic
    fun message(
      @PropertyKey(resourceBundle = BUNDLE_NAME) key: String,
      vararg params: Any?,
    ): String {
      return CommonBundle.message(getBundle(), key, *params)
    }
  }
}
