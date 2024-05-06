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
package org.jetbrains.android

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttribute
import org.jetbrains.android.facet.AndroidFacet
import com.android.tools.idea.res.isResourceFile

/**
 * {@link GotoDeclarationHandler} which handles XML attribute names.
 */
class XmlAttributeNameGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
    if (sourceElement == null) {
      return PsiElement.EMPTY_ARRAY
    }
    val facet = AndroidFacet.getInstance(sourceElement) ?: return PsiElement.EMPTY_ARRAY
    if (!isResourceFile(sourceElement.containingFile.virtualFile, facet)) {
      return PsiElement.EMPTY_ARRAY
    }
    val attribute = sourceElement.parent as? XmlAttribute ?: return PsiElement.EMPTY_ARRAY
    val namespace = ResourceNamespace.fromNamespaceUri(attribute.namespace) ?: return PsiElement.EMPTY_ARRAY
    return AndroidResourceToPsiResolver.getInstance().getXmlAttributeNameGotoDeclarationTargets(
      attribute.localName,
      namespace,
      sourceElement)
  }
}
