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
package org.jetbrains.android.dom.navigation

import com.android.SdkConstants.*
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.AndroidPsiUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_START_DESTINATION

fun getStartDestLayoutId(navResourceId: String, project: Project, resourceResolver: ResourceResolver): String? {
  if (!navResourceId.startsWith("@navigation/")) {
    return null
  }
  val fileName = resourceResolver.findResValue(navResourceId, false)?.value ?: return null
  val file = LocalFileSystem.getInstance().findFileByPath(fileName) ?: return null
  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, file) as? XmlFile ?: return null
  return ApplicationManager.getApplication().runReadAction(Computable<String> {
    val startDestId = stripId(psiFile.rootTag?.attributes?.firstOrNull { it.localName == ATTR_START_DESTINATION }?.value)
    if (startDestId != null) {
      val startDest = psiFile.rootTag
        ?.children
        ?.filterIsInstance(XmlTag::class.java)
        ?.firstOrNull { stripId(it.attributes.firstOrNull { it.localName == ATTR_ID }?.value) == startDestId
      }

      startDest?.getAttributeValue(ATTR_LAYOUT, TOOLS_URI)
    } else {
      null
    }
  })
}

private fun stripId(id: String?): String? {
  if (id != null) {
    if (id.startsWith(NEW_ID_PREFIX)) {
      return id.substring(NEW_ID_PREFIX.length)
    } else if (id.startsWith(ID_PREFIX)) {
      return id.substring(ID_PREFIX.length)
    }
  }
  return null
}
