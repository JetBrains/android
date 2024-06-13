/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.databinding.psiclass

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.databinding.BindingLayout
import com.android.tools.idea.databinding.index.VariableData
import com.android.tools.idea.databinding.index.ViewIdData
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.xml.XmlTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EagerLightBindingClassConfigTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun valuesPassedThrough() {
    val mockConfig: LightBindingClassConfig = mock()

    val facet = requireNotNull(projectRule.module.androidFacet)
    whenever(mockConfig.facet).thenReturn(facet)

    val bindingLayout: BindingLayout = mock()
    whenever(mockConfig.targetLayout).thenReturn(bindingLayout)

    whenever(mockConfig.superName).thenReturn("superName")
    whenever(mockConfig.className).thenReturn("className")
    whenever(mockConfig.qualifiedName).thenReturn("qualifiedName")
    whenever(mockConfig.rootType).thenReturn("rootType")

    val variableTags: List<Pair<VariableData, XmlTag>> = emptyList()
    whenever(mockConfig.variableTags).thenReturn(variableTags)

    val scopedViewIds: Map<BindingLayout, Collection<ViewIdData>> = emptyMap()
    whenever(mockConfig.scopedViewIds).thenReturn(scopedViewIds)

    whenever(mockConfig.shouldGenerateGettersAndStaticMethods()).thenReturn(true)
    whenever(mockConfig.settersShouldBeAbstract()).thenReturn(false)

    val eagerConfig = EagerLightBindingClassConfig(mockConfig)

    assertThat(eagerConfig.facet).isSameAs(facet)
    assertThat(eagerConfig.targetLayout).isSameAs(bindingLayout)
    assertThat(eagerConfig.superName).isEqualTo("superName")
    assertThat(eagerConfig.className).isEqualTo("className")
    assertThat(eagerConfig.qualifiedName).isEqualTo("qualifiedName")
    assertThat(eagerConfig.rootType).isEqualTo("rootType")
    assertThat(eagerConfig.variableTags).isSameAs(variableTags)
    assertThat(eagerConfig.scopedViewIds).isSameAs(scopedViewIds)
    assertThat(eagerConfig.shouldGenerateGettersAndStaticMethods()).isTrue()
    assertThat(eagerConfig.settersShouldBeAbstract()).isFalse()
  }
}
