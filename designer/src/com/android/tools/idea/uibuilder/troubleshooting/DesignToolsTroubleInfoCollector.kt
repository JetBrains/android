/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.troubleshooting

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.troubleshooting.TroubleInfoCollector

/**
 * Collects diagnostics from Design Tools to be displayed when the user uses `Help/Collect
 * Troubleshooting Information...`
 */
class DesignToolsTroubleInfoCollector(
  private val defaultCollectors: List<TroubleInfoCollector> =
    listOf(
      BuildStatusTroubleInfoCollector(),
      FastPreviewTroubleInfoCollector(),
      ProjectInfoTroubleInfoCollector(),
    )
) : TroubleInfoCollector {
  override fun toString(): String = "Design Tools"

  override fun collectInfo(project: Project): String =
    StringBuilder()
      .also { output ->
        (defaultCollectors + providersExtensionPoint.extensions).forEach { collector ->
          val collectedString = collector.collectInfo(project)
          if (collectedString.isNotBlank()) output.appendLine(collectedString).appendLine()
        }
      }
      .toString()

  companion object {
    /**
     * Extension point for [TroubleInfoCollector] specific for Design Tools. All the extensions will
     * be shown in the "Design Tools" tab in `Help/Collect Troubleshooting Information...`.
     */
    val providersExtensionPoint: ExtensionPointName<TroubleInfoCollector> =
      ExtensionPointName.create("com.android.tools.idea.uibuilder.troubleshooting.infoCollector")
  }
}
