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
package com.android.tools.idea.naveditor.property2

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.property2.support.NavEnumSupportProvider
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.property.panel.api.EnumValue
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
    val values = getValues(AUTO_URI, ATTR_DESTINATION, NelePropertyType.DESTINATION, action1)

    val expected = listOf("", "fragment3", "navigation1", "fragment2", "root", "fragment1")
    testDisplays(expected, values)
    testValues(expected.map { if (it.isBlank()) null else "@id/$it" }, values)
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
    val values = getValues(AUTO_URI, ATTR_START_DESTINATION, NelePropertyType.DESTINATION, root)

    val expected = listOf("", "activity1", "fragment1", "navigation1")
    testDisplays(expected, values)
    testValues(expected.map { if (it.isBlank()) null else "@id/$it" }, values)
  }

  fun testNames() {
    addFragment("fragment1")
    addFragment("fragment2")
    addFragment("fragment3")

    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    val fragment1 = model.find("fragment1")!!
    val values = getValues(ANDROID_URI, ATTR_NAME, NelePropertyType.CLASS_NAME, fragment1)

    val expectedDisplays = listOf("",
                                  "BlankFragment (mytest.navtest)",
                                  "fragment1 (mytest.navtest)",
                                  "fragment2 (mytest.navtest)",
                                  "fragment3 (mytest.navtest)")

    testDisplays(expectedDisplays, values)

    val expectedValues = listOf(null,
                                "mytest.navtest.BlankFragment",
                                "mytest.navtest.fragment1",
                                "mytest.navtest.fragment2",
                                "mytest.navtest.fragment3")

    testValues(expectedValues, values)
  }

  private fun testDisplays(expectedDisplays: List<String>, values: List<EnumValue>) {
    expectedDisplays.forEachIndexed { i, expected ->
      assertEquals(expected, values[i].display)
    }
  }

  private fun testValues(expectedValues: List<String?>, values: List<EnumValue>) {
    expectedValues.forEachIndexed { i, expected ->
      assertEquals(expected, values[i].value)
    }
  }

  private fun getValues(namespace: String, name: String, type: NelePropertyType, component: NlComponent): List<EnumValue> {
    val propertiesModel = NelePropertiesModel(myRootDisposable, myFacet)
    val property = NelePropertyItem(namespace, name, type,
                                    null, "", "",
                                    propertiesModel, null, listOf(component))

    val enumSupportProvider = NavEnumSupportProvider()
    val enumSupport = enumSupportProvider(property)
    assertNotNull(enumSupport)
    return enumSupport!!.values
  }

  private fun addFragment(name: String) {
    val relativePath = "src/mytest/navtest/$name.java"
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