/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.idea.npw.ui.TemplateIcon
import com.google.common.cache.CacheLoader
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader.findIcon
import java.io.File
import javax.swing.Icon
import java.util.Optional

private val log get() = logger<IconLoader>()

/**
 * Guava [CacheLoader] which can convert a file path to an icon. This is used to help us load standard 256x256 icons out of template files.
 *
 * Note: optional [Icon] is used instead of nullable [Icon] because null is a special value in cacheLoader and should not be used.
 */
internal class IconLoader : CacheLoader<File, Optional<Icon>>() {
  override fun load(iconPath: File): Optional<Icon> {
    if (!iconPath.isFile) {
      log.warn("Image file ${iconPath.absolutePath} was not found")
      return Optional.empty()
    }
    val icon = findIcon(iconPath.toURI().toURL()) ?: run {
      log.warn("File ${iconPath.absolutePath} exists but is not a valid image")
      return Optional.empty()
    }
    return Optional.of(
      TemplateIcon(icon).apply {
        cropBlankWidth()
        setHeight(256)
      })
  }
}
