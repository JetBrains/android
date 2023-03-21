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
package com.android.tools.idea.rendering.parsers

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.resourceNamespace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.assertj.core.util.VisibleForTesting

/** Studio-specific [XmlFile]-based implementation of [RenderXmlFile]. */
class PsiXmlFile(@VisibleForTesting val xmlFile: XmlFile) : RenderXmlFile {
  override val folderType: ResourceFolderType?
    get() = getFolderType(xmlFile)

  override val rootTag: RenderXmlTag?
    get() = PsiXmlTag.create(AndroidPsiUtils.getRootTagSafely(xmlFile))

  override val project: Project
    get() = xmlFile.project

  override val name: String
    get() = xmlFile.name

  override val isValid: Boolean
    get() = xmlFile.isValid

  override val relativePath: String
    get() = xmlFile.virtualFile.path

  override val resourceNamespace: ResourceNamespace?
    get() = xmlFile.resourceNamespace

  /** Get the value of an attribute in the [XmlFile] safely (meaning it will acquire the read lock first). */
  override fun getRootTagAttribute(attribute: String, namespace: String?): String? {
    val application = ApplicationManager.getApplication()
    return if (!application.isReadAccessAllowed) {
      application.runReadAction(Computable { getRootTagAttribute(attribute, namespace) })
    } else {
      xmlFile.rootTag?.let { tag -> namespace?.let { tag.getAttribute(attribute, it) } ?: tag.getAttribute(attribute) }?.value
    }
  }

  override val psiFile: PsiFile
    get() = xmlFile
}