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
package org.jetbrains.android.dom.xml

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.CustomLogicResourceDomFileDescription

/** Describes files in [ResourceFolderType.XML] that has preference class as root tag. */
class PreferenceClassDomFileDescription :
  CustomLogicResourceDomFileDescription<PreferenceElement>(
    PreferenceElement::class.java,
    ResourceFolderType.XML,
    "preference"
  ) {

  object Util {
    @JvmStatic
    fun isPreferenceClassFile(file: XmlFile): Boolean {
      val rootTag = file.rootTag ?: return false

      // If the root tag uses a custom namespace, leave it alone and don't provide any schema. See
      // IDEA-105294.
      if (rootTag.attributes.any { it.name == SdkConstants.XMLNS }) {
        return false
      }

      // If we know parent tag then it should use [XmlResourceDomFileDescription] or another
      // specific description class. If we don't, we assume it's a class name that we need to
      // resolve.
      return rootTag.name !in AndroidXmlResourcesUtil.KNOWN_ROOT_TAGS
    }
  }

  override fun checkFile(file: XmlFile, module: Module?): Boolean {
    return runReadAction { Util.isPreferenceClassFile(file) }
  }
}
