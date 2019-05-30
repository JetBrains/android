/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.ui

import org.jetbrains.android.util.AndroidBundle.message

import com.android.tools.idea.npw.template.TemplateHandle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import icons.AndroidIcons
import java.io.File
import java.io.IOException
import javax.swing.Icon

/**
 * Utility methods to load Template Images and find labels
 */
object ActivityGallery {
  /**
   * Return the image associated with the current template, if it specifies one, or null otherwise.
   */
  @JvmStatic
  fun getTemplateIcon(templateHandle: TemplateHandle?, isCppTemplate: Boolean): TemplateIcon? {
    if (isCppTemplate) {
      return TemplateIcon(AndroidIcons.Wizards.CppConfiguration)
    }

    val thumb = templateHandle?.metadata?.thumbnailPath
    if (thumb == null || thumb.isEmpty()) {
      return null
    }

    val file = File(templateHandle.rootPath, thumb.replace('/', File.separatorChar))
    val icon = IconLoader.findIcon(file.toURI().toURL()) ?: return null
    return TemplateIcon(icon)
  }

  @JvmStatic
  fun getTemplateImageLabel(templateHandle: TemplateHandle?, isCppTemplate: Boolean): String = when {
    isCppTemplate -> message("android.wizard.gallery.item.add.cpp")
    templateHandle == null -> message("android.wizard.gallery.item.add.no.activity")
    else -> templateHandle.metadata.title ?: ""
  }

  @JvmStatic
  fun getTemplateDescription(templateHandle: TemplateHandle?, isCppTemplate: Boolean): String = when {
      isCppTemplate -> message("android.wizard.gallery.item.add.cpp.Desc")
      templateHandle == null -> message("android.wizard.gallery.item.add.no.activity.desc")
      else -> templateHandle.metadata.description ?: ""
  }
}
