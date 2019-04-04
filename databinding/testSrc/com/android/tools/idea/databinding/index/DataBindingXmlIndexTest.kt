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
package com.android.tools.idea.databinding.index

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DataBindingXmlIndexTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val fixture: CodeInsightTestFixture by lazy {
    projectRule.fixture
  }

  @Before
  fun setUp() {
    fixture.testDataPath = "${TestDataPaths.TEST_DATA_ROOT}/xml"
    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    ModuleDataBinding.getInstance(androidFacet!!).setMode(DataBindingMode.ANDROIDX)
  }

  @Test
  fun testIndex() {
    val file = fixture.configureByFile("databinding_index_valid.xml").virtualFile
    val map = DataBindingXmlIndex().indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val layout = map.values.first()
    assertThat(layout.importCount).isEqualTo(1)
    assertThat(layout.variableCount).isEqualTo(2)
  }

  @Test
  fun testIndexWithInvalidXml() {
    val file = fixture.configureByFile("databinding_index_invalid.xml").virtualFile
    val map = DataBindingXmlIndex().indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).isEmpty()
  }
}