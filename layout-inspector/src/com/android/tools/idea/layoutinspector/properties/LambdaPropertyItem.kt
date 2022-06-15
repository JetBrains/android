/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.property.panel.api.LinkPropertyItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.util.application.invokeLater
import java.util.concurrent.TimeUnit

/**
 * A [LinkPropertyItem] for a lambda parameter from Compose.
 *
 * @param name the parameter name
 * @param viewId the compose node this parameter belongs to
 * @param packageName the package name of the enclosing class as found in the synthetic name of the lambda
 * @param fileName the name of the enclosing file
 * @param lambdaName the second part of the synthetic lambda name
 * @param startLineNumber the first line number of the lambda as reported by JVMTI (1 based)
 * @param endLineNumber the last line number of the lambda as reported by JVMTI (1 based)
 */
class LambdaPropertyItem(
  name: String,
  viewId: Long,
  val packageName: String,
  val fileName: String,
  val lambdaName: String,
  val functionName: String,
  val startLineNumber: Int,
  val endLineNumber: Int,
  lookup: ViewNodeAndResourceLookup
): InspectorPropertyItem(
  namespace = "",
  attrName = name,
  name = name,
  initialType = PropertyType.LAMBDA,
  initialValue = "λ",
  section = PropertySection.DEFAULT,
  source = null,
  viewId = viewId,
  lookup = lookup
), LinkPropertyItem {
  override val link = object : AnAction("$fileName:$startLineNumber") {
    override fun actionPerformed(event: AnActionEvent) {
      val location =
        lookup.resourceLookup.findLambdaLocation(packageName, fileName, lambdaName, functionName, startLineNumber, endLineNumber)
      location.navigatable?.let {
        if (it.canNavigate()) {
          invokeLater {
            // Execute this via invokeLater to avoid painting errors by JBTable (hover line) when focus is removed
            it.navigate(true)
            LayoutInspector.get(event)?.currentClient?.stats?.gotoSourceFromPropertyValue(lookup.selection)
            if (location.source.endsWith(":unknown")) {
              showBalloonError("Could not determine exact source location", event)
            }
          }
          return
        }
      }
      showBalloonError("Could not determine source location", event)
    }
  }

  @Suppress("SameParameterValue")
  private fun showBalloonError(content: String, event: AnActionEvent) {
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    val background = globalScheme.getColor(EditorColors.NOTIFICATION_BACKGROUND) ?: UIUtil.getToolTipBackground()
    val balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(content, AllIcons.General.BalloonWarning, background, null)
      .setBorderColor(JBColor.border())
      .setBorderInsets(JBInsets.create(4, 4))
      .setFadeoutTime(TimeUnit.SECONDS.toMillis(4))
      .createBalloon()
    balloon.show(JBPopupFactory.getInstance().guessBestPopupLocation(event.dataContext), Balloon.Position.above)
  }
}
