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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.model.PsModel
import com.intellij.ide.projectView.PresentationData
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import javax.swing.Icon

interface PsModelNode<T: PsModel> {
  val models: List<T>
  val firstModel: T get() = models.first()
  fun nameOf(model: T): String = model.name

  fun buildName(): String = models.map { nameOf(it) }.distinct().joinToString(", ")
  fun buildIcon(): Icon?  = models.firstOrNull()?.icon
}

abstract class AbstractPsModelNode<T : PsModel> : AbstractPsNode, PsModelNode<T> {

  protected constructor(parent: AbstractPsNode, uiSettings: PsUISettings) : super(parent, uiSettings)
  protected constructor(uiSettings: PsUISettings) : super(uiSettings)

  protected fun updateNameAndIcon() {
    myName = buildName()
    icon = buildIcon()
  }

  override fun doUpdate(presentation: PresentationData) {
    presentation.clearText()
    presentation.addText(myName, REGULAR_ATTRIBUTES)
  }
}
