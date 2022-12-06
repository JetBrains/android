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
package com.android.tools.idea.tests.gui.framework.fixture.properties

import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.ptable.impl.PTableImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.testFramework.runInEdtAndGet
import org.fest.swing.fixture.AbstractComponentFixture

/**
 * A properties section fixture.
 *
 * Currently this is just a holder of the element fixtures of the section.
 * More functionality may be added later.
 */
class SectionFixture(val title: CollapsibleLabelPanelFixture?) {
  val components = mutableListOf<AbstractComponentFixture<*, *, *>>()

  fun findEditorOf(attributeName: String): ActionButtonBindingFixture {
    val index = components.indexOfFirst { (it as? CollapsibleLabelPanelFixture)?.name == attributeName }
    return components[index + 1] as ActionButtonBindingFixture
  }

  fun expand() {
    title?.expand()
  }

  fun collapse() {
    title?.collapse()
  }

  fun getPTable(): PTableFixture {
    for (table in components) {
      if (table.toString().contains("PTableFixture")) {
        return table as PTableFixture
      }
    }
    throw AssertionError("PTableFixture not found in Declared Attributes Panel")
  }

  fun getPTableImpl(): PTableImpl {
    return getPTable().target() as PTableImpl
  }

  fun clickAddAttributeActionButton() {
    val buttonStringName = "Add"
    title?.clickActionButton(buttonStringName)
  }

  fun clickRemoveAttributeActionButton() {
    val buttonStringName = "Remove"
    title?.clickActionButton(buttonStringName)
  }
}
