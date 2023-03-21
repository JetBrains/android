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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** Representation of a xml file. Used in rendering pipeline. */
interface RenderXmlFile {
  val folderType: ResourceFolderType?

  val rootTag: RenderXmlTag?

  val project: Project

  val name: String

  val isValid: Boolean

  val relativePath: String

  val resourceNamespace: ResourceNamespace?

  fun getRootTagAttribute(attribute: String, namespace: String?): String?

  /**
   * A [PsiFile] of this xml file, used only in [RenderErrorContributor] through [RenderResult]. Should be implemented by every
   * implementation but can be no-op outside of studio.
   */
  val psiFile: PsiFile
}