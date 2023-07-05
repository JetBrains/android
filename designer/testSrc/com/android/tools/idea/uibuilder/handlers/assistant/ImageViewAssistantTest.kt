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
package com.android.tools.idea.uibuilder.handlers.assistant

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory
import com.android.tools.idea.uibuilder.handlers.ImageViewHandler
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JList
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class ImageViewAssistantTest {

  @get:Rule val rule = AndroidProjectRule.inMemory()

  @Test
  fun uiState() {
    val nlComponent = Mockito.mock(NlComponent::class.java)
    val model = Mockito.mock(NlModel::class.java)
    val facet = AndroidFacet.getInstance(rule.module)!!
    whenever(model.facet).thenReturn(facet)
    whenever(nlComponent.model).thenReturn(model)
    var toolSrc: String? = null

    val imageHandler =
      object : ImageViewHandler() {
        override fun getToolsSrc(component: NlComponent): String? {
          return toolSrc
        }

        override fun setToolsSrc(component: NlComponent, value: String?) {
          toolSrc = value
        }
      }

    val assistant =
      ImageViewAssistant(ComponentAssistantFactory.Context(nlComponent) {}, imageHandler)
    val content = assistant.component.content

    assistant.sampleDataLoaded.get()
    assertTrue(UIUtil.findComponentOfType(content, JComboBox::class.java)?.isEnabled == true)

    val useAllCheckBox = UIUtil.findComponentOfType(content, JCheckBox::class.java)!!
    val sampleSetComboBox = UIUtil.findComponentOfType(content, JComboBox::class.java)!!
    val sampleResourceList = UIUtil.findComponentOfType(content, JList::class.java)!!

    assertThat(sampleSetComboBox.selectedIndex).isEqualTo(0)
    assertThat(useAllCheckBox.isSelected).isTrue()
    assertThat(useAllCheckBox.isEnabled).isFalse()
    assertThat(sampleResourceList.isEnabled).isFalse()

    sampleSetComboBox.selectedIndex = 1
    assertThat(sampleSetComboBox.selectedIndex).isEqualTo(1)
    assertThat(useAllCheckBox.isEnabled).isTrue()
    assertThat(useAllCheckBox.isSelected).isTrue()
    assertThat(sampleResourceList.isEnabled).isFalse()
    assertThat(toolSrc).isEqualTo("@tools:sample/avatars")

    useAllCheckBox.isSelected = false
    assertThat(sampleSetComboBox.selectedIndex).isEqualTo(1)
    assertThat(useAllCheckBox.isEnabled).isTrue()
    assertThat(useAllCheckBox.isSelected).isFalse()
    assertThat(sampleResourceList.isEnabled).isTrue()
    assertThat(sampleResourceList.selectedIndex).isEqualTo(0)
    assertThat(toolSrc).isEqualTo("@tools:sample/avatars[0]")

    sampleSetComboBox.selectedIndex = 2
    assertThat(sampleSetComboBox.selectedIndex).isEqualTo(2)
    assertThat(useAllCheckBox.isEnabled).isTrue()
    assertThat(useAllCheckBox.isSelected).isFalse()
    assertThat(sampleResourceList.isEnabled).isTrue()
    assertThat(sampleResourceList.selectedIndex).isEqualTo(0)
    assertThat(toolSrc).isEqualTo("@tools:sample/backgrounds/scenic[0]")

    useAllCheckBox.isSelected = true
    assertThat(sampleSetComboBox.selectedIndex).isEqualTo(2)
    assertThat(useAllCheckBox.isEnabled).isTrue()
    assertThat(useAllCheckBox.isSelected).isTrue()
    assertThat(sampleResourceList.isEnabled).isFalse()
    assertThat(sampleResourceList.selectedIndex).isEqualTo(-1)
    assertThat(toolSrc).isEqualTo("@tools:sample/backgrounds/scenic")

    sampleSetComboBox.selectedIndex = 0
    assertThat(sampleSetComboBox.selectedIndex).isEqualTo(0)
    assertThat(useAllCheckBox.isEnabled).isFalse()
    assertThat(useAllCheckBox.isSelected).isTrue()
    assertThat(sampleResourceList.isEnabled).isFalse()
    assertThat(sampleResourceList.selectedIndex).isEqualTo(-1)
    assertThat(toolSrc).isNull()
  }
}
