/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.project

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.projectPropertiesModel
import com.android.tools.idea.gradle.structure.configurables.ui.ModelPanel
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ConfigPanel
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.structure.dialog.VersionCatalogWarningHeader
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension

class ProjectPropertiesConfigPanel(project: PsProject, context: PsContext) :
  ConfigPanel<PsProject>(
    context,
    project,
    null,
    project,
    projectPropertiesModel()
  ),
  ModelPanel<PsProject> {

  init {
    uiComponent.border = JBUI.Borders.empty(8, 12, 8, 12)
    (uiComponent.components[0] as? JBScrollPane)?.border = JBUI.Borders.empty()
    uiComponent.minimumSize = Dimension(500, 300)
    uiComponent.preferredSize = Dimension(1050, 440)
    if (GradleVersionCatalogDetector.getInstance(project.ideProject).isVersionCatalogProject) {
      if (StudioFlags.GRADLE_VERSION_CATALOG_DISPLAY_BANNERS.get()) {
        uiComponent.add(VersionCatalogWarningHeader(), BorderLayout.NORTH)
      }
    }
  }

  override val title = "Properties"
  override fun setHistory(history: History?) = Unit
  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = ActionCallback.DONE
  override fun queryPlace(place: Place) = Unit
  // Currently there are no other tabs in the project perspective and they are not tracked.
  override val topConfigurable: PSDEvent.PSDTopTab? = null
}
