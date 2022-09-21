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

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.ConstraintLayoutViewInspectorFixture
import com.android.tools.idea.tests.gui.framework.matcher.Matchers
import com.android.tools.idea.tests.gui.framework.waitUntilFound
import com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintPanel
import com.android.tools.idea.uibuilder.property.ui.EmptyTablePanel
import com.android.tools.idea.uibuilder.property.ui.HorizontalEditorPanel
import com.android.tools.property.panel.api.PROPERTIES_PANEL_NAME
import com.android.tools.property.panel.api.PropertiesPanel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.SelectedComponentPanel
import com.android.tools.property.panel.impl.model.TitleLineModel
import com.android.tools.property.panel.impl.ui.ActionButtonBinding
import com.android.tools.property.panel.impl.ui.CollapsibleLabelPanel
import com.android.tools.property.panel.impl.ui.GenericLinePanel
import com.android.tools.property.panel.impl.ui.PropertiesPage
import com.android.tools.property.ptable.impl.PTableImpl
import com.intellij.util.containers.addIfNotNull
import org.fest.swing.core.Robot
import org.fest.swing.fixture.AbstractComponentFixture
import org.fest.swing.fixture.JPanelFixture
import org.fest.swing.timing.Wait
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

  private val currentId: String?
    get() = (findHeader()?.components?.firstOrNull() as? SelectedComponentPanelFixture)?.id

  fun waitForId(id: String): PropertiesPanelFixture<P> {
    Wait.seconds(20)
      .expecting("properties panel to populate for $id currently shown: ${currentId}")
      .until { currentId == id }
    robot.waitForIdle()
    return this
  }

  /**
   * Find the header section of the properties panel.
   */
  fun findHeader(): SectionFixture? = mainSections.firstOrNull()

  fun findConstraintLayoutViewInspector(sectionName: String): ConstraintLayoutViewInspectorFixture {
    return waitUntilFound("until WidgetConstraintPanel is found in section: $sectionName",
                          { findSectionByName(sectionName)?.components?.firstOrNull() as? ConstraintLayoutViewInspectorFixture },
                          10)
  }

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
          is ActionButtonBinding -> addControl(it)
          is HorizontalEditorPanel -> addControl(it)
          else -> error("unexpected section: ${it}")
        }
      }
      endSection()
      return sections
    }

    private fun addLabel(label: CollapsibleLabelPanel) {
      val fixture = CollapsibleLabelPanelFixture(robot, label)
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
        child is SelectedComponentPanel -> addSelectedComponentPanel(child)
        child is PTableImpl -> addTable(child)
        child is EmptyTablePanel -> addEmptyTable(child)
        child is JPanel -> addGenericPanel(child)
        else -> error("Missing fixture mapping")
      }
    }

    private fun addSelectedComponentPanel(panel: SelectedComponentPanel) {
      addComponent(SelectedComponentPanelFixture(robot, panel))
    }

    private fun addTable(table: PTableImpl) {
      addComponent(PTableFixture(robot, table))
    }

    private fun addEmptyTable(table: EmptyTablePanel) {
      addComponent(EmptyTablePanelFixture(robot, table))
    }

    private fun addGenericPanel(panel: JPanel) {
      val first = panel.components.firstOrNull()
      if (first is WidgetConstraintPanel) {
        addComponent(ConstraintLayoutViewInspectorFixture(robot, first))
      }
      else {
        addComponent(JPanelFixture(robot, panel))
      }
    }

    private fun addControl(actionButtonBinding: ActionButtonBinding) {
      addComponent(ActionButtonBindingFixture(robot, actionButtonBinding))
    }

    private fun addControl(panel: HorizontalEditorPanel) {
      addComponent(HorizontalEditorPanelFixture(robot, panel))
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
    @JvmStatic
    fun <P : PropertyItem> findPropertiesPanelInContainer(container: Container, robot: Robot): PropertiesPanelFixture<P> {
      val panel = GuiTests.waitUntilFound<JPanel>(robot, container, Matchers.byName(JPanel::class.java, PROPERTIES_PANEL_NAME))
      @Suppress("UNCHECKED_CAST")
      val properties = panel.getClientProperty(PROPERTIES_PANEL_NAME) as PropertiesPanel<P>
      return PropertiesPanelFixture(properties, robot)
    }
  }
}
