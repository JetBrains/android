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
package com.android.tools.idea.wear.dwf.inspections

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.idea.wear.dwf.dom.raw.configurations.ColorConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.configurations.UserConfigurationReference
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionLiteralExpr
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionVisitor
import com.android.tools.idea.wear.dwf.dom.raw.expressions.getWatchFaceFile
import com.android.tools.idea.wear.dwf.dom.raw.extractUserConfigurations
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile

class InvalidColorIndexXmlInspection : LocalInspectionTool() {
  override fun getStaticDescription() = message("inspection.invalid.color.index.description")

  override fun isAvailableForFile(file: PsiFile): Boolean {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) return false
    val watchFaceFile = file as? XmlFile ?: return false
    return isDeclarativeWatchFaceFile(watchFaceFile)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val watchFaceFile =
      holder.file as? XmlFile ?: throw IllegalStateException("Missing watch face file")
    val colorConfigurationsById = watchFaceFile.getColorConfigurationsById()

    return object : XmlElementVisitor() {
      override fun visitXmlAttributeValue(xmlAttributeValue: XmlAttributeValue) {
        val reference = xmlAttributeValue.reference as? UserConfigurationReference ?: return
        val colorIndexTextRange =
          reference.colorIndex?.let { colorIndex ->
            val colorIndexStartOffset =
              xmlAttributeValue.value
                .lastIndexOf(".$colorIndex")
                .takeIf { it > -1 }
                ?.let { it + 2 } // skip the " and . characters
              ?: return@let null
            TextRange(colorIndexStartOffset, colorIndexStartOffset + colorIndex.length)
          }

        visitReference(reference, holder, colorIndexTextRange, colorConfigurationsById)
      }
    }
  }
}

class InvalidColorIndexWFFExpressionInspection : LocalInspectionTool() {
  override fun getStaticDescription() = message("inspection.invalid.color.index.description")

  override fun isAvailableForFile(file: PsiFile): Boolean {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) return false
    val watchFaceFile = getWatchFaceFile(file) ?: return false
    return isDeclarativeWatchFaceFile(watchFaceFile)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val watchFaceFile =
      getWatchFaceFile(holder.file) ?: throw IllegalStateException("Missing watch face file")
    val colorConfigurationsById = watchFaceFile.getColorConfigurationsById()

    return object : WFFExpressionVisitor() {
      override fun visitLiteralExpr(literalExpr: WFFExpressionLiteralExpr) {
        val reference = literalExpr.reference as? UserConfigurationReference ?: return
        val colorIndexTextRange =
          reference.colorIndex?.let { colorIndex ->
            val colorIndexStartOffset =
              literalExpr.text
                .lastIndexOf(".$colorIndex")
                .takeIf { it > -1 }
                ?.let { it + 1 } // skip the . character
              ?: return@let null
            TextRange(colorIndexStartOffset, colorIndexStartOffset + colorIndex.length)
          }
        visitReference(reference, holder, colorIndexTextRange, colorConfigurationsById)
      }
    }
  }
}

private fun XmlFile.getColorConfigurationsById() =
  extractUserConfigurations().filterIsInstance<ColorConfiguration>().associateBy { it.id }

/** Visits a [UserConfigurationReference] and checks that the color index is valid. */
private fun visitReference(
  reference: UserConfigurationReference,
  holder: ProblemsHolder,
  colorIndexTextRange: TextRange?,
  colorConfigurationsById: Map<String, ColorConfiguration>,
) {
  if (reference.resolve() == null) return
  val colorConfiguration = colorConfigurationsById[reference.userConfigurationId] ?: return

  if (colorConfiguration.colorIndices.isEmpty()) {
    holder.registerProblem(
      reference.element,
      message("inspection.invalid.color.index.missing.colors"),
      ProblemHighlightType.ERROR,
    )
    return
  }

  val colorIndexRange = "[0, ${colorConfiguration.colorIndices.last}]"

  if (reference.colorIndex == null || reference.colorIndex.isEmpty()) {
    if (colorConfiguration.colorIndices.length > 1) {
      holder.registerProblem(
        reference.element,
        message("inspection.invalid.color.index.missing.index", colorIndexRange),
        ProblemHighlightType.ERROR,
      )
    }
    return
  }

  if (reference.colorIndex.toIntOrNull() !in colorConfiguration.colorIndices) {
    if (colorConfiguration.colorIndices.length <= 1) {
      holder.registerProblem(
        reference.element,
        message("inspection.invalid.color.index.invalid.index.optional", colorIndexRange),
        ProblemHighlightType.ERROR,
        colorIndexTextRange,
      )
    } else {
      holder.registerProblem(
        reference.element,
        message("inspection.invalid.color.index.invalid.index.in.range", colorIndexRange),
        ProblemHighlightType.ERROR,
        colorIndexTextRange,
      )
    }
  }
}

val IntRange.length
  get() = endInclusive - start + 1
