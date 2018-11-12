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
import com.android.SdkConstants.AUTO_URI
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.MockNlComponent
import com.android.tools.idea.uibuilder.property2.*
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito

open class SupportTestUtil(facet: AndroidFacet, fixture: CodeInsightTestFixture, val components: List<NlComponent>) {
  val model = NelePropertiesModel(fixture.testRootDisposable, facet)
  val nlModel = components[0].model

  constructor(facet: AndroidFacet, fixture: CodeInsightTestFixture,
              vararg tags: String, parentTag: String = "", activityName: String = ""):
    this(facet, fixture, createComponents(facet, fixture, activityName, parentTag, *tags))

  constructor(projectRule: AndroidProjectRule, vararg tags: String, parentTag: String = ""):
    this(AndroidFacet.getInstance(projectRule.module)!!, projectRule.fixture, *tags, parentTag = parentTag)

  fun makeProperty(namespace: String, name: String, type: NelePropertyType): NelePropertyItem {
    return NelePropertyItem(namespace, name, type, null, "", model, null, components)
  }

  fun makeProperty(namespace: String, definition: AttributeDefinition): NelePropertyItem {
    return NelePropertyItem(namespace, definition.name, NelePropertyType.STRING, definition, "", model, null, components)
  }

  fun makeFlagsProperty(namespace: String, definition: AttributeDefinition): NelePropertyItem {
    return NeleFlagsPropertyItem(namespace, definition.name, NelePropertyType.STRING, definition, "", model, null, components)
  }

  fun makeFlagsProperty(namespace: String, name: String, values: List<String>): NelePropertyItem {
    val definition = AttributeDefinition(ResourceNamespace.RES_AUTO, name, null, listOf(AttributeFormat.FLAGS))
    val valueMappings = HashMap<String, Int?>()
    values.forEach { valueMappings[it] = null }
    definition.setValueMappings(valueMappings)
    return makeFlagsProperty(namespace, definition)
  }

  fun makeIdProperty(): NeleIdPropertyItem {
    return NeleIdPropertyItem(model, null, null, components)
  }

  fun findSiblingById(id: String): NlComponent? {
    return findChildById(components[0].parent!!, id)
  }

  companion object {

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
      completeModel(facet, file, component.model)
      return SupportTestUtil(facet, fixture, listOf(component))
    }

    private fun findChildById(component: NlComponent, id: String): NlComponent? {
      return component.children.find { it.id == id }
    }

    private fun createComponents(facet: AndroidFacet,
                                 fixture: CodeInsightTestFixture,
                                 activityName: String,
                                 parentTag: String,
                                 vararg tags: String): List<NlComponent> {
      val (file, components) = when {
        tags.size == 1 && parentTag.isEmpty() -> createSingleComponent(fixture, activityName, tags[0])
        else -> createMultipleComponents(fixture, activityName, parentTag, *tags)
      }
      completeModel(facet, file, components[0].model)
      return components
    }

    private fun completeModel(facet: AndroidFacet, file: PsiFile, model: NlModel) {
      val manager = ConfigurationManager.getOrCreateInstance(facet)
      val configuration = manager.getConfiguration(file.virtualFile)
      Mockito.`when`(model.configuration).thenReturn(configuration)
      Mockito.`when`(model.project).thenReturn(facet.module.project)
    }

    private fun createSingleComponent(fixture: CodeInsightTestFixture,
                                      activityName: String,
                                      tag: String): Pair<PsiFile, List<NlComponent>> {
      val activityAttrs = formatActivityAttributes(activityName)
      val text = "<$tag xmlns:android=\"$ANDROID_URI\" xmlns:app=\"$AUTO_URI\" $activityAttrs/>"
      val file = fixture.addFileToProject("res/layout/${tag.toLowerCase()}.xml", text) as XmlFile
      return Pair(file, listOf(MockNlComponent.create(file.rootTag!!)))
    }

    private fun createMultipleComponents(fixture: CodeInsightTestFixture,
                                         activityName: String,
                                         parentTag: String,
                                         vararg tags: String): Pair<PsiFile, List<NlComponent>> {
      if (parentTag.isEmpty()) throw IllegalArgumentException("parentTag must be supplied")
      val activityAttrs = formatActivityAttributes(activityName)
      var text = "<$parentTag xmlns:android=\"$ANDROID_URI\" xmlns:app=\"$AUTO_URI\" $activityAttrs>"
      tags.forEach { text += "<$it/>" }
      text += "</$parentTag>"
      val file = fixture.addFileToProject("res/layout/${parentTag.toLowerCase()}.xml", text) as XmlFile
      val parent = MockNlComponent.create(file.rootTag!!)
      val components = mutableListOf<NlComponent>()
      file.rootTag!!.subTags.forEach { components.add(MockNlComponent.create(it)) }
      components.forEach { parent.addChild(it) }
      return Pair(file, components)
    }

    private fun formatActivityAttributes(activityName: String): String {
      if (activityName.isEmpty()) return ""
      return "xmlns:tools=\"http://schemas.android.com/tools\" tools:context=\"$activityName\""
    }
  }
}
