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
package com.android.tools.rendering.parsers

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.util.PathString
import com.android.resources.ResourceFolderType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Snapshot of a render-able XML file (on disk or in memory). */
class RenderXmlFileSnapshot(
  override val project: Project,
  override val name: String,
  override val folderType: ResourceFolderType?,
  fileContent: String,
) : RenderXmlFile {

  constructor(
    project: Project,
    filePath: PathString,
  ) : this(
    project,
    filePath.fileName,
    ResourceFolderType.getFolderType(filePath.parentFileName!!),
    filePath.toFile()!!.readBytes().toString(Charsets.UTF_8),
  )

  constructor(project: Project, filePath: String) : this(project, PathString(filePath))

  override val rootTag: RenderXmlTag = parseRootTag(fileContent)
  override val isValid: Boolean = true
  override val relativePath: String = name
  override val resourceNamespace: ResourceNamespace = ResourceNamespace.RES_AUTO

  override fun getRootTagAttribute(attribute: String, namespace: String?): String? =
    (namespace?.let { rootTag.getAttribute(namespace, attribute) }
        ?: rootTag.getAttribute(attribute))
      ?.value

  override fun get(): PsiFile {
    throw NotImplementedError("Getting PsiFile from XmlFileSnapshot is not supported.")
  }
}
