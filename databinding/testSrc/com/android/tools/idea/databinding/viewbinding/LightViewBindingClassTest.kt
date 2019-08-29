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

import com.android.flags.junit.RestoreFlagRule
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.tools.idea.databinding.isViewBindingEnabled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class LightViewBindingClassTest {
  private val projectRule =
    AndroidProjectRule.withAndroidModel(createAndroidProjectBuilder(viewBindingOptions = { ViewBindingOptionsStub(true) }))

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @get:Rule
  val viewBindingFlagRule = RestoreFlagRule(StudioFlags.VIEW_BINDING_ENABLED)

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  @Before
  fun setUp() {
    StudioFlags.VIEW_BINDING_ENABLED.override(true)
    assertThat(facet.isViewBindingEnabled()).isTrue()
    fixture.addFileToProject("src/main/AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())
  }

  @Test
  fun lightClassGeneratedForViewBindingLayout() {
    val file = fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)
    assertThat(binding).isNotNull()
  }

  @Test
  fun avoidGeneratingBindingForViewBindingIgnoreLayout() {
    val file = fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        tools:viewBindingIgnore="true">
        <LinearLayout
            android:id="@+id/test_id"
            android:orientation="vertical"
            android:layout_width="fill_parent"
            android:layout_height="fill_parent">
        </LinearLayout>
      </layout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)
    assertThat(binding).isNull()
  }
}