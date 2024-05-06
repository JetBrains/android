/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutlib

import com.android.tools.idea.layoutlib.LayoutLibraryLoader.LayoutLibraryProvider
import com.intellij.openapi.extensions.ExtensionPointName


/**
 * If the [LayoutLibraryProvider] service is declared in a different plugin, it
 * cannot be found by the [ServiceLoader] because each plugin has its own
 * classloader. To use [LayoutLibraryProvider] services from other plugins in
 * android plugin we set up an extension point so other plugins can subscribe
 * their services to it so that they are accessible in android plugin.
 */
class LayoutLibraryProviderBridge : LayoutLibraryProvider {
  private val epName = ExtensionPointName<LayoutLibraryProvider>("com.android.tools.idea.layoutlib.layoutLibraryProvider")

  override fun getLibrary(): LayoutLibrary? =
    epName.computeSafeIfAny(LayoutLibraryProvider::getLibrary)

  override fun getFrameworkRClass(): Class<*>? =
    epName.computeSafeIfAny(LayoutLibraryProvider::getFrameworkRClass)
}