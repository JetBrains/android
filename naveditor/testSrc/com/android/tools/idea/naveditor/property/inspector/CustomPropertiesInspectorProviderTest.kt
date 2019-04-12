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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.AUTO_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.editors.TextEditor
import com.android.tools.idea.naveditor.property.isCustomProperty
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.uibuilder.property.NlProperties
import com.android.tools.idea.uibuilder.property.editors.NlBooleanEditor
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor
import com.intellij.openapi.command.WriteCommandAction

class CustomPropertiesInspectorProviderTest : NavTestCase() {
  fun testCustomPropertiesInspector() {
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomdestination\")\n" +
                         "public class CustomNavigator extends Navigator<CustomNavigator.Destination> {\n" +
                         "  public static class Destination extends NavDestination {}\n" +
                         "}\n")
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomactivity\")\n" +
                         "public class CustomActivityNavigator extends ActivityNavigator {}\n")
      myFixture.addFileToProject("res/values/attrs2.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                         "<resources>\n" +
                                                         "    <declare-styleable name=\"CustomNavigator\">\n" +
                                                         "        <attr format=\"string\" name=\"myString\"/>\n" +
                                                         "        <attr format=\"boolean\" name=\"myBoolean\"/>\n" +
                                                         "        <attr format=\"integer\" name=\"myInteger\"/>\n" +
                                                         "    </declare-styleable>\n" +
                                                         "    <declare-styleable name=\"CustomActivityNavigator\">\n" +
                                                         "        <attr format=\"string\" name=\"myString2\"/>\n" +
                                                         "        <attr format=\"boolean\" name=\"myBoolean2\"/>\n" +
                                                         "        <attr format=\"integer\" name=\"myInteger2\"/>\n" +
                                                         "    </declare-styleable>\n" +
                                                         "</resources>\n")
    }
    ResourceRepositoryManager.getAppResources(myFacet).sync()

    // Temporary logging to help diagnose sporadic test failures
    val resources = ResourceRepositoryManager.getAppResources(myFacet).getResources(ResourceNamespace.RES_AUTO, ResourceType.ATTR)
    val resourceNames = resources.keySet().toList().sorted()
    assertTrue(toString(resourceNames),
               resourceNames == listOf("action", "argType", "data", "dataPattern", "defaultNavHost", "destination", "enterAnim", "exitAnim",
                                       "graph", "launchSingleTop", "myBoolean", "myBoolean2", "myInteger", "myInteger2", "myString",
                                       "myString2", "navGraph", "nullable", "popEnterAnim", "popExitAnim", "popUpTo", "popUpToInclusive",
                                       "startDestination", "uri"))

    val model = model("nav.xml") {
      NavModelBuilderUtil.navigation("root") {
        activity("activity")
        custom("mycustomactivity") {
          withAttribute(AUTO_URI, "myInteger2", "2")
        }
        custom("mycustomdestination") {
          withAttribute(AUTO_URI, "myBoolean", "true")
        }
      }
    }

    val propertiesManager = NavPropertiesManager(myFacet, model.surface, project)
    val inspectorProvider = CustomPropertiesInspectorProvider()
    val activityInspectorProvider = NavActivityPropertiesInspectorProvider()

    var components = listOf(model.find("activity")!!)
    var properties = getPropertyMap(propertiesManager, components)
    assertFalse(inspectorProvider.isApplicable(components, properties, propertiesManager))

    components = listOf(model.find("mycustomdestination")!!)
    properties = getPropertyMap(propertiesManager, components)

    // Temporary logging to help diagnose sporadic test failures
    val names = properties.keys.toList().sorted()
    assertTrue(toString(names), names == listOf("id", "label", "layout", "myBoolean", "myInteger", "myString", "name"))

    val customProperties = names.map { properties[it]?.isCustomProperty }
    assertTrue(toString(customProperties), customProperties == listOf(false, false, false, true, true, true, false))

    assertTrue(inspectorProvider.isApplicable(components, properties, propertiesManager))
    assertFalse(activityInspectorProvider.isApplicable(components, properties, propertiesManager))
    var inspector = inspectorProvider.createCustomInspector(components, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property.name }, listOf("layout", "myString", "myBoolean", "myInteger"))
    assertSameElements(inspector.editors.map { it.javaClass },
                       listOf(NlReferenceEditor::class.java, TextEditor::class.java, NlBooleanEditor::class.java, TextEditor::class.java))
    assertEquals("true", inspector.editors.find { it.property.name == "myBoolean" }!!.value)

    components = listOf(model.find("mycustomactivity")!!)
    properties = getPropertyMap(propertiesManager, components)
    assertTrue(inspectorProvider.isApplicable(components, properties, propertiesManager))
    assertTrue(activityInspectorProvider.isApplicable(components, properties, propertiesManager))
    inspector = inspectorProvider.createCustomInspector(components, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property.name }, listOf("myString2", "myBoolean2", "myInteger2"))
    assertSameElements(inspector.editors.map { it.javaClass },
                       listOf(TextEditor::class.java, NlBooleanEditor::class.java, TextEditor::class.java))
    assertEquals("2", inspector.editors.find { it.property.name == "myInteger2" }!!.value)
  }

  private fun getPropertyMap(propertiesManager: NavPropertiesManager, components: List<NlComponent>) =
    NlProperties.getInstance().getProperties(myFacet, propertiesManager, components).values().map { it.name to it }.toMap()
}