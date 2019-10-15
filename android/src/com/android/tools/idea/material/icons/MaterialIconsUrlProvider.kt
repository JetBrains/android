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
package com.android.tools.idea.material.icons

import com.android.ide.common.vectordrawable.VdIcon
import com.android.tools.idea.material.icons.MaterialIconsUtils.MATERIAL_ICONS_PATH
import com.android.tools.idea.material.icons.MaterialIconsUtils.toDirFormat
import java.net.URL

/**
 * Interface used to get [URL] objects for [MaterialVdIconsLoader].
 */
interface MaterialIconsUrlProvider {

  /**
   * Returns the [URL] of the files under a particular material icon style.
   */
  fun getStyleUrl(style: String): URL?

  /**
   * Returns the [URL] of the actual Material Icon file for the given style, icon name and its file name.
   */
  fun getIconUrl(style: String, iconName: String, iconFileName: String): URL?
}

/**
 * The default [MaterialIconsUrlProvider] for [VdIcon] files bundled with Android Studio.
 */
internal class MaterialIconsUrlProviderImpl : MaterialIconsUrlProvider {
  override fun getStyleUrl(style: String): URL? {
    return MaterialVdIconsLoader::class.java.classLoader.getResource(getStyleDirectoryPath(style))
  }

  override fun getIconUrl(style: String, iconName: String, iconFileName: String): URL? {
    return MaterialVdIconsLoader::class.java.classLoader.getResource(getIconDirectoryPath(style, iconName) + iconFileName)
  }

  private fun getIconDirectoryPath(style: String, name: String): String {
    return getStyleDirectoryPath(style) + name + "/"
  }

  private fun getStyleDirectoryPath(style: String): String {
    return MATERIAL_ICONS_PATH + style.toDirFormat() + "/"
  }
}