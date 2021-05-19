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
import com.android.tools.adtui.imagediff.ImageDiffUtil
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.model.ResolutionStackModel
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertiesModel
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.util.ComponentUtil.flatten
import com.android.tools.idea.layoutinspector.util.DemoExample
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.panel.api.TableSupport
import com.android.tools.property.panel.impl.model.TextFieldPropertyEditorModel
import com.android.tools.property.panel.impl.ui.PropertyTextField
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.JComponent
import javax.swing.LookAndFeel
import javax.swing.UIManager

private const val TEST_DATA_PATH = "tools/adt/idea/layout-inspector/testData/ui"
private const val DIFF_THRESHOLD = 0.2

@RunsInEdt
class ResolutionElementEditorTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private var laf: LookAndFeel? = null
  private var font: Font? = null

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun storeLAF() {
    laf = UIManager.getLookAndFeel()
    font = UIManager.getFont("Label.font")
  }

  @After
  fun restoreLAF() {
    setLookAndFeel(laf!!, font!!)
    laf = null
    font = null
  }

  @Test
  fun testPaint() {
    setLookAndFeel(IntelliJLaf(), ImageDiffUtil.getDefaultFont())
    val editors = createEditors()
    checkImage(editors, "Closed")

    editors[0].editorModel.isExpandedTableItem = true
    checkImage(editors, "Open")

    expandFirstLabel(editors[0], true)
    checkImage(editors, "OpenWithDetails")

    expandFirstLabel(editors[1], true)
    checkImage(editors, "OpenWithTwoDetails")
  }

  @Test
  fun testDynamicHeight() {
    var updateCount = 0
    val editors = createEditors()
    val editor = editors[0]
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
    val model = model(projectRule.project, DemoExample.setUpDemo(projectRule.fixture))
    val node = model["title"]!!
    val item1 = InspectorPropertyItem(
      ANDROID_URI, ATTR_TEXT_COLOR, ATTR_TEXT_COLOR, Type.COLOR, null, PropertySection.DECLARED, node.layout, node.drawId, model)
    val item2 = InspectorPropertyItem(
      ANDROID_URI, ATTR_ELEVATION, ATTR_ELEVATION, Type.FLOAT, null, PropertySection.DEFAULT, null, node.drawId, model)

    // The "textColor" attribute is defined in the layout file and we should have a link to the layout definition
    assertThat(ResolutionElementEditor.hasLinkPanel(item1)).isTrue()

    // The "elevation" attribute is never set so there will not be a link to follow:
    assertThat(ResolutionElementEditor.hasLinkPanel(item2)).isFalse()
  }

  @Test
  fun testDoubleClick() {
    val editors = createEditors()
    val editor = editors[0]
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

  private fun checkImage(editors: List<ResolutionElementEditor>, expected: String) {
    editors.forEach { updateSize(it) }
    @Suppress("UndesirableClassUsage")
    val generatedImage = BufferedImage(200, 300, BufferedImage.TYPE_INT_ARGB)
    val graphics = generatedImage.createGraphics()
    graphics.fillRect(0, 0, 200, 300)
    editors[0].setSize(200, editors[0].height)
    editors[0].doLayout()
    editors[0].paint(graphics)
    var y = editors[0].height
    if (editors[0].editorModel.isExpandedTableItem) {
      for (index in 1 until editors.size) {
        graphics.transform = AffineTransform.getTranslateInstance(0.0, y.toDouble())
        editors[index].setSize(200, editors[index].height)
        editors[index].doLayout()
        editors[index].paint(graphics)
        y += editors[index].height
      }
    }
    val platform = SystemInfo.OS_NAME.replace(' ', '_')
    val filename = "$TEST_DATA_PATH/testResolutionEditorPaint$expected$platform.png"
    ImageDiffUtil.assertImageSimilar(File(TestUtils.getWorkspaceRoot(), filename), generatedImage, DIFF_THRESHOLD)
  }

  private fun updateSize(component: Component) {
    component.invalidate()
    if (component is Container) {
      component.components.forEach { updateSize(it) }
      component.size = component.preferredSize
      component.doLayout()
    }
    else {
      component.size = component.preferredSize
    }
  }

  private fun expandFirstLabel(editor: ResolutionElementEditor, open: Boolean) {
    val keyStroke = if (open) KeyStrokes.RIGHT else KeyStrokes.LEFT
    val link = findFirstLinkComponent(editor)!!
    val action = link.getActionForKeyStroke(keyStroke)!!
    val event = ActionEvent(link, ActionEvent.ACTION_PERFORMED, "open")
    action.actionPerformed(event)
  }

  private fun createEditors(): List<ResolutionElementEditor> {
    val model = model(projectRule.project, DemoExample.setUpDemo(projectRule.fixture))
    val node = model["title"]!!
    val item = InspectorPropertyItem(
      ANDROID_URI, ATTR_TEXT_COLOR, ATTR_TEXT_COLOR, Type.COLOR, null, PropertySection.DECLARED, node.layout, node.drawId, model)
    val textStyleMaterial = ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "TextAppearance.Material")
    val map = listOf(textStyleMaterial).associateWith { model.resourceLookup.findAttributeValue(item, node, it) }
    val value = model.resourceLookup.findAttributeValue(item, node, item.source!!)
    val property = InspectorGroupPropertyItem(
      ANDROID_URI, item.attrName, item.type, value, null, item.group, item.source, node.drawId, model, map)
    val editors = mutableListOf<ResolutionElementEditor>()
    val propertiesModel = InspectorPropertiesModel()
    editors.add(createEditor(property, propertiesModel))
    property.children.forEach { editors.add(createEditor(it, propertiesModel)) }
    return editors
  }

  private fun createEditor(property: PropertyItem, propertiesModel: InspectorPropertiesModel): ResolutionElementEditor {
    val model = ResolutionStackModel(propertiesModel)
    val editorModel = TextFieldPropertyEditorModel(property, true)
    val editorComponent = PropertyTextField(editorModel)
    editorModel.readOnly = true
    return ResolutionElementEditor(model, editorModel, editorComponent)
  }

  private fun setLookAndFeel(laf: LookAndFeel, defaultFont: Font) {
    UIManager.setLookAndFeel(laf)
    val defaults = UIManager.getDefaults()
    defaults["TextField.font"] = defaultFont
    defaults["Label.font"] = defaultFont
    defaults["Panel.font"] = defaultFont
    defaults["TabbedPane.font"] = defaultFont
  }

  private fun findFirstLinkComponent(editor: ResolutionElementEditor): JComponent? =
    flatten(editor).filter { (it as? JComponent)?.actionMap?.get("open") != null }[0] as JComponent?
}
