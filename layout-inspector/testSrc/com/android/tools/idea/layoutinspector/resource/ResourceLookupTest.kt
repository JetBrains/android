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
package com.android.tools.idea.layoutinspector.resource

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TEXT_COLOR
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceType
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.data.AppContext
import com.android.tools.idea.layoutinspector.util.TestStringTable
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import java.awt.Color
import java.awt.Rectangle
import com.android.tools.idea.layoutinspector.properties.PropertyType as Type

class ResourceLookupTest {

  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testUpdateConfiguration() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    AndroidModel.set(facet, TestAndroidModel())
    val resourceLookup = ResourceLookup(projectRule.project)
    val table = TestStringTable()
    val theme = table.add(ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme.Hole.Light"))!!
    val appContext = AppContext(theme, 1440, 3120)
    val process = MODERN_DEVICE.createProcess("com.example.test")
    resourceLookup.updateConfiguration(FolderConfiguration.createDefault(), 1.0f, appContext, table, process)
    assertThat(resourceLookup.resolver).isNotNull()
  }

  @Test
  fun testUpdateConfigurationWithApplicationIdSuffix() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    AndroidModel.set(facet, TestAndroidModel("com.example.test.debug"))
    val resourceLookup = ResourceLookup(projectRule.project)
    val table = TestStringTable()
    val theme = table.add(ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, "Theme.Hole.Light"))!!
    val appContext = AppContext(theme, 1440, 3120)
    val process = MODERN_DEVICE.createProcess("com.example.test.debug")
    resourceLookup.updateConfiguration(FolderConfiguration.createDefault(), 1.0f, appContext, table, process)
    assertThat(resourceLookup.resolver).isNotNull()
  }

  @Test
  fun testSingleColorIcon() {
    val title = ViewNode(1, "TextView", null, Rectangle(30, 60, 300, 100), null, "Hello Folks", 0)
    val context = object : ViewNodeAndResourceLookup {
      override val resourceLookup = ResourceLookup(projectRule.project)
      override fun get(id: Long): ViewNode = title
      override val selection: ViewNode? = null
    }
    val property = InspectorPropertyItem(ANDROID_URI, ATTR_TEXT_COLOR, ATTR_TEXT_COLOR, Type.COLOR, "#CC0000",
                                         PropertySection.DECLARED, null, title.drawId, context)
    val icon = context.resourceLookup.resolveAsIcon(property, title)
    assertThat(icon).isEqualTo(JBUIScale.scaleIcon(ColorIcon(RESOURCE_ICON_SIZE, Color(0xCC0000), false)))
  }
}
