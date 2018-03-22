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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.property.MockNlComponent
import com.android.tools.idea.uibuilder.property2.NeleFlagsPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.facet.AndroidFacet
import org.mockito.Mockito

open class SupportTestUtil(parentDisposable: Disposable, private val facet: AndroidFacet, private val fixture: JavaCodeInsightTestFixture,
                           tag: String, parentTag: String = "", activityName: String = "") {
  private val model = NelePropertiesModel(parentDisposable, facet)
  private val components = listOf(createComponent(tag, parentTag, activityName))
  val nlModel = components[0].model

  fun makeProperty(namespace: String, name: String, type: NelePropertyType): NelePropertyItem {
    return NelePropertyItem(namespace, name, type, null, "", model, components)
  }

  fun makeProperty(namespace: String, definition: AttributeDefinition): NelePropertyItem {
    return NelePropertyItem(namespace, definition.name, NelePropertyType.STRING, definition, "", model, components)
  }

  fun makeFlagsProperty(namespace: String, definition: AttributeDefinition): NelePropertyItem {
    return NeleFlagsPropertyItem(namespace, definition.name, NelePropertyType.STRING, definition, "", model, components)
  }

  private fun createComponent(tag: String, parentTag: String, activityName: String): NlComponent {
    val (file, component) =
        if (parentTag.isEmpty()) createSingleComponent(tag, activityName) else createParentedComponent(tag, parentTag, activityName)
    val manager = ConfigurationManager.getOrCreateInstance(facet)
    val configuration = manager.getConfiguration(file.virtualFile)
    Mockito.`when`(component.model.configuration).thenReturn(configuration)
    Mockito.`when`(component.model.project).thenReturn(facet.module.project)
    return component
  }

  private fun createSingleComponent(tag: String, activityName: String): Pair<PsiFile, NlComponent> {
    val activityAttrs = formatActivityAttributes(activityName)
    val text = "<$tag xmlns:android=\"http://schemas.android.com/apk/res/android\" $activityAttrs/>"
    val file = fixture.addFileToProject("res/layout/${tag.toLowerCase()}.xml", text) as XmlFile
    return Pair(file, MockNlComponent.create(file.rootTag!!))
  }

  private fun createParentedComponent(tag: String, parentTag: String, activityName: String): Pair<PsiFile, NlComponent> {
    val activityAttrs = formatActivityAttributes(activityName)
    val text = "<$parentTag xmlns:android=\"http://schemas.android.com/apk/res/android\" $activityAttrs><$tag/></$parentTag>"
    val file = fixture.addFileToProject("res/layout/${tag.toLowerCase()}.xml", text) as XmlFile
    val parent = MockNlComponent.create(file.rootTag!!)
    val child = MockNlComponent.create(file.rootTag!!.findFirstSubTag(tag)!!)
    parent.addChild(child)
    return Pair(file, child)
  }

  private fun formatActivityAttributes(activityName: String): String {
    if (activityName.isEmpty()) return ""
    return "xmlns:tools=\"http://schemas.android.com/tools\" tools:context=\"$activityName\""
  }
}
