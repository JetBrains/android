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
package com.android.tools.idea.layoutinspector.ui

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ELEVATION
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.adtui.swing.PortableUiFontRule
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ResolutionStackModel
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertiesModel
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.util.CheckUtil
import com.android.tools.idea.layoutinspector.util.ComponentUtil.flatten
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableSupport
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.JBColor
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Box.Filler
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LookAndFeel
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.swing.plaf.metal.MetalTheme
import com.android.tools.idea.layoutinspector.properties.PropertyType as Type

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/ui"
private const val DIFF_THRESHOLD = 0.01

@RunsInEdt
class ResolutionElementEditorTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule)
    .around(IntelliJLafRule())
    .around(PortableUiFontRule())
    .around(EdtRule())
    .around(IconLoaderRule())!!

  @Test
  fun testPaintClosed() {
    val editors = createEditors()
    getEditor(editors, 1).isVisible = false
    checkImage(editors, "Closed")
  }

  @Test
  fun testPaintOpen() {
    val editors = createEditors()
    checkImage(editors, "Open")
  }

  @Test
  fun testPaintOpenWithDetails() {
    val editors = createEditors()
    getEditor(editors, 0).editorModel.isExpandedTableItem = true
    expandFirstLabel(getEditor(editors, 0), true)
    checkImage(editors, "OpenWithDetails")
  }

  @Test
  fun testPaintOpenWithTwoDetails() {
    val editors = createEditors()
    getEditor(editors, 0).editorModel.isExpandedTableItem = true
    expandFirstLabel(getEditor(editors, 0), true)
    expandFirstLabel(getEditor(editors, 1), true)
    checkImage(editors, "OpenWithTwoDetails")
  }

  @Test
  fun testDynamicHeight() {
    var updateCount = 0
    val editors = createEditors()
    val editor = getEditor(editors, 0)
    val model = editor.editorModel
    model.tableSupport = object : TableSupport {
      override fun updateRowHeight(scrollIntoView: Boolean) {
        updateCount++
      }
    }
    assertThat(model.isCustomHeight).isFalse()

    editor.editorModel.isExpandedTableItem = true
    assertThat(model.isCustomHeight).isTrue()
    assertThat(updateCount).isEqualTo(0)

    expandFirstLabel(editor, true)
    assertThat(model.isCustomHeight).isTrue()
    assertThat(updateCount).isEqualTo(1)

    expandFirstLabel(editor, false)
    assertThat(model.isCustomHeight).isTrue()
    assertThat(updateCount).isEqualTo(2)

    editor.editorModel.isExpandedTableItem = false
    assertThat(model.isCustomHeight).isFalse()
    assertThat(updateCount).isEqualTo(2)
  }

  @Test
  fun testHasLinkPanel() {
    val model = model(projectRule.project, FakeTreeSettings(), body = DemoExample.setUpDemo(projectRule.fixture))
    val node = model["title"]!!
    val item1 = InspectorPropertyItem(
      ANDROID_URI, ATTR_TEXT_COLOR, ATTR_TEXT_COLOR, Type.COLOR, null, PropertySection.DECLARED, node.layout, node.drawId, model)
    val item2 = InspectorPropertyItem(
      ANDROID_URI, ATTR_ELEVATION, ATTR_ELEVATION, Type.FLOAT, null, PropertySection.DEFAULT, null, node.drawId, model)

    // The "textColor" attribute is defined in the layout file, and we should have a link to the layout definition
    assertThat(ResolutionElementEditor.hasLinkPanel(item1)).isTrue()

    // The "elevation" attribute is never set so there will not be a link to follow:
    assertThat(ResolutionElementEditor.hasLinkPanel(item2)).isFalse()
  }

  @Test
  fun testDoubleClick() {
    val editors = createEditors()
    val editor = getEditor(editors, 0)
    var toggleCount = 0
    val model = editor.editorModel
    model.tableSupport = object : TableSupport {
      override fun toggleGroup() {
        toggleCount++
      }
    }
    val textEditor = (editor.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER)
    editor.size = Dimension(500, 200)
    editor.doLayout()
    val ui = FakeUi(textEditor)
    ui.mouse.doubleClick(200, 100)
    assertThat(toggleCount).isEqualTo(1)
    ui.mouse.doubleClick(200, 100)
    assertThat(toggleCount).isEqualTo(2)
  }

  private fun checkImage(editors: JPanel, expected: String) {
    editors.setBounds(0, 0, 200, 300)
    val ui = FakeUi(editors)
    val generatedImage = ui.render()
    CheckUtil.assertImageSimilarPerPlatform(TestUtils.resolveWorkspacePathUnchecked(TEST_DATA_PATH), "testResolutionEditorPaint$expected",
                                            generatedImage, DIFF_THRESHOLD)
  }

  private fun expandFirstLabel(editor: ResolutionElementEditor, open: Boolean) {
    val keyStroke = if (open) KeyStrokes.RIGHT else KeyStrokes.LEFT
    val link = findFirstLinkComponent(editor)!!
    val action = link.getActionForKeyStroke(keyStroke)!!
    val event = ActionEvent(link, ActionEvent.ACTION_PERFORMED, "open")
    action.actionPerformed(event)
  }

  private fun createEditors(): JPanel {
    val model = model(projectRule.project, FakeTreeSettings(), body = DemoExample.setUpDemo(projectRule.fixture))
    val node = model["title"]!!
    val item = InspectorPropertyItem(
      ANDROID_URI, ATTR_TEXT_COLOR, ATTR_TEXT_COLOR, Type.COLOR, null, PropertySection.DECLARED, node.layout, node.drawId, model)
    val textStyleMaterial = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "TextAppearance.Material")
    val map = listOf(textStyleMaterial).associateWith { model.resourceLookup.findAttributeValue(item, node, it) }
    val value = model.resourceLookup.findAttributeValue(item, node, item.source!!)
    val property = InspectorGroupPropertyItem(
      ANDROID_URI, item.attrName, item.type, value, null, item.section, item.source, node.drawId, model, map)
    val editors = JPanel()
    editors.layout = BoxLayout(editors, BoxLayout.PAGE_AXIS)
    val propertiesModel = InspectorPropertiesModel()
    editors.add(createEditor(property, propertiesModel))
    property.children.forEach { editors.add(createEditor(it, propertiesModel)) }
    editors.add(Filler(Dimension(0,0), Dimension(Int.MAX_VALUE, Int.MAX_VALUE), Dimension(Int.MAX_VALUE, Int.MAX_VALUE)))
    editors.background = JBColor.WHITE
    return editors
  }

  private fun getEditor(editors: JPanel, index: Int): ResolutionElementEditor {
    return editors.getComponent(index) as ResolutionElementEditor
  }

  private fun createEditor(property: PropertyItem, propertiesModel: InspectorPropertiesModel): ResolutionElementEditor {
    val model = ResolutionStackModel(propertiesModel)
    val editorModel = TextFieldPropertyEditorModel(property, true)
    val editorComponent = PropertyTextField(editorModel)
    editorModel.readOnly = true
    return ResolutionElementEditor(model, editorModel, editorComponent)
  }

  private fun findFirstLinkComponent(editor: ResolutionElementEditor): JComponent? =
    flatten(editor).filter { (it as? JComponent)?.actionMap?.get("open") != null }[0] as JComponent?
}

class IntelliJLafRule : ExternalResource() {
  private var laf: LookAndFeel? = null
  private var theme: MetalTheme? = null

  override fun before() {
    laf = UIManager.getLookAndFeel()
    // If the current LaF is MetalLookAndFeel, we also need to save away the theme, which provides the colors and fonts to MetalLookAndFeel,
    // since IntelliJLaf changes is.
    theme = MetalLookAndFeel.getCurrentTheme()
    // Clear out anything set explicitly by previous tests
    UIManager.getDefaults().clear()
    UIManager.setLookAndFeel(IntelliJLaf())
  }

  override fun after() {
    UIManager.getDefaults().clear()

    MetalLookAndFeel.setCurrentTheme(theme)
    UIManager.setLookAndFeel(laf)
    laf = null
    theme = null
  }
}
