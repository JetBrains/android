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

import com.android.SdkConstants
import com.android.ide.common.gradle.model.stubs.ViewBindingOptionsStub
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilder
import com.android.tools.idea.testing.findClass
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.lang.jvm.JvmModifier
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

  private val facet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  private val fixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

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
  fun lightClassGeneratedForViewBindingLayout() {
    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:id="@+id/test_id"
          android:orientation="vertical"
          android:layout_width="fill_parent"
          android:layout_height="fill_parent">
      </LinearLayout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)
    assertThat(binding).isNotNull()
  }

  @Test
  fun avoidGeneratingBindingForViewBindingIgnoreLayout() {
    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools"
          tools:viewBindingIgnore="true" />
          android:id="@+id/test_id"
          android:orientation="vertical"
          android:layout_width="fill_parent"
          android:layout_height="fill_parent">
      </LinearLayout>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)
    assertThat(binding).isNull()
  }

  @Test
  fun expectedStaticMethodsAreGenerated() {
    fixture.addFileToProject("src/main/res/layout/view_root_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <view xmlns:android="http://schemas.android.com/apk/res/android" />
    """.trimIndent())
    fixture.addFileToProject("src/main/res/layout/merge_root_activity.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <merge xmlns:android="http://schemas.android.com/apk/res/android" />
    """.trimIndent())
    val context = fixture.addClass("public class ViewRootActivity {}")

    (fixture.findClass("test.db.databinding.ViewRootActivityBinding", context) as LightBindingClass).let { binding ->
      val methods = binding.methods.filter { it.hasModifier(JvmModifier.STATIC) }
      assertThat(methods.map { it.presentation!!.presentableText }).containsExactly(
        "inflate(LayoutInflater)",
        "inflate(LayoutInflater, ViewGroup, boolean)",
        "bind(View)"
      )
    }

    (fixture.findClass("test.db.databinding.MergeRootActivityBinding", context) as LightBindingClass).let { binding ->
      val methods = binding.methods.filter { it.hasModifier(JvmModifier.STATIC) }
      assertThat(methods.map { it.presentation!!.presentableText }).containsExactly(
        "inflate(LayoutInflater, ViewGroup)",
        "bind(View)"
      )
    }
  }

  // ViewBinding logic breaks from DataBinding logic around view stubs. See also: b/142533358
  @Test
  fun correctTypeGeneratedForViewStubs() {
    fixture.addFileToProject("src/main/res/layout/activity_main.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <ViewStub
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:id="@+id/test_id"
          android:layout_width="fill_parent"
          android:layout_height="fill_parent">
      </ViewStub>
    """.trimIndent())
    val context = fixture.addClass("public class MainActivity {}")

    val binding = fixture.findClass("test.db.databinding.ActivityMainBinding", context)!!
    assertThat(binding.findFieldByName("testId", false)!!.type.canonicalText).isEqualTo(SdkConstants.CLASS_VIEWSTUB)
  }
}