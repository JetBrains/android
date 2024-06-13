/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.rendering

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.text.MessageFormat
import java.util.ResourceBundle

/** Messages bundle. */
object RenderingBundle {
  @NonNls private const val BUNDLE_NAME = "messages.RenderingBundle"
  private var ourBundle: Reference<ResourceBundle?>? = null
  private val bundle: ResourceBundle?
    get() {
      var bundle = ourBundle?.get()
      if (bundle == null) {
        bundle = DynamicBundle.getResourceBundle(RenderingBundle::class.java.classLoader, BUNDLE_NAME)
        ourBundle = SoftReference(bundle)
      }
      return bundle
    }

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String {
    return readFromBundleAndFormat(bundle!!, key, *params)
  }

  private fun readFromBundleAndFormat(
    bundle: ResourceBundle,
    key: String,
    vararg params: Any,
  ): String {
    val rawValue = bundle.getString(key)
    val locale = bundle.locale
    val format = MessageFormat(rawValue, locale)
    return format.format(params)
  }
}
