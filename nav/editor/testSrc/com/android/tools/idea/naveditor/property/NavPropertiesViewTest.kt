/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertiesProvider
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.android.tools.property.panel.impl.model.util.FakeLineType
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_ACTION
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DATA
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DATA_PATTERN
import org.jetbrains.android.dom.navigation.NavigationSchema.TAG_ARGUMENT
import org.jetbrains.android.facet.AndroidFacet

class NavPropertiesViewTest : NavTestCase() {
  fun testFragment() {
    val panel = setupPanel("fragment1")

    checkPanel(panel, 0)
    checkEditor(panel, 1, ANDROID_URI, ATTR_ID)
    checkEditor(panel, 2, ANDROID_URI, ATTR_LABEL)
    checkEditor(panel, 3, ANDROID_URI, ATTR_NAME)
    checkTitle(panel, 4, "Arguments")
    checkPanel(panel, 5)
    checkTitle(panel, 6, "Actions")
    checkPanel(panel, 7)
    checkTitle(panel, 8, "Deep Links")
    checkPanel(panel, 9)

    assertEquals(10, panel.lines.size)
  }

  fun testActivity() {
    val panel = setupPanel("activity1")

    checkPanel(panel, 0)
    checkEditor(panel, 1, ANDROID_URI, ATTR_ID)
    checkEditor(panel, 2, ANDROID_URI, ATTR_LABEL)
    checkEditor(panel, 3, ANDROID_URI, ATTR_NAME)
    checkTitle(panel, 4, "Activity")
    checkEditor(panel, 5, AUTO_URI, ATTR_ACTION)
    checkEditor(panel, 6, AUTO_URI, ATTR_DATA)
    checkEditor(panel, 7, AUTO_URI, ATTR_DATA_PATTERN)
    checkTitle(panel, 8, "Arguments")
    checkPanel(panel, 9)
    checkTitle(panel, 10, "Deep Links")
    checkPanel(panel, 11)

    assertEquals(12, panel.lines.size)
  }

  fun testAction() {
    val panel = setupPanel("action1")

    checkPanel(panel, 0)
    checkEditor(panel, 1, ANDROID_URI, ATTR_ID)
    checkEditor(panel, 2, AUTO_URI, NavigationSchema.ATTR_DESTINATION)
    checkTitle(panel, 3, "Animations")
    checkEditor(panel, 4, AUTO_URI, NavigationSchema.ATTR_ENTER_ANIM)
    checkEditor(panel, 5, AUTO_URI, NavigationSchema.ATTR_EXIT_ANIM)
    checkEditor(panel, 6, AUTO_URI, NavigationSchema.ATTR_POP_ENTER_ANIM)
    checkEditor(panel, 7, AUTO_URI, NavigationSchema.ATTR_POP_EXIT_ANIM)
    checkTitle(panel, 8, "Argument Default Values")
    checkPanel(panel, 9)
    checkTitle(panel, 10, "Pop Behavior")
    checkEditor(panel, 11, AUTO_URI, NavigationSchema.ATTR_POP_UP_TO)
    checkEditor(panel, 12, AUTO_URI, NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE)
    checkTitle(panel, 13, "Launch Options")
    checkEditor(panel, 14, AUTO_URI, NavigationSchema.ATTR_SINGLE_TOP)

    assertEquals(15, panel.lines.size)
  }

  fun testNavigation() {
    val panel = setupPanel("nested1")

    checkPanel(panel, 0)
    checkEditor(panel, 1, ANDROID_URI, ATTR_ID)
    checkEditor(panel, 2, ANDROID_URI, ATTR_LABEL)
    checkEditor(panel, 3, AUTO_URI, ATTR_START_DESTINATION)
    checkTitle(panel, 4, "Argument Default Values")
    checkPanel(panel, 5)
    checkTitle(panel, 6, "Arguments")
    checkPanel(panel, 7)
    checkTitle(panel, 8, "Global Actions")
    checkPanel(panel, 9)
    checkTitle(panel, 10, "Deep Links")
    checkPanel(panel, 11)

    assertEquals(12, panel.lines.size)
  }

  fun testInclude() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1")
        include("include1")
      }
    }

    val root = model.find("root")!!
    val include = root.children.first { it.tagName == TAG_INCLUDE }
    val panel = setupPanel(include, model.facet)

    checkPanel(panel, 0)
    checkEditor(panel, 1, AUTO_URI, ATTR_GRAPH)

    assertEquals(2, panel.lines.size)
  }

  fun testDeeplink() {
    val panel = setupPanel("deeplink1")
    assertEquals(0, panel.lines.size)
  }

  fun testArgument() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        action("action1", destination = "fragment1") {
          argument("argument")
        }
        fragment("fragment1") {
          argument("argument")
        }
      }
    }

    val root = model.find("action1")!!
    val include = root.children.first { it.tagName == TAG_ARGUMENT }
    val panel = setupPanel(include, model.facet)

    assertEquals(0, panel.lines.size)
  }

  private fun setupPanel(name: String): FakeInspectorPanel {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          action("action1", "nested1")
          deeplink("deeplink1", "www.foo.com")
        }
        activity("activity1")
        navigation("nested1", "fragment2") {
          fragment("fragment2")
        }
        include("include1")
      }
    }

    val component = model.find(name)!!
    return setupPanel(component, model.facet)
  }

  private fun setupPanel(component: NlComponent, facet: AndroidFacet): FakeInspectorPanel {
    val propertiesModel = NlPropertiesModel(myRootDisposable, facet)
    val propertiesView = NavPropertiesView(propertiesModel)

    val provider = NlPropertiesProvider(facet)
    val propertiesTable = provider.getProperties(propertiesModel, null, listOf(component))
    val panel = FakeInspectorPanel()

    for (builder in propertiesView.main.builders) {
      builder.attachToInspector(panel, propertiesTable)
    }

    for (builder in propertiesView.tabs[0].builders) {
      builder.attachToInspector(panel, propertiesTable)
    }

    return panel
  }

  private fun checkPanel(inspector: FakeInspectorPanel, line: Int) {
    assertTrue(line < inspector.lines.size)
    assertEquals(FakeLineType.PANEL, inspector.lines[line].type)
  }

  private fun checkEditor(inspector: FakeInspectorPanel, line: Int, namespace: String, name: String) {
    assertTrue(line < inspector.lines.size)
    assertEquals(FakeLineType.PROPERTY, inspector.lines[line].type)
    assertEquals(name, inspector.lines[line].editorModel?.property?.name)
    assertEquals(namespace, inspector.lines[line].editorModel?.property?.namespace)
  }

  private fun checkTitle(inspector: FakeInspectorPanel, line: Int, title: String) {
    assertTrue(line < inspector.lines.size)
    assertEquals(FakeLineType.TITLE, inspector.lines[line].type)
    assertEquals(title, inspector.lines[line].title)
  }
}