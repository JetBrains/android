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
import com.intellij.ui.treeStructure.SimpleNode

abstract class AbstractPsResettableNode<T : PsModel> protected constructor(uiSettings: PsUISettings) : AbstractPsModelNode<T>(uiSettings) {

  private var myChildren: List<AbstractPsModelNode<*>>? = null

  init {
    autoExpandNode = true
  }

  override fun getChildren(): Array<SimpleNode> {
    if (myChildren == null) {
      myChildren = createChildren()
    }
    return myChildren!!.toTypedArray()
  }

  fun reset() {
    myChildren = null
  }

  protected abstract fun createChildren(): List<AbstractPsModelNode<*>>
}
