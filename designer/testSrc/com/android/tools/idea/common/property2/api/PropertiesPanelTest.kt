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
package com.android.tools.idea.common.property2.api

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import javax.swing.JPanel
import javax.swing.JTabbedPane

class PropertiesPanelTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.inMemory()

  private var properties: PropertiesComponent? = null
  private var model1: FakeModel? = null
  private var model2: FakeModel? = null
  private var view1: PropertiesView<FakeProperty>? = null
  private var view2: PropertiesView<FakeProperty>? = null
  private var tab1a: PropertiesViewTab<FakeProperty>? = null
  private var tab1b: PropertiesViewTab<FakeProperty>? = null
  private var tab2a: PropertiesViewTab<FakeProperty>? = null
  private var tab2b: PropertiesViewTab<FakeProperty>? = null
  private var tab2c: PropertiesViewTab<FakeProperty>? = null
  private var builder1a: InspectorBuilder<FakeProperty>? = null
  private var builder1b: InspectorBuilder<FakeProperty>? = null
  private var builder2a: InspectorBuilder<FakeProperty>? = null
  private var builder2b: InspectorBuilder<FakeProperty>? = null
  private var builder2c: InspectorBuilder<FakeProperty>? = null

  @Suppress("UNCHECKED_CAST")
  @Before
  fun setUp() {
    properties = PropertiesComponentMock()
    projectRule.replaceService(PropertiesComponent::class.java, properties!!)
    model1 = FakeModel()
    model2 = FakeModel()
    view1 = PropertiesView("Layout Editor", model1!!)
    tab1a = view1!!.addTab("Basic")
    tab1b = view1!!.addTab("Advanced")
    view2 = PropertiesView("Navigation Editor", model2!!)
    tab2a = view2!!.addTab("Simple")
    tab2b = view2!!.addTab("Extra")
    tab2c = view2!!.addTab("Last")
    builder1a = mock(InspectorBuilder::class.java) as InspectorBuilder<FakeProperty>
    builder1b = mock(InspectorBuilder::class.java) as InspectorBuilder<FakeProperty>
    builder2a = mock(InspectorBuilder::class.java) as InspectorBuilder<FakeProperty>
    builder2b = mock(InspectorBuilder::class.java) as InspectorBuilder<FakeProperty>
    builder2c = mock(InspectorBuilder::class.java) as InspectorBuilder<FakeProperty>
    tab1a!!.builders.add(builder1a!!)
    tab1b!!.builders.add(builder1b!!)
    tab2a!!.builders.add(builder2a!!)
    tab2b!!.builders.add(builder2b!!)
    tab2c!!.builders.add(builder2c!!)
  }

  @After
  fun tearDown() {
    model1 = null
    model2 = null
    view1 = null
    view2 = null
    tab1a = null
    tab1b = null
    tab2a = null
    tab2b = null
    builder1a = null
    builder1b= null
    builder2a = null
    builder2b = null
  }

  fun <T> any(): T = ArgumentMatchers.any() as T
  fun <T> eq(arg: T): T = ArgumentMatchers.eq(arg) as T

  @Test
  fun testTwoTabsVisible() {
    val panel = PropertiesPanel(projectRule.fixture.testRootDisposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    model1!!.propertiesGenerated()

    checkBothTabsVisibleInView1(panel)
  }

  @Test
  fun testSwitchBetweenViews() {
    val panel = PropertiesPanel(projectRule.fixture.testRootDisposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    model1!!.propertiesGenerated()
    checkBothTabsVisibleInView1(panel)

    // Now switch to the other view:
    model2!!.propertiesGenerated()
    checkAllThreeTabsVisibleInView2(panel)

    // Last switch back to the first view:
    model1!!.propertiesGenerated()
    checkBothTabsVisibleInView1(panel)
  }

  @Test
  fun testPreferredTabOpened() {
    properties!!.setValue("android.last.property.tab.Layout Editor", "Advanced")
    properties!!.setValue("android.last.property.tab.Navigation Editor", "Last")

    val panel = PropertiesPanel(projectRule.fixture.testRootDisposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    model1!!.propertiesGenerated()
    assertThat(panel.selectedTab()).isEqualTo("Advanced")

    model2!!.propertiesGenerated()
    assertThat(panel.selectedTab()).isEqualTo("Last")
  }

  @Test
  fun testChangingTabsUpdatesThePreferredTab() {
    val panel = PropertiesPanel(projectRule.fixture.testRootDisposable)
    panel.addView(view2!!)
    model2!!.propertiesGenerated()
    val tabs = panel.component.getComponent(0) as JTabbedPane

    tabs.selectedIndex = 2
    assertThat(properties!!.getValue("android.last.property.tab.Navigation Editor")).isEqualTo("Last")

    tabs.selectedIndex = 0
    assertThat(properties!!.getValue("android.last.property.tab.Navigation Editor")).isEqualTo("Simple")

    tabs.selectedIndex = 1
    assertThat(properties!!.getValue("android.last.property.tab.Navigation Editor")).isEqualTo("Extra")
  }

  @Test
  fun testFilterHidesNonSearchableTabs() {
    val panel = PropertiesPanel(projectRule.fixture.testRootDisposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    tab1a!!.searchable = false
    model1!!.propertiesGenerated()
    panel.filter = "abc"

    assertThat(panel.pages.size).isEqualTo(2)
    verify(builder1a!!).attachToInspector(eq(panel.pages[0]), any())
    verify(builder1b!!).attachToInspector(eq(panel.pages[1]), any())
    assertThat(panel.component.getComponent(0)).isEqualTo(panel.pages[1].component)
    val hidden = panel.component.getComponent(1) as JPanel
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(2)
    assertThat(hidden.getComponent(0)).isEqualTo(panel.pages[0].component)
    assertThat(hidden.getComponent(1)).isInstanceOf(JTabbedPane::class.java)
  }

  private fun checkBothTabsVisibleInView1(panel: PropertiesPanel) {
    assertThat(panel.pages.size).isEqualTo(2)
    verify(builder1a!!, atLeastOnce()).attachToInspector(eq(panel.pages[0]), any())
    verify(builder1b!!, atLeastOnce()).attachToInspector(eq(panel.pages[1]), any())
    val tabs = panel.component.getComponent(0) as JTabbedPane
    val hidden = panel.component.getComponent(1) as JPanel
    assertThat(tabs.isVisible).isTrue()
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(0)
    assertThat(tabs.tabCount).isEqualTo(2)
    assertThat(tabs.getTitleAt(0)).isEqualTo("Basic")
    assertThat(tabs.getTitleAt(1)).isEqualTo("Advanced")
  }

  private fun checkAllThreeTabsVisibleInView2(panel: PropertiesPanel) {
    assertThat(panel.pages.size).isEqualTo(3)
    verify(builder2a!!).attachToInspector(eq(panel.pages[0]), any())
    verify(builder2b!!).attachToInspector(eq(panel.pages[1]), any())
    verify(builder2c!!).attachToInspector(eq(panel.pages[2]), any())
    val tabs = panel.component.getComponent(0) as JTabbedPane
    val hidden = panel.component.getComponent(1) as JPanel
    assertThat(tabs.isVisible).isTrue()
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(0)
    assertThat(tabs.tabCount).isEqualTo(3)
    assertThat(tabs.getTitleAt(0)).isEqualTo("Simple")
    assertThat(tabs.getTitleAt(1)).isEqualTo("Extra")
    assertThat(tabs.getTitleAt(2)).isEqualTo("Last")
  }
}

private class FakeProperty(override val name: String): PropertyItem {
  override val namespace: String
    get() = ANDROID_URI
  override var value: String? = "Value"
  override val isReference = false
}

private class FakeModel: PropertiesModel<FakeProperty> {

  override val properties: PropertiesTable<FakeProperty>
    get() = PropertiesTable.emptyTable()

  override fun deactivate() {
  }

  private val listeners = mutableListOf<PropertiesModelListener>()

  override fun addListener(listener: PropertiesModelListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: PropertiesModelListener) {
    listeners.remove(listener)
  }

  fun propertiesGenerated() {
    listeners.forEach { it.propertiesGenerated(this) }
  }
}
