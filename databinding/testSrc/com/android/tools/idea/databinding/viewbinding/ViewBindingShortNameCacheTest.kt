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

import com.android.tools.idea.gradle.model.impl.IdeViewBindingOptionsImpl
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
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
  private val projectRule =
    AndroidProjectRule.withAndroidModel(AndroidProjectBuilder(viewBindingOptions = { IdeViewBindingOptionsImpl(enabled = true) }))

  // The tests need to run on the EDT thread but we must initialize the project rule off of it
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setUp() {
    assertThat(facet.isViewBindingEnabled()).isTrue()
    fixture.addFileToProject("src/main/AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())
  }

  @Test
  fun shortNameCacheContainsViewBindingClassesAndFields() {
    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
        <androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <TextView android:id="@+id/testId"/>
        </androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent())

    // initialize module resources
    StudioResourceRepositoryManager.getInstance(facet).moduleResources

    val cache = PsiShortNamesCache.getInstance(projectRule.project)

    assertThat(cache.allClassNames.asIterable()).contains("ActivityMainBinding")
    assertThat(cache.allFieldNames.asIterable()).contains("testId")
  }
}