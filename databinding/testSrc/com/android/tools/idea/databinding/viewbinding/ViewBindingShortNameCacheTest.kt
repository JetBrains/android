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
package com.android.tools.idea.databinding.viewbinding

import com.android.builder.model.ViewBindingOptions
import com.android.flags.junit.RestoreFlagRule
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory
import com.android.ide.common.gradle.model.stubs.AndroidProjectStub
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ViewBindingShortNameCacheTest {
  private val projectRule = AndroidProjectRule.onDisk()

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule
  val viewBindingFlagRule = RestoreFlagRule(StudioFlags.VIEW_BINDING_ENABLED)

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    StudioFlags.VIEW_BINDING_ENABLED.override(true)

    val androidProject = object : AndroidProjectStub("1.0") {
      override fun getViewBindingOptions(): ViewBindingOptions {
        return ViewBindingOptionsStub(true)
      }
    }
    facet.configuration.model = AndroidModuleModel(
      androidProject.name,
      projectRule.project.baseDir.toIoFile(),
      androidProject,
      androidProject.defaultVariant!!,
      IdeDependenciesFactory()
    )

    assertThat(facet.isViewBindingEnabled()).isTrue()


  }

  @Test
  fun shortNameCacheContainsViewBindingClassesAndFields() {
    fixture.addFileToProject("res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent())

    // initialize module resources
    ResourceRepositoryManager.getInstance(facet).moduleResources

    val cache = PsiShortNamesCache.getInstance(projectRule.project)

    assertThat(cache.allClassNames.asIterable()).contains("ActivityMainBinding")
    assertThat(cache.allFieldNames.asIterable()).contains("testId")
  }
}