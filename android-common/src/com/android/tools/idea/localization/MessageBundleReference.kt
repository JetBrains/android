/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.localization

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.util.ReflectionUtil
import java.lang.ref.SoftReference
import java.util.ResourceBundle
import java.util.function.Supplier

/**
 * Simple helper class useful for creating a message bundle for your module.
 *
 * It creates a soft reference to an underlying text bundle, which means that it can
 * be garbage collected if needed (although it will be reallocated again if you request
 * a new message from it).
 *
 * You might use it like so:
 *
 * ```
 * # In module 'custom'...
 *
 * # resources/messages/CustomBundle.properties:
 * sample.text.key=This is a sample text value.
 *
 * # src/messages/CustomBundle.kt:
 * private const val BUNDLE_NAME = "messages.CustomBundle"
 * object CustomBundle {
 *   private val bundleRef = MessageBundleReference(BUNDLE_NAME)
 *   fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any) = bundleRef.message(key, *params)
 * }
 * ```
 *
 * That's it! Now you can call `CustomBundle.message("sample.text.key")` to fetch the text value.
 *
 * @param name the fully qualified path to the bundle messages text file
 */
class MessageBundleReference(private val name: String) {
  /**
   * [ReflectionUtil.getCallerClass] has 4 as a parameter because:
   * 1. ReflectionUtil.getCallerClass
   * 2. MessageBundleReference.<init>
   * 3. CustomBundle.<clinit>
   */
  private val bundleClassLoader = ReflectionUtil.getCallerClass(3).classLoader
  private var bundleRef: SoftReference<ResourceBundle>? = null

  private fun getBundle(): ResourceBundle =
    bundleRef?.get() ?:
    DynamicBundle.getResourceBundle(bundleClassLoader, name).also { bundleRef = SoftReference(it) }

  fun message(key: String, vararg params: Any) = AbstractBundle.message(getBundle(), key, *params)

  fun lazyMessage(key: String, vararg params: Any) = Supplier { message(key, *params) }
}