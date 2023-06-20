/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.psi.kotlin.gradle

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.TestDataPaths
import com.android.tools.idea.nav.safeargs.project.NavigationResourcesModificationListener
import com.android.tools.idea.nav.safeargs.psi.kotlin.classesInScope
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.name.FqName
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class SafeArgsKtPackageDescriptorTestMultiKtModules {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule
  val restoreSafeArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.SIMPLE_KOTLIN_PROJECT)
    NavigationResourcesModificationListener.ensureSubscribed(fixture.project)
  }

  /**
   * Check contributed descriptors for base app and library module by providing fully qualified package names
   *
   * Test Project structure:
   * -base app module(safe arg mode is on) --> lib dep module(safe arg mode is on)
   * -excluded lib module(safe arg mode is on)
   */
  @Test
  fun multiModuleTest() {
    projectRule.requestSyncAndWait()

    // check contents when providing app package name in app module.
    val appModule = fixture.project.findAppModule().getMainModule()
    val appModuleDescriptor = appModule.toDescriptor()

    val classesMetadataAppPackageInApp = appModuleDescriptor!!
      .getPackage(FqName("com.example.myapplication"))
      .memberScope
      .classesInScope { name -> name.endsWith("Args") || name.endsWith("Directions") }

    assertThat(classesMetadataAppPackageInApp.map { it.fqcn to it.file }).containsExactly(
      "com.example.myapplication.SecondFragmentArgs" to "nav_graph.xml",
      "com.example.myapplication.SecondFragmentDirections" to "nav_graph.xml"
    )

    // check contents when providing lib package name in app module.
    val classesMetadataLibPackageInApp = appModuleDescriptor
      .getPackage(FqName("com.example.mylibrary"))
      .memberScope
      .classesInScope { name -> name.endsWith("Args") || name.endsWith("Directions") }

    // It collects 'packageFragmentProviderForWholeModuleWithDependencies'
    assertThat(classesMetadataLibPackageInApp.map { it.fqcn to it.file }).containsExactly(
      "com.example.mylibrary.FirstFragmentArgs" to "nav_graph.xml",
      "com.example.mylibrary.FirstFragmentDirections" to "nav_graph.xml",
      "com.example.mylibrary.SecondFragmentArgs" to "libnav_graph.xml",
      "com.example.mylibrary.SecondFragmentDirections" to "libnav_graph.xml"
    )

    // check contents for library module
    val libModule = fixture.project.findModule("mylibrary").getMainModule()
    val libModuleDescriptor = libModule.toDescriptor()

    val classesMetadataInLib = libModuleDescriptor!!
      .getPackage(FqName("com.example.mylibrary"))
      .memberScope
      .classesInScope { name -> name.endsWith("Args") || name.endsWith("Directions") }

    assertThat(classesMetadataInLib.map { it.fqcn to it.file }).containsExactly(
      "com.example.mylibrary.SecondFragmentArgs" to "libnav_graph.xml",
      "com.example.mylibrary.SecondFragmentDirections" to "libnav_graph.xml"
    )

    // check contents for excluded library module
    val libModuleExcluded = fixture.project.findModule("mylibraryexcluded").getMainModule()
    val libModuleExcludedDescriptor = libModuleExcluded.toDescriptor()

    val classesMetadataInLibExcluded = libModuleExcludedDescriptor!!
      .getPackage(FqName("com.example.mylibrary"))
      .memberScope
      .classesInScope { name -> name.endsWith("Args") || name.endsWith("Directions") }

    assertThat(classesMetadataInLibExcluded.map { it.fqcn to it.file }).containsExactly(
      "com.example.mylibrary.SecondFragmentArgs" to "excludedlibnav_graph.xml",
      "com.example.mylibrary.SecondFragmentDirections" to "excludedlibnav_graph.xml"
    )
  }
}