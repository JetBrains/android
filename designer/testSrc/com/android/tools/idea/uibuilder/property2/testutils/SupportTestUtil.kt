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
package com.android.tools.idea.uibuilder.property2.testutils

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.getRoot
import com.android.tools.idea.uibuilder.property.MockNlComponent
import com.android.tools.idea.uibuilder.property2.NeleFlagsPropertyItem
import com.android.tools.idea.uibuilder.property2.NeleIdPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.google.common.collect.ImmutableList
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.text.nullize
import org.intellij.lang.annotations.Language
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.mockito.Mockito
import java.util.stream.Collectors

open class SupportTestUtil(facet: AndroidFacet, val fixture: CodeInsightTestFixture, val components: MutableList<NlComponent>) {
  val model = NelePropertiesModel(fixture.testRootDisposable, facet)
  val nlModel = components.first().model
  private val frameworkResourceManager = ModuleResourceManagers.getInstance(facet).frameworkResourceManager

  constructor(facet: AndroidFacet, fixture: CodeInsightTestFixture,
              vararg tags: String, parentTag: String = "", fileName: String = "", activityName: String = ""):
    this(facet, fixture, createComponents(facet, fixture, activityName, parentTag, fileName, *tags).toMutableList())

  constructor(facet: AndroidFacet, fixture: CodeInsightTestFixture, component: ComponentDescriptor):
    this(facet, fixture, createComponent(facet, fixture, component).toMutableList())

  constructor(projectRule: AndroidProjectRule, vararg tags: String, parentTag: String = "", fileName: String = ""):
    this(AndroidFacet.getInstance(projectRule.module)!!, projectRule.fixture, *tags, parentTag = parentTag, fileName = fileName)

  constructor(projectRule: AndroidProjectRule, component: ComponentDescriptor):
    this(AndroidFacet.getInstance(projectRule.module)!!, projectRule.fixture, component)

  init {
    model.surface = (nlModel as? SyncNlModel)?.surface
  }

  fun makeProperty(namespace: String, name: String, type: NelePropertyType): NelePropertyItem {
    val definition = findDefinition(namespace, name)
    when {
      definition == null ->
        return NelePropertyItem(namespace, name, type, null, "", "", model, null, components)
      definition.formats.contains(AttributeFormat.FLAGS) ->
        return NeleFlagsPropertyItem(namespace, name, NelePropertyType.ENUM, definition, "", "", model, null, components)
      else -> return makeProperty(namespace, definition, type)
    }
  }

  fun makeProperty(namespace: String, definition: AttributeDefinition, type: NelePropertyType): NelePropertyItem {
    return NelePropertyItem(namespace, definition.name, type, definition, "", "", model, null, components)
  }

  fun makeFlagsProperty(namespace: String, definition: AttributeDefinition): NelePropertyItem {
    return NeleFlagsPropertyItem(namespace, definition.name, NelePropertyType.STRING, definition, "", "", model, null, components)
  }

  fun makeFlagsProperty(namespace: String, name: String, values: List<String>): NelePropertyItem {
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, name, null, listOf(AttributeFormat.FLAGS))
    val valueMappings = HashMap<String, Int?>()
    values.forEach { valueMappings[it] = null }
    definition.setValueMappings(valueMappings)
    return makeFlagsProperty(namespace, definition)
  }

  fun makeIdProperty(): NeleIdPropertyItem {
    val definition = findDefinition(ANDROID_URI, ATTR_ID)
    return NeleIdPropertyItem(model, definition, "", null, components)
  }

  fun setUpCustomView() {
    setUpCustomView(fixture)
  }

  fun findSiblingById(id: String): NlComponent? {
    return findChildById(components[0].parent!!, id)
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

    fun fromId(projectRule: AndroidProjectRule, text: String, id: String): SupportTestUtil {
      val facet = AndroidFacet.getInstance(projectRule.module)!!
      val fixture = projectRule.fixture
      val file = fixture.addFileToProject("res/layout/a_layout.xml", text) as XmlFile
      val root = MockNlComponent.create(file.rootTag!!)
      for (tag in file.rootTag!!.subTags) {
        val child = MockNlComponent.create(tag)
        root.addChild(child)
      }
      val component = findChildById(root, id)!!
      completeModel(facet, file, component.model, root)
      return SupportTestUtil(facet, fixture, mutableListOf(component))
    }

    private fun findChildById(component: NlComponent, id: String): NlComponent? {
      return component.children.find { it.id == id }
    }

    private fun createComponent(facet: AndroidFacet, fixture: CodeInsightTestFixture, descriptor: ComponentDescriptor): List<NlComponent> {
      val model = NlModelBuilderUtil.model(facet, fixture as JavaCodeInsightTestFixture, "layout", "layout.xml", descriptor).build()
      val root = model.getRoot()
      return if (root.childCount > 0) root.children else model.components
    }

    private fun createComponents(facet: AndroidFacet,
                                 fixture: CodeInsightTestFixture,
                                 activityName: String,
                                 parentTag: String,
                                 fileName: String,
                                 vararg tags: String): List<NlComponent> {
      val (file, components) = when {
        tags.size == 1 && parentTag.isEmpty() -> createSingleComponent(fixture, activityName, tags[0])
        else -> createMultipleComponents(fixture, activityName, parentTag, fileName, *tags)
      }
      val root = if (tags.size == 1) components.first() else components.first().parent!!
      completeModel(facet, file, components.first().model, root)
      return components
    }

    private fun completeModel(facet: AndroidFacet, file: PsiFile, model: NlModel, root: NlComponent) {
      val manager = ConfigurationManager.getOrCreateInstance(facet)
      val configuration = manager.getConfiguration(file.virtualFile)
      val all = root.flatten().collect(Collectors.toList())
      Mockito.`when`(model.configuration).thenReturn(configuration)
      Mockito.`when`(model.project).thenReturn(facet.module.project)
      Mockito.`when`(model.components).thenReturn(ImmutableList.of(root))
      Mockito.`when`(model.flattenComponents()).thenAnswer { all.stream() }
    }

    private fun createSingleComponent(fixture: CodeInsightTestFixture,
                                      activityName: String,
                                      tag: String): Pair<PsiFile, List<NlComponent>> {
      val activityAttrs = formatActivityAttributes(activityName)
      val id = formatId(tag, 0, 1)
      val text = "<$tag xmlns:android=\"$ANDROID_URI\" xmlns:app=\"$AUTO_URI\" $id $activityAttrs/>"
      val file = fixture.addFileToProject("res/layout/${tag.toLowerCase()}.xml", text) as XmlFile
      return Pair(file, listOf(MockNlComponent.create(file.rootTag!!)))
    }

    private fun createMultipleComponents(fixture: CodeInsightTestFixture,
                                         activityName: String,
                                         parentTag: String,
                                         fileName: String,
                                         vararg tags: String): Pair<PsiFile, List<NlComponent>> {
      if (parentTag.isEmpty()) throw IllegalArgumentException("parentTag must be supplied")
      val activityAttrs = formatActivityAttributes(activityName)
      val parentId = formatId(parentTag, 0, 1)
      var text = "<$parentTag xmlns:android=\"$ANDROID_URI\" xmlns:app=\"$AUTO_URI\" $parentId $activityAttrs>"
      tags.forEachIndexed { index, tag -> text += "<$tag ${formatId(tag, index, tags.size)}/>" }
      text += "</$parentTag>"
      val layoutFileName = fileName.nullize() ?: parentTag
      val file = fixture.addFileToProject("res/layout/${layoutFileName.toLowerCase()}.xml", text) as XmlFile
      val parent = MockNlComponent.create(file.rootTag!!)
      val components = mutableListOf<NlComponent>()
      file.rootTag!!.subTags.forEach { components.add(MockNlComponent.create(parent.model, it)) }
      components.forEach { parent.addChild(it) }
      return Pair(file, components)
    }

    private fun formatId(tagName: String, index: Int, size: Int): String {
      val idName = tagName.substring(tagName.lastIndexOf('.') + 1).toLowerCase()
      val id = if (size == 1) idName else idName + (index + 1)
      return "android:$ATTR_ID=\"$NEW_ID_PREFIX$id\""
    }

    private fun formatActivityAttributes(activityName: String): String {
      if (activityName.isEmpty()) return ""
      return "xmlns:tools=\"http://schemas.android.com/tools\" tools:context=\"$activityName\""
    }
  }
}
