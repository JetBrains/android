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

import com.android.tools.idea.layoutinspector.ui.SelectedViewPanel
import com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.property.panel.api.PROPERTIES_PANEL_NAME
import com.android.tools.property.panel.api.PropertiesPanel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.impl.model.TitleLineModel
import com.android.tools.property.panel.impl.ui.CollapsibleLabelPanel
import com.android.tools.property.panel.impl.ui.GenericLinePanel
import com.android.tools.property.panel.impl.ui.PropertiesPage
import com.android.tools.property.ptable2.impl.PTableImpl
import com.intellij.util.containers.addIfNotNull
import org.fest.swing.core.Robot
import org.fest.swing.fixture.AbstractComponentFixture
import java.awt.Container
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * A fixture for a [PropertiesPanel]
 */
class PropertiesPanelFixture<P : PropertyItem>(private val propertiesPanel: PropertiesPanel<P>, val robot: Robot) {

  private val mainSections
    get() = findSections(propertiesPanel.mainPage)

  private val scrollableSections
    get() = propertiesPanel.pages.firstOrNull()?.let { findSections(it) } ?: listOf()

  /**
   * Find the header section of the properties panel.
   */
  fun findHeader(): SectionFixture? = mainSections.firstOrNull()

  /**
   * Find a section in the properties panel.
   *
   * This will be null if the section doesn't currently exist.
   */
  fun findSectionByName(name: String): SectionFixture? =
    (mainSections union scrollableSections).firstOrNull { it.title?.name == name }

  private fun findSections(page: PropertiesPage): List<SectionFixture> {
    return SectionBuilder(page, robot).build()
  }

  private class SectionBuilder(private val page: PropertiesPage, private val robot: Robot) {
    private val sections = mutableListOf<SectionFixture>()
    private var section: SectionFixture? = null

    fun build(): List<SectionFixture> {
      val scrollPane = page.component as JScrollPane
      val inspector = scrollPane.viewport.view as JComponent
      inspector.components.forEach {
        when (it) {
          is CollapsibleLabelPanel -> addLabel(it)
          is GenericLinePanel -> addPanel(it)
          is PTableImpl -> addTable(it)
          else -> error("unexpected section: ${it}")
        }
      }
      endSection()
      return sections
    }

    private fun addLabel(label: CollapsibleLabelPanel) {
      val fixture = CollapsibleLabelPanelFixture(label, robot)
      if (label.model is TitleLineModel) {
        endSection()
        section = SectionFixture(fixture)
      }
      else {
        addComponent(fixture)
      }
    }

    private fun addPanel(panel: GenericLinePanel) {
      val child = panel.components.singleOrNull() ?: return
      when {
        child is JPanel && child.componentCount == 0 -> return // ignore spacers
        child is SelectedViewPanel -> addComponent(SelectedViewPanelFixture(child, robot))
        child is PTableImpl -> addTable(child)
        else -> error("Missing fixture mapping")
      }
    }

    private fun addTable(table: PTableImpl) {
      addComponent(PTableFixture(robot, table))
    }

    private fun addComponent(fixture: AbstractComponentFixture<*, *, *>) {
      val current = section ?: SectionFixture(null)
      current.components.add(fixture)
      section = current
    }

    private fun endSection() {
      sections.addIfNotNull(section)
      section = null
    }
  }

  companion object {

    /**
     * Use this method to create this fixture.
     *
     * Since the properties panel is used several places a [Container] must be supplied.
     */
    fun <P : PropertyItem> findPropertiesPanelInContainer(container: Container, robot: Robot): PropertiesPanelFixture<P> {
      val panel = waitUntilFound<JPanel>(robot, container, Matchers.byName(JPanel::class.java, PROPERTIES_PANEL_NAME))
      @Suppress("UNCHECKED_CAST")
      val properties = panel.getClientProperty(PROPERTIES_PANEL_NAME) as PropertiesPanel<P>
      return PropertiesPanelFixture(properties, robot)
    }
  }
}
