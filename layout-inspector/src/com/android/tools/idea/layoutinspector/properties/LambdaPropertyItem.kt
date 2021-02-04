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

import com.android.tools.property.panel.api.LinkPropertyItem
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.util.application.invokeLater

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
  group = PropertySection.DEFAULT,
  source = null,
  viewId = viewId,
  lookup = lookup
), LinkPropertyItem {
  override val link = object : AnAction("$fileName:$startLineNumber") {
    override fun actionPerformed(event: AnActionEvent) {
      val loc = lookup.resourceLookup.findLambdaLocation(packageName, fileName, lambdaName, functionName, startLineNumber, endLineNumber)
      loc?.navigatable?.let {
        if (it.canNavigate()) {
          invokeLater {
            // Execute this via invokeLater to avoid painting errors by JBTable (hover line) when focus is removed
            it.navigate(true)
            lookup.stats.gotoSourceFromPropertyValue(lookup.selection)
          }
          return
        }
      }
      templatePresentation.isEnabled = false
      templatePresentation.text = "$fileName:unknown"
    }
  }
}
