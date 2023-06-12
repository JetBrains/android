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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.compose

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.property.panel.api.LinkPropertyItem
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.application.executeOnPooledThread
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A [LinkPropertyItem] for a lambda parameter from Compose.
 *
 * @param name the parameter name
 * @param section the section the parameter will show up in the parameters/attributes table
 * @param viewId the compose node this parameter belongs to
 * @param packageName the package name of the enclosing class as found in the synthetic name of the lambda
 * @param fileName the name of the enclosing file
 * @param lambdaName the second part of the synthetic lambda name examples: "1", "f1$1"
 * @param startLineNumber the first line number of the lambda as reported by JVMTI (1 based)
 * @param endLineNumber the last line number of the lambda as reported by JVMTI (1 based)
 */
class LambdaParameterItem(
  name: String,
  section: PropertySection,
  viewId: Long,
  rootId: Long,
  index: Int,
  val packageName: String,
  val fileName: String,
  val lambdaName: String,
  val functionName: String,
  val startLineNumber: Int,
  val endLineNumber: Int,
  lookup: ViewNodeAndResourceLookup
): ParameterItem(
  name,
  PropertyType.LAMBDA,
  value = "λ",
  section,
  viewId,
  lookup,
  rootId,
  index,
), LinkPropertyItem {
  override val link = object : AnAction("$fileName:$startLineNumber") {
    override fun actionPerformed(event: AnActionEvent) {
      val popupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(event.dataContext)
      executeOnPooledThread {
        gotoLambdaLocation(event, popupLocation)
      }.also { futureCaptor?.invoke(it) }
    }
  }

  /**
   * Allow tests to control the execution of [gotoLambdaLocation].
   */
  @VisibleForTesting
  var futureCaptor: ((Future<*>) -> Unit)? = null

  @Slow
  private fun gotoLambdaLocation(event: AnActionEvent, popupLocation: RelativePoint) {
    val location = runReadAction {
      lookup.resourceLookup.findLambdaLocation(packageName, fileName, lambdaName, functionName, startLineNumber, endLineNumber)
    }
    location.navigatable?.let {
      if (runReadAction { it.canNavigate() }) {
        invokeLater {
          // Execute this via invokeLater to avoid painting errors by JBTable (hover line) when focus is removed
          it.navigate(true)
          LayoutInspector.get(event)?.currentClient?.stats?.gotoSourceFromPropertyValue(lookup.selection)
          if (location.source.endsWith(":unknown")) {
            showBalloonError("Could not determine exact source location", popupLocation)
          }
        }
        return
      }
    }
    invokeLater {
      showBalloonError("Could not determine source location", popupLocation)
    }
  }

  @Suppress("SameParameterValue")
  @UiThread
  private fun showBalloonError(content: String, popupLocation: RelativePoint) {
    val globalScheme = EditorColorsManager.getInstance().globalScheme
    val background = globalScheme.getColor(EditorColors.NOTIFICATION_BACKGROUND) ?: UIUtil.getToolTipBackground()
    val balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(content, AllIcons.General.BalloonWarning, background, null)
      .setBorderColor(JBColor.border())
      .setBorderInsets(JBInsets.create(4, 4))
      .setFadeoutTime(TimeUnit.SECONDS.toMillis(4))
      .createBalloon()
    balloon.show(popupLocation, Balloon.Position.above)
  }

  override fun clone(): LambdaParameterItem = LambdaParameterItem(
    name, section, viewId, rootId, index, packageName, fileName, lambdaName, functionName, startLineNumber, endLineNumber, lookup)
}
