// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet

import com.intellij.facet.ui.FacetEditorTab
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * This class is to redirect a user from Android-Gradle facet tab in Idea's project structure to "Android Project Structure" in
 * Project Setting dialog.
 *
 * @see com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
 */
class AndroidGradleFacetEditorForIdea(private val project: Project) : FacetEditorTab() {
  override fun isModified() = false

  @Nls
  override fun getDisplayName() = AndroidBundle.message("configurable.GradleFacetEditorTab.display.name")

  override fun createComponent(): JComponent = panel {
    row(AndroidBundle.message("configurable.AndroidProjectStructureConfigurableForIdea.redirect.text")){
      ShowSettingsUtil.getInstance().showSettingsDialog(project, AndroidBundle.message("configurable.AndroidProjectStructureConfigurableForIdea.display.name"))
    }
  }
}