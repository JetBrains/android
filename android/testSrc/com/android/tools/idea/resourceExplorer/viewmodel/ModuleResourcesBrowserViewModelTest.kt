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
package com.android.tools.idea.resourceExplorer.viewmodel

import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.idea.res.*
import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetType
import org.junit.After
import org.junit.Test

import org.junit.Before
import org.junit.Rule
import java.util.*

class ModuleResourcesBrowserViewModelTest {
  private val projectRule = AndroidProjectRule.onDisk()

  private val chain = RuleChain(projectRule, EdtRule())
  @Rule
  fun getChain() = chain

  private val disposable = Disposer.newDisposable("ModuleResourcesBrowserViewModelTest")

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = getTestDataDirectory()
  }

  @After
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun getResourceResolver() {
    val viewModel = createViewModel()
    Truth.assertThat(viewModel.resourceResolver).isNotNull()
  }

  @RunsInEdt
  @Test
  fun getResourceValues() {
    projectRule.fixture.copyFileToProject("res/values/colors.xml", "res/values/colors.xml")
    val viewModel = createViewModel()

    val values = viewModel.getResourceValues(ResourceType.COLOR)
    Truth.assertThat(values).isNotNull()
    Truth.assertThat(values.flatMap { it.designAssets }
      .map { it.resourceItem.resourceValue?.value })
      .containsExactly("#3F51B5", "#303F9F", "#9dff00")
    // TODO Test dependencies
  }

  private fun createViewModel(): ModuleResourcesBrowserViewModel {
    val module = projectRule.module
    val facet = AndroidFacet.getInstance(module)!!
    val synchronizationManager = SynchronizationManager(facet)
    Disposer.register(disposable, synchronizationManager)
    return ModuleResourcesBrowserViewModel(facet, synchronizationManager)
  }
}