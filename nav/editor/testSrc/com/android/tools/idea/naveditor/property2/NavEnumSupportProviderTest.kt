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
import com.android.SdkConstants.ATTR_MODULE_NAME
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.addDynamicFeatureModule
import com.android.tools.idea.naveditor.property.support.ClassEnumValue
import com.android.tools.idea.naveditor.property.support.NavEnumSupportProvider
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumValue
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_DESTINATION

class NavEnumSupportProviderTest : NavTestCase() {
  fun testDestinations() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        navigation("navigation1") {
          fragment("fragment2")
          fragment("fragment3") {
            action("action1")
          }
        }
      }
    }

    val action1 = model.find("action1")!!
    val property = getProperty(AUTO_URI, ATTR_DESTINATION, NlPropertyType.DESTINATION, action1)
    val support = getSupport(property)
    val values = support.values

    val expected = listOf("none", "fragment3", "navigation1", "fragment2", "root", "fragment1")
    testDisplays(expected, values)
    testValues(expected.map { if (it == "none") null else "@id/$it" }, values)
    assertThat(support.createValue("@id/text123")).isEqualTo(EnumValue.item("@id/text123", "text123"))
  }

  fun testStartDestinations() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", "fragment1")
        fragment("fragment1")
        activity("activity1")
        navigation("navigation1")
      }
    }

    val root = model.find("root")!!
    val property = getProperty(AUTO_URI, ATTR_START_DESTINATION, NlPropertyType.DESTINATION, root)
    val values = getValues(property)

    val expected = listOf("none", "activity1", "fragment1", "navigation1")
    testDisplays(expected, values)
    testValues(expected.map { if (it == "none") null else "@id/$it" }, values)
  }

  fun testNames() {
    val dynamicFeatureModuleName = "dynamicfeaturemodule"
    addDynamicFeatureModule(dynamicFeatureModuleName, myModule, myFixture)

    addFragment("fragment1")
    addFragment("fragment2")
    addFragment("fragment3")
    addFragment("dynamicFragment", dynamicFeatureModuleName)

    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val property = getProperty(ANDROID_URI, ATTR_NAME, NlPropertyType.CLASS_NAME, fragment1)
    val support = getSupport(property)
    val values = support.values

    val expectedDisplays = listOf("none",
                                  "BlankFragment (mytest.navtest)",
                                  "dynamicFragment (mytest.navtest)",
                                  "fragment1 (mytest.navtest)",
                                  "fragment2 (mytest.navtest)",
                                  "fragment3 (mytest.navtest)")

    testDisplays(expectedDisplays, values)

    val expectedValues = listOf(null,
                                "mytest.navtest.BlankFragment",
                                "mytest.navtest.dynamicFragment",
                                "mytest.navtest.fragment1",
                                "mytest.navtest.fragment2",
                                "mytest.navtest.fragment3")

    testValues(expectedValues, values)

    val expectedNames = listOf(null, null, dynamicFeatureModuleName, null, null, null)
    assertThat(values.map { (it as? ClassEnumValue)?.moduleName }).containsExactlyElementsIn(expectedNames).inOrder()
    assertThat(support.createValue("mytest.navtest.ImportantFragment"))
      .isEqualTo(EnumValue.item("mytest.navtest.ImportantFragment", "ImportantFragment (mytest.navtest)"))
  }

  fun testSelectName() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val fragment1 = model.find("fragment1")!!
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val property = getProperty(ANDROID_URI, ATTR_NAME, NlPropertyType.CLASS_NAME, fragment1)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    val enumValue = ClassEnumValue("mytest.navtest.BlankFragment", "BlankFragment (mytest.navtest)", null, true)
    enumValue.select(property)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    testSelectName(fragment1, "mytest.navtest.BlankFragment",  null)
    testSelectName(fragment1, "mytest.navtest.DynamicFragment",  "dynamicfeaturemodule")
  }

  private fun testSelectName(component: NlComponent, value: String, moduleName: String?) {
    val property = getProperty(ANDROID_URI, ATTR_NAME, NlPropertyType.CLASS_NAME, component)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val enumValue = ClassEnumValue(value, "display", moduleName, true)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    enumValue.select(property)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

    assertEquals(value, component.getAttribute(ANDROID_URI, ATTR_NAME))
    assertEquals(moduleName, component.getAttribute(AUTO_URI, ATTR_MODULE_NAME))
  }

  private fun testDisplays(expectedDisplays: List<String>, values: List<EnumValue>) {
    assertThat(values.map { it.display }).containsExactlyElementsIn(expectedDisplays).inOrder()
  }

  private fun testValues(expectedValues: List<String?>, values: List<EnumValue>) {
    assertThat(values.map { it.value }).containsExactlyElementsIn(expectedValues).inOrder()
  }

  private fun getProperty(namespace: String, name: String, type: NlPropertyType, component: NlComponent) : NlPropertyItem {
    val propertiesModel = NlPropertiesModel(myRootDisposable, myFacet)
    return NlPropertyItem(namespace, name, type, null, "", "", propertiesModel, listOf(component))
  }

  private fun getValues(property: NlPropertyItem) : List<EnumValue> {
    val enumSupport = getSupport(property)
    return enumSupport.values
  }

  private fun getSupport(property: NlPropertyItem): EnumSupport {
    val enumSupportProvider = NavEnumSupportProvider()
    val enumSupport = enumSupportProvider(property)
    assertNotNull(enumSupport)
    return enumSupport!!
  }

  private fun addFragment(name: String, folder: String = "src/mytest/navtest") {
    val relativePath = "$folder/$name.java"
    val fileText = """
      .package mytest.navtest;
      .import android.support.v4.app.Fragment;
      .
      .public class $name extends Fragment {
      .}
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)
  }
}