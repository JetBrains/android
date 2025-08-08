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

import com.android.SdkConstants.TAG_COMPLICATION
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_PARAMETER
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_TEMPLATE
import com.android.tools.idea.wear.dwf.dom.raw.configurations.UserConfigurationReference
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentInFile
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

fun getReferences(literalExpr: WFFExpressionLiteralExpr): Array<PsiReference> {
  val userConfigurationReference =
    getWatchFaceFile(literalExpr)
      ?.let { watchFaceFile ->
        UserConfigurationReference(literalExpr, watchFaceFile, literalExpr.text)
      }
      ?.takeIf { literalExpr.id != null || literalExpr.dataSourceOrConfiguration != null }
  return listOfNotNull(userConfigurationReference, templateParameterStringReference(literalExpr))
    .toTypedArray()
}

/**
 * Retrieves the Declarative Watch Face [XmlFile] associated with the element. If the element is
 * within an injected [WFFExpressionLanguage], then the Declarative Watch Face file is the file the
 * language is injected in. Otherwise, we attempt to use the current file.
 */
fun getWatchFaceFile(element: PsiElement): XmlFile? {
  val injectedLanguageManager = InjectedLanguageManager.getInstance(element.project)
  val psiFile = injectedLanguageManager.getTopLevelFile(element) ?: element.containingFile
  return psiFile as? XmlFile
}

/**
 * Returns the parent `<Complication>` tag from the Declarative Watch Face file the WFF Expression
 * has been injected in, if any.
 */
fun getParentComplicationTag(element: PsiElement): XmlTag? {
  val injectedHost =
    InjectedLanguageManager.getInstance(element.project).getInjectionHost(element.containingFile)
      ?: return null
  return injectedHost.findParentInFile(withSelf = true) {
    (it as? XmlTag)?.name == TAG_COMPLICATION
  } as? XmlTag
}

/**
 * Expressions in `<Parameter>` tags can reference a string resource. However, to do so, the whole
 * expression must be the reference to the string. If there are other elements in the expression,
 * then the resource name will be used.
 *
 * This method returns a [TemplateParameterStringReference] if the given [literalExpr] is the only
 * element in the expression, is inside a `<Template>`'s `<Parameter>` tag and, is an ID or a
 * string.
 *
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/group/part/text/formatter/template">Template</a>
 */
private fun templateParameterStringReference(
  literalExpr: WFFExpressionLiteralExpr
): TemplateParameterStringReference? {
  if (literalExpr.id == null && literalExpr.quotedString == null) return null
  if (!isOnlyElementInExpression(literalExpr)) return null
  if (!isInjectedInTemplateParameterTag(literalExpr)) return null

  return TemplateParameterStringReference(literalExpr)
}

/** Returns whether the given [element] is the only element in the WFF expression */
private fun isOnlyElementInExpression(element: WFFExpressionLiteralExpr): Boolean {
  return element.parent.elementType is IFileElementType
}

/** Returns whether the element is inside a `<Parameter>` tag within a `<Template>` tag. */
private fun isInjectedInTemplateParameterTag(element: WFFExpressionLiteralExpr): Boolean {
  val injectedHost =
    InjectedLanguageManager.getInstance(element.project).getInjectionHost(element.containingFile)
      ?: return false

  val xmlTag = injectedHost.parentOfType<XmlTag>() as? XmlTag ?: return false
  return xmlTag.name == TAG_PARAMETER && xmlTag.parentTag?.name == TAG_TEMPLATE
}
