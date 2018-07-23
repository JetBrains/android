/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.treeStructure.SimpleNode

internal class TargetConfigurationNode(
  configuration: Configuration,
  uiSettings: PsUISettings
) : AbstractPsNode(uiSettings){
  init {
    myName = configuration.name
    icon = configuration.icon
  }

  override fun getChildren(): Array<SimpleNode> = SimpleNode.NO_CHILDREN

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    presentation.clearText()
    presentation.addText(myName, REGULAR_ATTRIBUTES)
  }
}
