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
package com.android.tools.property.panel.api

import com.android.tools.property.panel.impl.model.util.FakeInspectorBuilder
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.panel.impl.model.util.FakePropertyModel
import com.android.tools.property.panel.impl.ui.WatermarkPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel
import javax.swing.JTabbedPane

class PropertiesPanelTest {

  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  @get:Rule val disposableRule = DisposableRule()

  private var model1: FakePropertyModel? = null
  private var model2: FakePropertyModel? = null
  private var view1: PropertiesView<FakePropertyItem>? = null
  private var view2: PropertiesView<FakePropertyItem>? = null
  private var tab1a: PropertiesViewTab<FakePropertyItem>? = null
  private var tab1b: PropertiesViewTab<FakePropertyItem>? = null
  private var tab2a: PropertiesViewTab<FakePropertyItem>? = null
  private var tab2b: PropertiesViewTab<FakePropertyItem>? = null
  private var tab2c: PropertiesViewTab<FakePropertyItem>? = null
  private var builder1: FakeInspectorBuilder? = null
  private var builder2: FakeInspectorBuilder? = null
  private var builder1a: FakeInspectorBuilder? = null
  private var builder1b: FakeInspectorBuilder? = null
  private var builder2a: FakeInspectorBuilder? = null
  private var builder2b: FakeInspectorBuilder? = null
  private var builder2c: FakeInspectorBuilder? = null

  @Suppress("UNCHECKED_CAST")
  @Before
  fun setUp() {
    model1 = FakePropertyModel()
    model2 = FakePropertyModel()
    view1 = PropertiesView("Layout Editor", model1!!)
    tab1a = view1!!.addTab("Basic")
    tab1b = view1!!.addTab("Advanced")
    view2 = PropertiesView("Navigation Editor", model2!!)
    tab2a = view2!!.addTab("Simple")
    tab2b = view2!!.addTab("Extra")
    tab2c = view2!!.addTab("Last")
    builder1 = FakeInspectorBuilder()
    builder2 = FakeInspectorBuilder()
    builder1a = FakeInspectorBuilder()
    builder1b = FakeInspectorBuilder()
    builder2a = FakeInspectorBuilder()
    builder2b = FakeInspectorBuilder()
    builder2c = FakeInspectorBuilder()
    view1!!.main.builders.add(builder1!!)
    view2!!.main.builders.add(builder2!!)
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
    builder1 = null
    builder2 = null
    builder1a = null
    builder1b = null
    builder2a = null
    builder2b = null
  }

  @Test
  fun testTwoTabsVisible() {
    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    model1!!.propertiesGenerated()

    checkBothTabsVisibleInView1(panel)
  }

  @Test
  fun testSwitchBetweenViews() {
    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    model1!!.propertiesGenerated()
    checkBothTabsVisibleInView1(panel)

    // Now switch to the other view:
    model2!!.propertiesGenerated()
    checkAllThreeTabsVisibleInView2(panel)

    // Last switch back to the first view:
    model1!!.propertiesGenerated()
    checkBothTabsVisibleInView1(panel, 2)
  }

  @Test
  fun testPreferredTabOpened() {
    val properties = PropertiesComponent.getInstance()
    properties.setValue("android.last.property.tab.Layout Editor", "Advanced")
    properties.setValue("android.last.property.tab.Navigation Editor", "Last")

    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    model1!!.propertiesGenerated()
    assertThat(panel.selectedTab()).isEqualTo("Advanced")

    model2!!.propertiesGenerated()
    assertThat(panel.selectedTab()).isEqualTo("Last")
  }

  @Test
  fun testChangingTabsUpdatesThePreferredTab() {
    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view2!!)
    model2!!.propertiesGenerated()
    val tabs = panel.component.getComponent(1) as JTabbedPane
    assertThat(tabs.tabCount).isEqualTo(3)
    val properties = PropertiesComponent.getInstance()

    tabs.selectedIndex = 2
    assertThat(properties.getValue("android.last.property.tab.Navigation Editor")).isEqualTo("Last")

    tabs.selectedIndex = 0
    assertThat(properties.getValue("android.last.property.tab.Navigation Editor"))
      .isEqualTo("Simple")

    tabs.selectedIndex = 1
    assertThat(properties.getValue("android.last.property.tab.Navigation Editor"))
      .isEqualTo("Extra")
  }

  @Test
  fun testFilterHidesNonSearchableTabs() {
    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    tab1a!!.searchable = false
    model1!!.propertiesGenerated()
    panel.filter = "abc"

    assertThat(panel.pages.size).isEqualTo(2)
    assertThat(builder1!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder1a!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder1b!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder2!!.attachToInspectorCalled).isEqualTo(0)
    assertThat(builder2a!!.attachToInspectorCalled).isEqualTo(0)
    assertThat(builder2b!!.attachToInspectorCalled).isEqualTo(0)
    assertThat(builder2c!!.attachToInspectorCalled).isEqualTo(0)
    assertThat(panel.component.getComponent(0)).isEqualTo(panel.mainPage.component)
    assertThat(panel.component.getComponent(1)).isEqualTo(panel.pages[1].component)
    val hidden = panel.component.getComponent(2) as JPanel
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(3)
    assertThat(hidden.getComponent(0)).isEqualTo(panel.pages[0].component)
    assertThat(hidden.getComponent(1)).isInstanceOf(JTabbedPane::class.java)
    assertThat(hidden.getComponent(2)).isInstanceOf(WatermarkPanel::class.java)
  }

  @Test
  fun testOneTabNotApplicable() {
    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    builder1a!!.applicable = false
    model1!!.propertiesGenerated()
    assertThat(panel.pages.size).isEqualTo(2)
    assertThat(builder1!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder1a!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder1b!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(panel.component.componentCount).isEqualTo(3)
    assertThat(panel.component.getComponent(0)).isEqualTo(panel.mainPage.component)
    assertThat(panel.component.getComponent(1)).isEqualTo(panel.pages[1].component)
    val hidden = panel.component.getComponent(2) as JPanel
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(3)
    assertThat(hidden.getComponent(0)).isEqualTo(panel.pages[0].component)
    assertThat(hidden.getComponent(1)).isInstanceOf(JTabbedPane::class.java)
    assertThat(hidden.getComponent(2)).isInstanceOf(WatermarkPanel::class.java)
  }

  @Test
  fun testNothingApplicable() {
    val panel = PropertiesPanel<FakePropertyItem>(disposableRule.disposable)
    panel.addView(view1!!)
    panel.addView(view2!!)
    builder1!!.applicable = false
    builder1a!!.applicable = false
    builder1b!!.applicable = false
    model1!!.propertiesGenerated()
    assertThat(panel.pages.size).isEqualTo(2)
    assertThat(builder1!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder1a!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(builder1b!!.attachToInspectorCalled).isEqualTo(1)
    assertThat(panel.component.componentCount).isEqualTo(2)
    assertThat(panel.component.components[0]).isInstanceOf(WatermarkPanel::class.java)
    val hidden = panel.component.components[1] as JPanel
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(4)
    assertThat(hidden.getComponent(0)).isEqualTo(panel.mainPage.component)
    assertThat(hidden.getComponent(1)).isEqualTo(panel.pages[0].component)
    assertThat(hidden.getComponent(2)).isEqualTo(panel.pages[1].component)
    assertThat(hidden.getComponent(3)).isInstanceOf(JTabbedPane::class.java)
  }

  private fun checkBothTabsVisibleInView1(
    panel: PropertiesPanel<FakePropertyItem>,
    expectedCallCount: Int = 1,
  ) {
    assertThat(panel.pages.size).isEqualTo(2)
    assertThat(builder1!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    assertThat(builder1a!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    assertThat(builder1b!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    val main = panel.component.getComponent(0)
    val tabs = panel.component.getComponent(1) as JTabbedPane
    val hidden = panel.component.getComponent(2) as JPanel
    assertThat(main).isSameAs(panel.mainPage.component)
    assertThat(main.isVisible).isTrue()
    assertThat(tabs.isVisible).isTrue()
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(1)
    assertThat(hidden.getComponent(0)).isInstanceOf(WatermarkPanel::class.java)
    assertThat(tabs.tabCount).isEqualTo(2)
    assertThat(tabs.getTitleAt(0)).isEqualTo("Basic")
    assertThat(tabs.getTitleAt(1)).isEqualTo("Advanced")
  }

  private fun checkAllThreeTabsVisibleInView2(
    panel: PropertiesPanel<FakePropertyItem>,
    expectedCallCount: Int = 1,
  ) {
    assertThat(panel.pages.size).isEqualTo(3)
    assertThat(builder2!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    assertThat(builder2a!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    assertThat(builder2b!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    assertThat(builder2c!!.attachToInspectorCalled).isEqualTo(expectedCallCount)
    val main = panel.component.getComponent(0)
    val tabs = panel.component.getComponent(1) as JTabbedPane
    val hidden = panel.component.getComponent(2) as JPanel
    assertThat(main).isSameAs(panel.mainPage.component)
    assertThat(main.isVisible).isTrue()
    assertThat(tabs.isVisible).isTrue()
    assertThat(hidden.isVisible).isFalse()
    assertThat(hidden.componentCount).isEqualTo(1)
    assertThat(hidden.getComponent(0)).isInstanceOf(WatermarkPanel::class.java)
    assertThat(tabs.tabCount).isEqualTo(3)
    assertThat(tabs.getTitleAt(0)).isEqualTo("Simple")
    assertThat(tabs.getTitleAt(1)).isEqualTo("Extra")
    assertThat(tabs.getTitleAt(2)).isEqualTo("Last")
  }
}
