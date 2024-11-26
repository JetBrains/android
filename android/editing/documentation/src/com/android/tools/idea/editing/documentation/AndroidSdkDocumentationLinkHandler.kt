/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.editing.documentation

import com.android.tools.idea.editing.documentation.target.AndroidSdkDocumentationTarget
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.psi.JavaPsiFacade

/**
 * [DocumentationLinkHandler] that ensures links to other PSI elements work correctly from
 * [AndroidSdkDocumentationTarget]s.
 */
class AndroidSdkDocumentationLinkHandler : DocumentationLinkHandler {
  override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
    if (target !is AndroidSdkDocumentationTarget<*>) return null
    if (url.startsWith(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)) {
      val qualifiedName = url.removePrefix(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL)
      val element =
        JavaPsiFacade.getInstance(target.targetElement.project)
          .findClass(qualifiedName, target.targetElement.resolveScope) ?: return null
      return LinkResolveResult.resolvedTarget(psiDocumentationTargets(element, element).first())
    }
    return null
  }
}
