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
@file:JvmName(name = "ComposePreviewBundle")

package com.android.tools.idea.compose.preview

import com.intellij.AbstractBundle
import com.intellij.reference.SoftReference
import java.lang.ref.Reference
import java.util.ResourceBundle
import org.jetbrains.annotations.PropertyKey

const val BUNDLE: String = "com.android.tools.idea.compose.preview.ComposePreviewBundle"

fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
  AbstractBundle.message(getBundle(), key, *params)

private var ourBundle: Reference<ResourceBundle>? = null

private fun getBundle(): ResourceBundle {
  var bundle = SoftReference.dereference(ourBundle)

  if (bundle == null) {
    bundle = ResourceBundle.getBundle(BUNDLE)!!
    ourBundle = SoftReference(bundle)
  }

  return bundle
}
