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

/**
 * Utility methods to load Template Images and find labels
 */
@file:JvmName("ActivityGallery")

package com.android.tools.idea.npw.ui

import com.android.tools.idea.npw.template.TemplateHandle
import com.android.tools.idea.wizard.template.Template
import com.intellij.openapi.util.IconLoader
import icons.AndroidIcons
import org.jetbrains.android.util.AndroidBundle.message
import java.io.File

val cppIcon: TemplateIcon get() = TemplateIcon(AndroidIcons.Wizards.CppConfiguration)

/**
 * Return the image associated with the current template, if it specifies one, or null otherwise.
 */
fun getTemplateIcon(templateHandle: TemplateHandle?): TemplateIcon? {
  val thumb = templateHandle?.metadata?.thumbnailPath
  if (thumb == null || thumb.isEmpty()) {
    return TemplateIcon(AndroidIcons.Wizards.NoActivity)
  }

  val file = File(templateHandle.rootPath, thumb.replace('/', File.separatorChar))
  val icon = IconLoader.findIcon(file.toURI().toURL()) ?: return null
  return TemplateIcon(icon)
}

fun getTemplateImageLabel(templateHandle: TemplateHandle?): String = when (templateHandle) {
  null -> message("android.wizard.gallery.item.add.no.activity")
  else -> templateHandle.metadata.title ?: ""
}

fun getTemplateDescription(templateHandle: TemplateHandle?): String = when (templateHandle) {
  null -> message("android.wizard.gallery.item.add.no.activity.desc")
  else -> templateHandle.metadata.description ?: ""
}

fun getTemplateIcon(template: Template): TemplateIcon? {
  if (template == Template.NoActivity) {
    return TemplateIcon(AndroidIcons.Wizards.NoActivity)
  }

  val icon = IconLoader.findIcon(template.thumb().path) ?: return null
  return TemplateIcon(icon)
}

