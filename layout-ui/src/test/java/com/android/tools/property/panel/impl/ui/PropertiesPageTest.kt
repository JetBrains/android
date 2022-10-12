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
package com.android.tools.property.panel.impl.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT
import com.android.testutils.MockitoCleanerRule
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.android.tools.property.panel.impl.model.GenericInspectorLineModel
import com.android.tools.property.panel.impl.model.SeparatorLineModel
import com.android.tools.property.panel.impl.model.TitleLineModel
import com.android.tools.property.panel.impl.model.util.FakePropertyItem
import com.android.tools.property.ptable.PTableModel
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import javax.swing.JComponent
import javax.swing.JLabel

class PropertiesPageTest {
  companion object {
    @JvmField
    @ClassRule
    val rule = ApplicationRule()
  }

  @get:Rule
  val cleaner = MockitoCleanerRule()

  private var disposable: Disposable? = null
  private var page: PropertiesPage? = null
  private var tableModel: PTableModel? = null
  private var tableUI: TableUIProvider? = null

  @Before
  @Suppress("UNCHECKED_CAST")
  fun setUp() {
    val controlTypeProvider = mock(ControlTypeProvider::class.java) as ControlTypeProvider<PropertyItem>
    val editorProvider = mock(EditorProvider::class.java) as EditorProvider<PropertyItem>
    disposable = Disposer.newDisposable()
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(PropertiesComponent::class.java, PropertiesComponentMock(),
                                                                         disposable!!)
    tableUI = TableUIProvider(controlTypeProvider, editorProvider)
    tableModel = mock(PTableModel::class.java)
    page = PropertiesPage(disposable!!)
    page!!.clear()
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable!!)
    tableUI = null
    tableModel = null
    disposable = null
    page = null
  }

  private val pageLines: List<InspectorLineModel>
    get() = page?.inspectorModel?.lines ?: error("No lines found")

  @Test
  fun testStartLeftFractionTakenFromProperties() {
    assertThat(page!!.nameColumnFraction.value).isEqualTo(0.4f)

    PropertiesComponent.getInstance().setValue(LEFT_FRACTION_KEY, 0.8f, 0.4f)
    val other = PropertiesPage(disposable!!)
    assertThat(other.nameColumnFraction.value).isEqualTo(0.8f)
  }

  @Test
  fun testStartLeftFractionSavedToProperties() {
    page!!.nameColumnFraction.value = 0.7f
    assertThat(PropertiesComponent.getInstance().getFloat(LEFT_FRACTION_KEY, 0.4f)).isEqualTo(0.7f)
  }

  @Test
  fun testSeparatorAddedBeforeFirstEditorComponent() {
    page!!.addEditor(makeEditor())

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,
                    CollapsibleLabelModel::class.java)
  }

  @Test
  fun testSeparatorAddedBeforeFirstCustomComponent() {
    page!!.addComponent(JLabel())

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,
                    GenericInspectorLineModel::class.java)
  }

  @Test
  fun testNoSeparatorAddedBeforeFirstTableComponent() {
    page!!.addTable(tableModel!!, false, tableUI!!, emptyList())

    checkLineModels(pageLines, TableLineModel::class.java)
  }

  @Test
  fun testNoSeparatorAddedBetweenTableAndTitle() {
    page!!.addTitle("Title1")
    page!!.addTable(tableModel!!, false, tableUI!!, emptyList())
    page!!.addSubTitle("SubTitle")

    checkLineModels(pageLines,
                    CollapsibleLabelModel::class.java,
                    TableLineModel::class.java,
                    CollapsibleLabelModel::class.java)
  }

  @Test
  fun testFlatTitleHasTwoSeparators() {
    page!!.addEditor(makeEditor())
    page!!.addTitle("Title1")
    page!!.addEditor(makeEditor())

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,      // Top separator
                    CollapsibleLabelModel::class.java,   // Editor
                    SeparatorLineModel::class.java,      // Separator before title
                    TitleLineModel::class.java,          // Title
                    SeparatorLineModel::class.java,      // Separator after title
                    CollapsibleLabelModel::class.java)   // Editor
  }

  @Test
  fun testTitleGroupHasTwoSeparators() {
    page!!.addEditor(makeEditor())
    val title = page!!.addTitle("Title1") as TitleLineModel
    title.makeExpandable(true)
    page!!.addEditor(makeEditor(), title)

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,      // Top separator
                    CollapsibleLabelModel::class.java,   // Editor
                    SeparatorLineModel::class.java,      // Separator before title
                    TitleLineModel::class.java,          // Title
                    SeparatorLineModel::class.java,      // Separator after title
                    CollapsibleLabelModel::class.java)   // Editor

    checkLineModels(title.children,
                    SeparatorLineModel::class.java,      // Separator after title
                    CollapsibleLabelModel::class.java)   // Editor
  }

  @Test
  fun testTitleGroupWithTableHasOneSeparator() {
    page!!.addEditor(makeEditor())
    val title = page!!.addTitle("Title1") as TitleLineModel
    title.makeExpandable(true)
    page!!.addTable(tableModel!!, false, tableUI!!, emptyList(), title)

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,
                    CollapsibleLabelModel::class.java,
                    SeparatorLineModel::class.java,
                    TitleLineModel::class.java,
                    TableLineModel::class.java)

    checkLineModels(title.children,
                    TableLineModel::class.java)
  }

  @Test
  fun testTitleWithPriorTitleGroupHasSeparatorInPreviousGroup() {
    page!!.addEditor(makeEditor())
    val title1 = page!!.addTitle("Title1") as TitleLineModel
    title1.makeExpandable(true)
    page!!.addEditor(makeEditor(), title1)
    page!!.addTitle("Title2")

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,     // Separator before editor
                    CollapsibleLabelModel::class.java,  // Editor
                    SeparatorLineModel::class.java,     // Separator before first title
                    TitleLineModel::class.java,         // First Title
                    SeparatorLineModel::class.java,     // Separator after first title
                    CollapsibleLabelModel::class.java,  // Editor
                    SeparatorLineModel::class.java,     // Separator before second title
                    TitleLineModel::class.java)         // Second title

    checkLineModels(title1.children,
                    SeparatorLineModel::class.java,     // Separator after first title
                    CollapsibleLabelModel::class.java,  // Editor
                    SeparatorLineModel::class.java)     // Separator before second title, must be in previous group
  }

  @Test
  fun testTitleWithPriorEditorGroupHasFixedSeparator() {
    val editor1 = page!!.addEditor(makeEditor()) as CollapsibleLabelModel
    editor1.makeExpandable(true)
    page!!.addEditor(makeEditor(), editor1)
    page!!.addTitle("Title")

    checkLineModels(pageLines,
                    SeparatorLineModel::class.java,     // Separator before first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    CollapsibleLabelModel::class.java,  // Second editor
                    SeparatorLineModel::class.java,     // Separator before second title
                    TitleLineModel::class.java)         // Title

    checkLineModels(editor1.children,
                    CollapsibleLabelModel::class.java)  // Second editor
                                                        // The separator before the title should NOT be in this group !
  }

  @Test
  fun testTitleWithNestedEditorGroupsHasSeparatorOnFirstGroup() {
    val title1 = page!!.addTitle("Title1") as TitleLineModel
    title1.makeExpandable(true)
    val editor1 = page!!.addEditor(makeEditor(), title1) as CollapsibleLabelModel
    editor1.makeExpandable(true)
    val editor2 = page!!.addEditor(makeEditor(), editor1) as CollapsibleLabelModel
    editor2.makeExpandable(true)
    val editor3 = page!!.addEditor(makeEditor(), editor2) as CollapsibleLabelModel
    editor3.makeExpandable(true)
    page!!.addEditor(makeEditor(), editor3) as CollapsibleLabelModel
    page!!.addTitle("Title2")

    checkLineModels(pageLines,
                    TitleLineModel::class.java,         // First title
                    SeparatorLineModel::class.java,     // Separator after first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    CollapsibleLabelModel::class.java,  // Second editor
                    CollapsibleLabelModel::class.java,  // Third editor
                    CollapsibleLabelModel::class.java,  // Forth editor
                    SeparatorLineModel::class.java,     // Separator before second title
                    TitleLineModel::class.java)         // Second title

    checkLineModels(title1.children,
                    SeparatorLineModel::class.java,     // Separator after first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    SeparatorLineModel::class.java)     // Separator before second title
  }

  @Test
  fun testSubTitle() {
    val title1 = page!!.addTitle("Title1") as TitleLineModel
    title1.makeExpandable(true)
    page!!.addEditor(makeEditor(), title1)
    val subtitle1 = page!!.addSubTitle("SubTitle1", parent = title1) as TitleLineModel
    page!!.addEditor(makeEditor(), subtitle1)
    page!!.addEditor(makeEditor(), subtitle1)
    val title2 = page!!.addTitle("Title1") as TitleLineModel
    title2.makeExpandable(true)

    checkLineModels(pageLines,
                    TitleLineModel::class.java,         // First title
                    SeparatorLineModel::class.java,     // Separator before first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    SeparatorLineModel::class.java,     // Separator after first editor
                    TitleLineModel::class.java,         // First subtitle
                    SeparatorLineModel::class.java,     // Separator before second editor
                    CollapsibleLabelModel::class.java,  // Second editor
                    CollapsibleLabelModel::class.java,  // Third editor
                    SeparatorLineModel::class.java,     // Separator after third editor
                    TitleLineModel::class.java)         // Second title

    checkLineModels(title1.children,
                    SeparatorLineModel::class.java,     // Separator before first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    SeparatorLineModel::class.java,     // Separator after first editor
                    TitleLineModel::class.java)         // First subtitle

    checkLineModels(subtitle1.children,
                    SeparatorLineModel::class.java,     // Separator before second editor
                    CollapsibleLabelModel::class.java,  // Second editor
                    CollapsibleLabelModel::class.java,  // Third editor
                    SeparatorLineModel::class.java)     // Separator after third editor  (bug: missing)
  }

  @Test
  fun testSubTitleFollowedByEditor() {
    val title1 = page!!.addTitle("Title1") as TitleLineModel
    title1.makeExpandable(true)
    page!!.addEditor(makeEditor(), title1)
    val subtitle1 = page!!.addSubTitle("SubTitle1", parent = title1) as TitleLineModel
    page!!.addEditor(makeEditor(), subtitle1)
    page!!.addEditor(makeEditor(), title1)   // <= Note: this editor has title1 as parent !!
    val title2 = page!!.addTitle("Title1") as TitleLineModel
    title2.makeExpandable(true)

    checkLineModels(pageLines,
                    TitleLineModel::class.java,         // First title
                    SeparatorLineModel::class.java,     // Separator before first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    SeparatorLineModel::class.java,     // Separator after first editor
                    TitleLineModel::class.java,         // First subtitle
                    SeparatorLineModel::class.java,     // Separator before second editor
                    CollapsibleLabelModel::class.java,  // Second editor
                    SeparatorLineModel::class.java,     // Separator before third editor
                    CollapsibleLabelModel::class.java,  // Third editor
                    SeparatorLineModel::class.java,     // Separator after third editor
                    TitleLineModel::class.java)         // Second title

    checkLineModels(title1.children,
                    SeparatorLineModel::class.java,     // Separator before first editor
                    CollapsibleLabelModel::class.java,  // First editor
                    SeparatorLineModel::class.java,     // Separator after first editor
                    TitleLineModel::class.java,         // First subtitle
                    SeparatorLineModel::class.java,     // Separator before third editor
                    CollapsibleLabelModel::class.java,  // Third editor
                    SeparatorLineModel::class.java)     // Separator after third editor

    checkLineModels(subtitle1.children,
                    SeparatorLineModel::class.java,     // Separator before second editor
                    CollapsibleLabelModel::class.java)  // Second editor
  }

  private fun checkLineModels(lines: List<InspectorLineModel>?, vararg classes: Class<*>) {
    val linesToCheck = lines ?: error("Expected non null lines")
    for ((index, line) in linesToCheck.withIndex()) {
      assertThat(line).isInstanceOf(classes[index])
    }
    assertThat(lines).hasSize(classes.size)
  }

  private fun makeEditor(): Pair<PropertyEditorModel, JComponent> {
    val model = mock(PropertyEditorModel::class.java)
    val editor = JLabel()
    val property = FakePropertyItem(ANDROID_URI, ATTR_TEXT, "Hello")
    whenever(model.property).thenReturn(property)
    return Pair(model, editor)
  }
}
