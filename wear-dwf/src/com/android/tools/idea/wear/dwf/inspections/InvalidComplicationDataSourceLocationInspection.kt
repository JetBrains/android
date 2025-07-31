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

import com.android.SdkConstants.ATTR_TYPE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wear.dwf.WFFConstants
import com.android.tools.idea.wear.dwf.WFFConstants.DataSources
import com.android.tools.idea.wear.dwf.WearDwfBundle.message
import com.android.tools.idea.wear.dwf.dom.raw.expressions.StaticDataSource
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionDataSourceOrConfiguration
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionLanguage
import com.android.tools.idea.wear.dwf.dom.raw.expressions.WFFExpressionVisitor
import com.android.tools.idea.wear.dwf.dom.raw.expressions.getParentComplicationTag
import com.android.tools.idea.wear.dwf.dom.raw.expressions.getWatchFaceFile
import com.android.tools.wear.wff.WFFVersion
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile

/**
 * Inspection that checks that a complication data source is properly located under the right
 * complication slot.
 *
 * Complication data sources, for example `[COMPLICATION.TEXT]`, can only be used in
 * [WFFExpressionLanguage] if the WFF expression is located under a `<Complication>` tag within a
 * Declarative Watch Face. The complication data sources available depend on the type of the
 * `<Complication>` tag as well the Watch Face's [WFFVersion].
 *
 * @see WFFConstants.DataSources.COMPLICATION_BY_TYPE
 * @see <a
 *   href="https://developer.android.com/reference/wear-os/wff/complication/complication">Complication</a>
 */
class InvalidComplicationDataSourceLocationInspection : LocalInspectionTool() {
  override fun getStaticDescription() =
    message("inspection.invalid.complication.data.source.location.description")

  override fun isAvailableForFile(file: PsiFile): Boolean {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) return false
    val watchFaceFile = getWatchFaceFile(file) ?: return false
    return isDeclarativeWatchFaceFile(watchFaceFile)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : WFFExpressionVisitor() {
      override fun visitDataSourceOrConfiguration(element: WFFExpressionDataSourceOrConfiguration) {
        val complicationDataSource =
          DataSources.COMPLICATION_ALL.find { it.id == element.id.text } ?: return
        val parentComplicationTag = getParentComplicationTag(element)
        if (parentComplicationTag == null) {
          holder.registerProblem(
            element,
            message("inspection.invalid.complication.data.source.location.missing.tag"),
            ProblemHighlightType.ERROR,
          )
          return
        }

        val parentComplicationType = parentComplicationTag.getAttribute(ATTR_TYPE)?.value
        val compatibleTypes = compatibleComplicationTypes(complicationDataSource)
        if (parentComplicationType in compatibleTypes) {
          // all good
          return
        }
        holder.registerProblem(
          element,
          message(
            "inspection.invalid.complication.data.source.location.available.complication.types",
            compatibleTypes.joinToString { "\"$it\"" },
          ),
          ProblemHighlightType.ERROR,
        )
      }
    }
  }

  private fun compatibleComplicationTypes(dataSource: StaticDataSource) =
    DataSources.COMPLICATION_BY_TYPE.filter { (_, dataSources) ->
        dataSource.id in dataSources.map { it.id }
      }
      .keys
}
