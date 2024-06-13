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
package com.android.tools.idea.lint.quickFixes

import com.android.resources.Density
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import java.util.regex.Pattern

class ConvertToDpQuickFix(element: PsiElement) : PsiBasedModCommandAction<PsiElement>(element) {
  override fun getFamilyName() = "ConvertToDpQuickFix"

  override fun getPresentation(context: ActionContext, element: PsiElement) =
    if (PsiTreeUtil.getParentOfType(element, XmlTag::class.java) != null)
      Presentation.of(message("android.lint.fix.convert.to.dp"))
    else null

  override fun perform(context: ActionContext, element: PsiElement): ModCommand {
    val parentTag =
      PsiTreeUtil.getParentOfType(element, XmlTag::class.java) ?: return ModCommand.nop()

    val densities = Density.values().filter { it.isValidValueForDevice }
    val defaultValue =
      densities.firstOrNull { it.dpiValue == Density.DEFAULT_DENSITY } ?: return ModCommand.nop()
    val initialValue =
      if (ApplicationManager.getApplication().isUnitTestMode) defaultValue
      else densities.firstOrNull { it.dpiValue == ourPrevDpi } ?: defaultValue

    // First choice is the one selected in batch mode, so pick out the initialValue option.
    val actions = mutableListOf(ConvertToDpAction(initialValue, parentTag))
    densities
      .filter { it != initialValue }
      .forEach { actions.add(ConvertToDpAction(it, parentTag)) }

    return ModCommand.chooseAction("Choose Screen Density", actions)
  }

  private class ConvertToDpAction(private val density: Density, private val parentTag: XmlTag) :
    ModCommandAction {
    override fun getFamilyName() = "ConvertToDpQuickFix"

    override fun getPresentation(context: ActionContext) =
      Presentation.of(getLabelForDensity(density))

    @Suppress("UnstableApiUsage")
    override fun perform(context: ActionContext) =
      ModCommand.psiUpdate(parentTag) { tag, _ ->
        val dpi = density.dpiValue
        for (attribute in tag.attributes) {
          val value = attribute.value

          if (value != null && value.endsWith("px")) {
            val newValue = convertToDp(value, dpi)
            if (newValue != null) {
              attribute.setValue(newValue)
            }
          }
        }
        val tagValueElement = tag.value
        val tagValue = tagValueElement.text
        if (tagValue.endsWith("px")) {
          val newValue = convertToDp(tagValue, dpi)

          if (newValue != null) {
            tagValueElement.text = newValue
          }
        }

        if (
          !ApplicationManager.getApplication().isUnitTestMode &&
            !IntentionPreviewUtils.isIntentionPreviewActive()
        ) {
          // Remember the selection only when the fix was actually applied
          ourPrevDpi = dpi
        }
      }
  }

  companion object {
    private val LOG =
      Logger.getInstance("#com.android.tools.idea.lint.quickFixes.ConvertToDpQuickFix")
    private val PX_ATTR_VALUE_PATTERN: Pattern = Pattern.compile("(\\d+)px")

    private var ourPrevDpi = Density.DEFAULT_DENSITY

    private fun convertToDp(value: String, dpi: Int): String? {
      var newValue: String? = null
      val matcher = PX_ATTR_VALUE_PATTERN.matcher(value)

      if (matcher.matches()) {
        val numberString = matcher.group(1)
        try {
          val px = numberString.toInt()
          val dp = px * 160 / dpi
          newValue = dp.toString() + "dp"
        } catch (nufe: NumberFormatException) {
          LOG.error(nufe)
        }
      }
      return newValue
    }

    private fun getLabelForDensity(density: Density) =
      "${density.shortDisplayValue} (${density.dpiValue})"
  }
}
