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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.APP_PREFIX
import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.FD_RES_LAYOUT
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.lint.AttributeKey
import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.property.NlFlagsPropertyItem
import com.android.tools.idea.uibuilder.property.NlIdPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import org.intellij.lang.annotations.Language
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Predicate

private const val DEFAULT_FILENAME = "layout.xml"

open class SupportTestUtil(facet: AndroidFacet, val fixture: CodeInsightTestFixture, val components: MutableList<NlComponent>) {
  private var updates = 0
  private val queue = MergingUpdateQueue("MQ", 100, true, null, fixture.testRootDisposable).apply { isPassThrough = true }
  val model = NlPropertiesModel(fixture.testRootDisposable, facet, queue)
  val nlModel = components.first().model
  private val frameworkResourceManager = ModuleResourceManagers.getInstance(facet).frameworkResourceManager

  constructor(facet: AndroidFacet, fixture: CodeInsightTestFixture,
              vararg tags: String, parentTag: String = "", resourceFolder: String = FD_RES_LAYOUT,
              fileName: String = DEFAULT_FILENAME, activityName: String = ""):
    this(facet, fixture, createComponents(facet, fixture, activityName, parentTag, resourceFolder, fileName, *tags).toMutableList())

  constructor(facet: AndroidFacet, fixture: CodeInsightTestFixture, component: ComponentDescriptor):
    this(facet, fixture, createComponent(facet, fixture, FD_RES_LAYOUT, DEFAULT_FILENAME, component).toMutableList())

  constructor(projectRule: AndroidProjectRule, vararg tags: String, parentTag: String = "",
              resourceFolder: String = FD_RES_LAYOUT, fileName: String = DEFAULT_FILENAME):
    this(AndroidFacet.getInstance(projectRule.module)!!, projectRule.fixture, *tags, parentTag = parentTag, resourceFolder = resourceFolder,
         fileName = fileName)

  constructor(projectRule: AndroidProjectRule, component: ComponentDescriptor):
    this(AndroidFacet.getInstance(projectRule.module)!!, projectRule.fixture, component)

  init {
    model.addListener(object : PropertiesModelListener<NlPropertyItem> {
      override fun propertiesGenerated(model: PropertiesModel<NlPropertyItem>) {
        updates++
      }

      override fun propertyValuesChanged(model: PropertiesModel<NlPropertyItem>) {
        updates++
      }
    })
    model.surface = (nlModel as? SyncNlModel)?.surface
  }

  fun waitForPropertiesUpdate(updatesToWaitFor: Int, timeout: Long = 10, unit: TimeUnit = TimeUnit.SECONDS) {
    val stop = System.currentTimeMillis() + unit.toMillis(timeout)
    while (updates < updatesToWaitFor && System.currentTimeMillis() < stop) {
      Thread.sleep(100)
      UIUtil.dispatchAllInvocationEvents()
    }
    if (updates < updatesToWaitFor) {
      throw TimeoutException()
    }
  }

  fun makeProperty(namespace: String, name: String, type: NlPropertyType): NlPropertyItem {
    val definition = findDefinition(namespace, name)
    return when {
      definition == null ->
        NlPropertyItem(namespace, name, type, null, "", "", model, components)
      definition.formats.contains(AttributeFormat.FLAGS) ->
        NlFlagsPropertyItem(namespace, name, NlPropertyType.ENUM, definition, "", "", model, components)
      else ->
        makeProperty(namespace, definition, type)
    }
  }

  fun makeProperty(namespace: String, definition: AttributeDefinition, type: NlPropertyType): NlPropertyItem {
    return NlPropertyItem(namespace, definition.name, type, definition, "", "", model, components)
  }

  fun makeFlagsProperty(namespace: String, definition: AttributeDefinition): NlPropertyItem {
    return NlFlagsPropertyItem(namespace, definition.name, NlPropertyType.STRING, definition, "", "", model, components)
  }

  fun makeFlagsProperty(namespace: String, name: String, values: List<String>): NlPropertyItem {
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, name, null, listOf(AttributeFormat.FLAGS))
    val valueMappings = HashMap<String, Int?>()
    values.forEach { valueMappings[it] = null }
    definition.setValueMappings(valueMappings)
    return makeFlagsProperty(namespace, definition)
  }

  fun makeIdProperty(): NlIdPropertyItem {
    val definition = findDefinition(ANDROID_URI, ATTR_ID)
    return NlIdPropertyItem(model, definition, "", components)
  }

  fun setUpCustomView() {
    setUpCustomView(fixture)
  }

  fun findSiblingById(id: String): NlComponent? {
    return findChildById(components[0].parent!!, id)
  }

  fun selectById(id: String): SupportTestUtil {
    components.clear()
    components.add(nlModel.find(id)!!)
    model.surface?.selectionModel?.setSelection(components)
    return this
  }

  fun select(condition: Predicate<NlComponent>): SupportTestUtil {
    components.clear()
    components.add(nlModel.find(condition)!!)
    model.surface?.selectionModel?.setSelection(components)
    return this
  }

  fun clearSnapshots(): SupportTestUtil {
    nlModel.flattenComponents().forEach { it.snapshot = null }
    return this
  }

  fun addIssue(property: NlPropertyItem, level: HighlightDisplayLevel, message: String) {
    val lintModel = nlModel.lintAnnotationsModel ?: LintAnnotationsModel().apply { nlModel.lintAnnotationsModel = this }
    val component = property.components.single()
    lintModel.addIssue(component, AttributeKey(component, property.namespace, property.name), mock(), message,
                       mock(), level, mock(), mock(), mock())
  }

  private fun findDefinition(namespace: String, name: String): AttributeDefinition? {
    if (namespace != ANDROID_URI) {
      return null
    }
    val ref = ResourceReference.attr(ResourceNamespace.ANDROID, name)
    return frameworkResourceManager?.attributeDefinitions?.getAttrDefinition(ref)
  }

  companion object {

    fun setUpCustomView(fixture: CodeInsightTestFixture) {
      @Language("XML")
      val attrsSrc = """<?xml version="1.0" encoding="utf-8"?>
      <resources>
        <declare-styleable name="PieChart">
          <!-- Help Text -->
          <attr name="legend" format="boolean" />
          <attr name="labelPosition" format="enum">
            <enum name="left" value="0"/>
            <enum name="right" value="1"/>
          </attr>
        </declare-styleable>
      </resources>
      """.trimIndent()

      @Language("JAVA")
      val javaSrc = """
      package com.example;

      import android.content.Context;
      import android.view.View;

      public class PieChart extends View {
          public PieChart(Context context) {
              super(context);
          }
      }
      """.trimIndent()

      fixture.addFileToProject("res/values/attrs.xml", attrsSrc)
      fixture.addFileToProject("src/com/example/PieChart.java", javaSrc)
    }

    private fun findChildById(component: NlComponent, id: String): NlComponent? {
      return component.children.find { it.id == id }
    }

    protected fun createComponent(
      facet: AndroidFacet,
      fixture: CodeInsightTestFixture,
      resourceFolder: String,
      fileName: String,
      descriptor: ComponentDescriptor
    ): List<NlComponent> {
      val model = NlModelBuilderUtil.model(facet, fixture, resourceFolder, fileName, descriptor).build()
      val root = model.getRoot()
      return if (root.childCount > 0) root.children else model.components
    }

    private fun createComponents(
      facet: AndroidFacet,
      fixture: CodeInsightTestFixture,
      activityName: String,
      parentTag: String,
      resourceFolder: String,
      fileName: String,
      vararg tags: String
    ): List<NlComponent> {
      val descriptor = if (tags.size == 1 && parentTag.isEmpty()) fromSingleTag(activityName, tags[0])
      else fromMultipleTags(activityName, parentTag, resourceFolder, *tags)

      return createComponent(facet, fixture, resourceFolder, fileName, descriptor)
    }

    private fun fromSingleTag(activityName: String, tag: String): ComponentDescriptor {
      val descriptor = ComponentDescriptor(tag)
        .withBounds(0, 0, 100, 100)
        .id(toId(tag))

      if (activityName.isNotEmpty()) {
        descriptor
          .withAttribute(XMLNS_PREFIX + "tools", TOOLS_URI)
          .withAttribute(TOOLS_URI, ATTR_CONTEXT, activityName)
      }
      if (tag.contains('.')) {
        descriptor.withAttribute(XMLNS_PREFIX + APP_PREFIX, AUTO_URI)
      }
      return descriptor
        .wrapContentWidth()
        .wrapContentHeight()
    }

    private fun fromMultipleTags(
      activityName: String,
      parentTag: String,
      resourceFolder: String,
      vararg tags: String
    ): ComponentDescriptor {
      if (parentTag.isEmpty()) throw IllegalArgumentException("parentTag must be supplied")
      val descriptor = ComponentDescriptor(parentTag)
        .withBounds(0, 0, 1000, 1000)

      if (resourceFolder == FD_RES_LAYOUT) {
        descriptor.id(toId(parentTag))
          .matchParentWidth()
          .matchParentHeight()
      }
      if (activityName.isNotEmpty()) {
        descriptor.withAttribute(TOOLS_URI, ATTR_CONTEXT, activityName)
      }
      if (parentTag.contains('.') || Arrays.stream(tags).anyMatch { tag -> tag.contains('.') }) {
        descriptor.withAttribute(XMLNS_PREFIX + APP_PREFIX, AUTO_URI)
      }

      for ((index, tag) in tags.withIndex()) {
        val child = ComponentDescriptor(tag).withBounds(0, index * 100, 100, 100)
        if (resourceFolder == FD_RES_LAYOUT) {
          child
            .id(toId(tag, index + 1))
            .wrapContentWidth()
            .wrapContentHeight()
        }
        descriptor.addChild(child, null)
      }
      return descriptor
    }

    private fun toId(tagName: String, index: Int = 0): String {
      val offset = if (index == 0) "" else index.toString()
      return "@+id/${tagName.toLowerCase().substringAfterLast('.')}$offset"
    }
  }
}
