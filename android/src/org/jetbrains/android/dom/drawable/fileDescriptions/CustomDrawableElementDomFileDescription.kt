/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.dom.drawable.fileDescriptions

import com.android.resources.ResourceFolderType
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.CustomLogicResourceDomFileDescription
import org.jetbrains.android.dom.drawable.AndroidDrawableDomUtil
import org.jetbrains.android.dom.drawable.CustomDrawableDomElement

/**
 * Root tag based on user classes. If we can't parse tag as known one (see [checkFile]), we assume
 * it comes from class.
 */
class CustomDrawableElementDomFileDescription :
  CustomLogicResourceDomFileDescription<CustomDrawableDomElement>(
    CustomDrawableDomElement::class.java,
    ResourceFolderType.DRAWABLE,
    "customDrawable"
  ) {

  override fun checkFile(file: XmlFile, module: Module?): Boolean {
    val nonCustomDrawableTags =
      module?.androidFacet?.let { AndroidDrawableDomUtil.getPossibleRoots(it) } ?: return false
    val isNonCustomDrawableTag =
      file.rootTag != null && nonCustomDrawableTags.contains(file.rootTag!!.name)
    return !isNonCustomDrawableTag
  }
}
