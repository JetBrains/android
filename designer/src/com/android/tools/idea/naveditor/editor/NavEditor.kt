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
package com.android.tools.idea.naveditor.editor

import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.DesignerEditor
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.naveditor.property.NavPropertyPanelDefinition
import com.android.tools.idea.naveditor.structure.StructurePanel
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

private const val WORKBENCH_NAME = "NAV_EDITOR"

const val NAV_EDITOR_ID = "nav-designer"

open class NavEditor(file: VirtualFile, project: Project) : DesignerEditor(file, project) {

  override fun getEditorId() = NAV_EDITOR_ID

  override fun createEditorPanel() =
    DesignerEditorPanel(this, myProject, myFile, WorkBench<DesignSurface>(myProject, WORKBENCH_NAME, this),
                        { NavDesignSurface(myProject, it, this) },
                        {
                          ImmutableList.of(NavPropertyPanelDefinition(it, Side.RIGHT, Split.TOP, AutoHide.DOCKED),
                                           StructurePanel.StructurePanelDefinition())
                        })

  override fun getName() = "Design"
}
