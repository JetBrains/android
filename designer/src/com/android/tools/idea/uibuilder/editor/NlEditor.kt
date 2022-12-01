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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.DesignerEditor
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.componenttree.NlComponentTreeDefinition
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.palette.PaletteDefinition
import com.android.tools.idea.uibuilder.property.NlPropertiesPanelDefinition
import com.android.tools.idea.uibuilder.structure.NlLegacyComponentTreeDefinition
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.uipreview.AndroidEditorSettings

private const val WORKBENCH_NAME = "NELE_EDITOR"

const val NL_EDITOR_ID = "android-designer2"

private val LAYOUT_EDITOR_SUPPORTED_ACTIONS = setOf(NlSupportedActions.SWITCH_DEVICE,
                                                    NlSupportedActions.SWITCH_DEVICE_ORIENTATION,
                                                    NlSupportedActions.SWITCH_DESIGN_MODE,
                                                    NlSupportedActions.SWITCH_NIGHT_MODE,
                                                    NlSupportedActions.TOGGLE_ISSUE_PANEL)

class NlEditor(file: VirtualFile, project: Project) : DesignerEditor(file, project) {

  override fun getEditorId() = NL_EDITOR_ID

  override fun createEditorPanel() =
    DesignerEditorPanel(this, myProject, myFile, WorkBench(myProject, WORKBENCH_NAME, this, this),
                        {
                          NlDesignSurface.builder(myProject, this)
                            .setRunVisualLintAnalysis(StudioFlags.NELE_VISUAL_LINT_ALWAYS_RUN.get())
                            .setSupportedActions(LAYOUT_EDITOR_SUPPORTED_ACTIONS)
                            .build()
                        },
                        NlComponentRegistrar,
                        { toolWindowDefinitions(it) },
                        AndroidEditorSettings.getInstance().globalState.preferredSurfaceState())

  private fun toolWindowDefinitions(facet: AndroidFacet): List<ToolWindowDefinition<DesignSurface<*>>> {
    val definitions = ImmutableList.builder<ToolWindowDefinition<DesignSurface<*>>>()

    definitions.add(PaletteDefinition(myProject, Side.LEFT, Split.TOP, AutoHide.DOCKED))
    definitions.add(NlPropertiesPanelDefinition(facet, Side.RIGHT, Split.TOP, AutoHide.DOCKED))
    if (StudioFlags.NELE_NEW_COMPONENT_TREE.get() && StudioFlags.USE_COMPONENT_TREE_TABLE.get()) {
      definitions.add(NlComponentTreeDefinition(myProject, Side.LEFT, Split.BOTTOM, AutoHide.DOCKED))
    } else {
      definitions.add(
        NlLegacyComponentTreeDefinition(
          myProject,
          Side.LEFT,
          Split.BOTTOM,
          AutoHide.DOCKED
        )
      )
    }
    return definitions.build()
  }

  override fun getName() = "Design"
}

fun AndroidEditorSettings.GlobalState.preferredSurfaceState() = when(preferredEditorMode) {
  AndroidEditorSettings.EditorMode.CODE -> DesignerEditorPanel.State.DEACTIVATED
  AndroidEditorSettings.EditorMode.SPLIT -> DesignerEditorPanel.State.SPLIT
  AndroidEditorSettings.EditorMode.DESIGN -> DesignerEditorPanel.State.FULL
  else -> DesignerEditorPanel.State.FULL // default
}
