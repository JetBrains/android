/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.folding.AndroidFoldingSettings
import com.android.tools.idea.wear.dwf.WFFConstants.ATTRIBUTE_EXPRESSION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_PARAMETER
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_TEMPLATE
import com.android.tools.idea.wear.dwf.dom.raw.removeSurroundingQuotes
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.facet.AndroidFacet

/**
 * [FoldingBuilderEx] that folds string resource references in `expression` attributes of
 * `<Parameter>` tags that are within `<Template>` tags.
 *
 * These should only be folded if the whole expression is a string resource reference. There cannot
 * be multiple string resource references in the same expression.
 *
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/group/part/text/formatter/template">Template</a>
 */
class TemplateParameterStringFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(
    root: PsiElement,
    document: Document,
    quick: Boolean,
  ): Array<out FoldingDescriptor?> {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get())
      return FoldingDescriptor.EMPTY_ARRAY
    val facet = AndroidFacet.getInstance(root) ?: return FoldingDescriptor.EMPTY_ARRAY

    return PsiTreeUtil.findChildrenOfType(root, XmlAttributeValue::class.java)
      .filter {
        val parentAttribute = it.parentOfType<XmlAttribute>()
        val parentTag = parentAttribute?.parentOfType<XmlTag>()
        parentAttribute?.name == ATTRIBUTE_EXPRESSION &&
          parentTag?.name == TAG_PARAMETER &&
          parentTag.parentTag?.name == TAG_TEMPLATE
      }
      .mapNotNull { xmlAttributeValue ->
        val reference =
          ResourceReference(
            ResourceNamespace.RES_AUTO,
            ResourceType.STRING,
            xmlAttributeValue.value.removeSurroundingQuotes(),
          )
        val resourceResolver =
          AndroidAnnotatorUtil.pickConfiguration(xmlAttributeValue.containingFile, facet)
            ?.resourceResolver ?: return@mapNotNull null
        val resolvedValue =
          resourceResolver.getResolvedResource(reference)?.value ?: return@mapNotNull null
        FoldingDescriptor(xmlAttributeValue.node, xmlAttributeValue.textRange, null, resolvedValue)
      }
      .toTypedArray()
  }

  override fun isCollapsedByDefault(node: ASTNode) =
    AndroidFoldingSettings.getInstance().isCollapseAndroidStrings

  override fun getPlaceholderText(node: ASTNode) = node.text
}
