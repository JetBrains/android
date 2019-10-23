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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.importer.ImportersProvider
import com.android.tools.idea.ui.resourcemanager.model.FilterOptions
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.LangDataKeys
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ResourceExplorerToolbarViewModelTest {

  @get:Rule
  var rule = AndroidProjectRule.onDisk()

  private lateinit var viewModel: ResourceExplorerToolbarViewModel

  @Before
  fun setUp() {
    viewModel = ResourceExplorerToolbarViewModel(rule.module.androidFacet!!, ImportersProvider(), FilterOptions()) {}
    rule.fixture.testDataPath = getTestDataDirectory()
  }

  @Test
  fun getCurrentModuleName() {
    assertEquals(rule.module.name, viewModel.currentModuleName)
  }

  @Test
  fun getImportersActions() {
    val importers = viewModel.getImportersActions().map { it.templatePresentation.text }.sorted()
    assertThat(importers).isEmpty()
  }

  @Test
  fun getDirectories() {
    val resFolder = rule.fixture.copyDirectoryToProject("res/", "res")
    assertThat(viewModel.directories.map { it.virtualFile }).containsExactly(resFolder)
  }

  @Test
  fun getData() {
    assertThat(viewModel.getData(LangDataKeys.MODULE.name)).isEqualTo(rule.module)
  }
}
