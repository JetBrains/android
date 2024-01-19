/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.ATTR_SCROLLBARS
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_HORIZONTAL
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property.testutils.MinApiRule
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.ptable.PTableItem
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.readAction
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import icons.StudioIcons
import kotlinx.coroutines.runBlocking
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.kotlin.tools.projectWizard.core.asNullable
import org.jetbrains.kotlin.tools.projectWizard.core.toResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

private const val EXPECTED_CONTEXT_DOCUMENTATION =
  "<html><body><b>context</b><br/><br/></body></html>"
private const val EXPECTED_TEXT_DOCUMENTATION =
  "<html><body><b>android:text</b><br/><br/>Formats: string<br/><br/>Text to display.</body></html>"
private const val EXPECTED_SCROLLBARS_DOCUMENTATION =
  "<html><body><b>android:scrollbars</b><br/><br/>Formats: flags<br/>Values: horizontal, vertical, none<br/><br/>Defines which scrollbars should be displayed on scrolling or not.</body></html>"

class NlPropertyDocumentationTargetTest {
  private val projectRule = AndroidProjectRule.withSdk()
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(MinApiRule(projectRule)).around(EdtRule())!!

  @Test
  fun testTextProperty() = runBlocking {
    val (presentation, data) =
      checkDocumentation { util -> util.properties[ANDROID_URI, ATTR_TEXT] }
    assertThat(presentation.presentableText).isEqualTo(ATTR_TEXT)
    assertThat(presentation.icon).isNull()
    assertThat(presentation.containerText).isNull()
    assertThat(presentation.locationText).isNull()
    assertThat(data.html).isEqualTo(EXPECTED_TEXT_DOCUMENTATION)
  }

  @Test
  fun testScrollbarsProperty() = runBlocking {
    val (presentation, data) =
      checkDocumentation { util ->
        // A single flag will display help for the corresponding NlFlagsPropertyItem:
        (util.properties[ANDROID_URI, ATTR_SCROLLBARS] as NlFlagsPropertyItem).flag(
          VALUE_HORIZONTAL
        )
      }
    assertThat(presentation.presentableText).isEqualTo(ATTR_SCROLLBARS)
    assertThat(presentation.icon).isEqualTo(StudioIcons.LayoutEditor.Properties.FLAG)
    assertThat(presentation.containerText).isNull()
    assertThat(presentation.locationText).isNull()
    assertThat(data.html).isEqualTo(EXPECTED_SCROLLBARS_DOCUMENTATION)
  }

  @Test
  fun testToolsProperty() = runBlocking {
    val (presentation, data) =
      checkDocumentation { util -> util.properties[TOOLS_URI, ATTR_CONTEXT] }
    assertThat(presentation.presentableText).isEqualTo(ATTR_CONTEXT)
    assertThat(presentation.icon).isEqualTo(StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE)
    assertThat(presentation.containerText).isNull()
    assertThat(presentation.locationText).isNull()
    assertThat(data.html).isEqualTo(EXPECTED_CONTEXT_DOCUMENTATION)
  }

  @Test
  fun testFabricatedProperty() = runBlocking {
    val (presentation, data) = checkDocumentation { FabricatedProperty(ANDROID_URI, ATTR_TEXT) }
    assertThat(presentation.presentableText).isEqualTo(ATTR_TEXT)
    assertThat(presentation.icon).isNull()
    assertThat(presentation.containerText).isNull()
    assertThat(presentation.locationText).isNull()
    assertThat(data.html).isEqualTo(EXPECTED_TEXT_DOCUMENTATION)
  }

  private suspend fun checkDocumentation(
    property: (InspectorTestUtil) -> PTableItem
  ): Pair<TargetPresentation, DocumentationData> {
    val util = InspectorTestUtil(projectRule, SdkConstants.TEXT_VIEW)
    util.model.surface?.selectionModel?.setSelection(util.components)
    readAction { util.loadProperties() }
    val item: PTableItem? = property(util)
    val target = NlPropertyDocumentationTarget(util.model) { resolvedPromise(item) }
    val navigatable = readAction { target.navigatable }
    assertThat(navigatable).isInstanceOf(XmlTag::class.java)
    assertThat((navigatable as XmlTag).name).isEqualTo("TextView")
    val presentation = target.computePresentation()
    val result =
      target.computeDocumentation().toResult { error("Something went wrong") }.asNullable
        as? AsyncDocumentation
    val data = result?.supplier?.invoke() as? DocumentationData ?: error("No Documentation found")
    return Pair(presentation, data)
  }

  private class FabricatedProperty(override val namespace: String, override val name: String) :
    PropertyItem {
    override var value: String? = null
  }
}
