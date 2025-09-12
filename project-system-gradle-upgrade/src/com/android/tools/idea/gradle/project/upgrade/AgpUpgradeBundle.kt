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
package com.android.tools.idea.gradle.project.upgrade

import com.intellij.AbstractBundle
import com.intellij.reference.SoftReference.dereference
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.ResourceBundle
import java.util.function.Supplier
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey

class AgpUpgradeBundle private constructor() {

  companion object {
    private const val BUNDLE_NAME = "messages.AgpUpgradeBundle"
    private var ourBundle: Reference<ResourceBundle>? = null

    @JvmStatic
    fun getBundle(): ResourceBundle =
      dereference(ourBundle) ?: ResourceBundle.getBundle(BUNDLE_NAME).also { ourBundle = SoftReference(it) }

    @JvmStatic
    fun message(@NotNull @PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): @Nls String =
      AbstractBundle.message(getBundle(), key, *params)

    @JvmStatic
    fun messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): Supplier<String> =
      Supplier<String> { message(key, *params) }
  }
}