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
package com.android.tools.idea.analytics

import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName

interface IdeBrandProvider {
  fun get() : AndroidStudioEvent.IdeBrand
}

private val EP = ExtensionPointName.create<IdeBrandProvider>("com.android.tools.idea.analytics.ideBrandProvider")

fun currentIdeBrand() : AndroidStudioEvent.IdeBrand {
  val extensions = EP.extensionList
  if (extensions.isEmpty()) {
    return AndroidStudioEvent.IdeBrand.ANDROID_STUDIO
  }

  // We shouldn't have multiple plugins implementing this extension. But it is only for analytics, so we just log a warning.
  if (extensions.size > 1) {
    val allBrands = extensions.map { it.get() }.joinToString { ", " }
    Logger.getInstance(IdeBrandProvider::class.java).warn("Multiple IDE brands detected ($allBrands), using the first one.")
  }

  return extensions.first().get()
}