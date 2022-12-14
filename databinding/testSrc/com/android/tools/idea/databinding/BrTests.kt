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
package com.android.tools.idea.databinding

import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Misc. tests that target [LightBrClass], [BrShortNamesCache], and potentially other classes
 * related to verifying / covering code around light BR classes.
 */
@RunsInEdt
@RunWith(Parameterized::class)
class BrTests(private val mode: DataBindingMode) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val modes = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.withSdk()

  // We want to run tests on the EDT thread, but we also need to make sure the project rule is not
  // initialized on the EDT.
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a [JavaCodeInsightTestFixture] because our
   * [AndroidProjectRule] is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private val fixture: JavaCodeInsightTestFixture
    get() = projectRule.fixture as JavaCodeInsightTestFixture

  private val androidFacet
    get() = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)!!

  @Before
  fun setUp() {
    val fixture = fixture

    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
        <application />
      </manifest>
    """.trimIndent())

    LayoutBindingModuleCache.getInstance(androidFacet).dataBindingMode = mode
  }

  @Test
  fun brClassReferenceAndImportIsFoundWithoutWarnings_Java() {
    fixture.addFileToProject("res/layout/variables_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="aStr" type="String" />
        </data>
      </layout>
    """.trimIndent())

    val activityWithBr = fixture.addClass(
      // language=java
      """
      package test.db;

      import android.app.Activity;
      import android.os.Bundle;
      import test.db.BR;

      public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
          int brValue = BR.aStr;
        }
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(activityWithBr.containingFile.virtualFile)
    fixture.checkHighlighting() // If BR is found, there will be no "Cannot resolve symbol" warning
  }

  @Test
  fun brClassReferenceAndImportIsFoundWithoutWarnings_Kotlin() {
    fixture.addFileToProject("res/layout/variables_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="aStr" type="String" />
        </data>
      </layout>
    """.trimIndent())

    val activityWithBr = fixture.addFileToProject(
      "src/test/db/MainActivity.kt",
      // language=kotlin
      """
      package test.db

      import android.app.Activity
      import android.os.Bundle
      import test.db.BR

      class MainActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
          BR.aStr
        }
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(activityWithBr.containingFile.virtualFile)
    fixture.checkHighlighting() // If BR is found, there will be no "Cannot resolve symbol" warning
  }


  @Test
  fun codeCompletionOnBrClassesWorks() {
    fixture.addFileToProject("res/layout/variables_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="aStr" type="String" />
          <variable name="anInt" type="Integer" />
          <variable name="anObject" type="Object" />
        </data>
      </layout>
    """.trimIndent())

    val activityWithBr = fixture.addClass("""
      package test.db;

      import test.db.BR;
      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
          BR.${caret}
        }
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(activityWithBr.containingFile.virtualFile)
    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings!!).containsAllOf("_all", "aStr", "anInt", "anObject")
  }

  @Test
  fun fieldsCanBeFoundThroughShortNamesCache() {
    fixture.addFileToProject("res/layout/variables_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="aStr" type="String" />
          <variable name="anInt" type="Integer" />
          <variable name="anObject" type="Object" />
        </data>
      </layout>
    """.trimIndent())

    // This has to be called to explicitly fetch resources as a side-effect, which are used by the
    // BrShortNamesCache class.
    ResourceRepositoryManager.getInstance(androidFacet).moduleResources

    val projectScope = projectRule.project.projectScope()
    val invalidScope = GlobalSearchScope.EMPTY_SCOPE
    val cache = PsiShortNamesCache.getInstance(projectRule.project) // Powered behind the scenes by BrShortNamesCache

    assertThat(cache.allFieldNames.asIterable()).containsAllOf("_all", "aStr", "anInt", "anObject")
    assertThat(cache.getFieldsByName("anInt", projectScope).map { it.name }).containsExactly("anInt")
    assertThat(cache.getFieldsByName("anInt", invalidScope)).isEmpty()
    assertThat(cache.getFieldsByNameIfNotMoreThan("aStr", projectScope, 10).map { it.name }).containsExactly("aStr")
    assertThat(cache.getFieldsByNameIfNotMoreThan("aStr", projectScope, 0).asIterable()).isEmpty()
    assertThat(cache.allClassNames.asIterable()).contains(DataBindingUtil.BR)
  }
}