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
package com.android.tools.idea.databinding.viewbinding.gradle

import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiAnchor
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.FqName
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingNavigationTest {
  private val projectRule = AndroidGradleProjectRule()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   */
  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val editorManager
    get() = FileEditorManager.getInstance(projectRule.project)

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    projectRule.load(TestDataPaths.PROJECT_FOR_VIEWBINDING)

    val syncState = GradleSyncState.getInstance(projectRule.project)
    assertThat(syncState.isSyncNeeded().toBoolean()).isFalse()

    // Make sure that all file system events up to this point have been processed.
    VirtualFileManager.getInstance().syncRefresh()
    UIUtil.dispatchAllInvocationEvents()

    assertThat(projectRule.androidFacet(":app").isViewBindingEnabled()).isTrue()
  }

  @Test
  fun navigateLightViewBindingClass() {
    assertThat(editorManager.selectedFiles).isEmpty()

    val moduleDescriptor = projectRule.project.findAppModule().getMainModule().toDescriptor()!!
    val classDescriptor = moduleDescriptor.resolveClassByFqName(FqName("com.android.example.viewbinding.MainActivity"),
                                                                NoLookupLocation.WHEN_FIND_BY_FQNAME)!!
    val context = classDescriptor.findPsi()!!

    // ActivityMainBinding is in-memory and generated on the fly from activity_main.xml
    val binding = fixture.findClass("com.android.example.viewbinding.databinding.ActivityMainBinding", context) as LightBindingClass
    binding.navigate(true)
    assertThat(editorManager.selectedFiles[0].name).isEqualTo("activity_main.xml")

    // Regression test for 261536892: PsiAnchor.create throws assertion error for LightBindingClass navigation element.
    PsiAnchor.create(binding.navigationElement)
  }

  @Test
  fun navigateLightViewBindingField() {
    assertThat(editorManager.selectedFiles).isEmpty()

    val moduleDescriptor = projectRule.project.findAppModule().getMainModule().toDescriptor()!!
    val classDescriptor = moduleDescriptor.resolveClassByFqName(FqName("com.android.example.viewbinding.MainActivity"),
                                                                NoLookupLocation.WHEN_FIND_BY_FQNAME)!!
    val context = classDescriptor.findPsi()!!

    // ActivityMainBinding is in-memory and generated on the fly from activity_main.xml.
    val binding = fixture
      .findClass("com.android.example.viewbinding.databinding.ActivityMainBinding", context)!!
      .findFieldByName("testId", false)!!

    binding.navigate(true)
    assertThat(editorManager.selectedFiles[0].name).isEqualTo("activity_main.xml")
    assertThat(binding.navigationElement).isInstanceOf(XmlTag::class.java)
    assertThat(binding.navigationElement.text).contains("id=\"@+id/testId\"")
  }
}