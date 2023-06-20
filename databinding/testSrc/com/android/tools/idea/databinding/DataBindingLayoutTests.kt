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
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunsInEdt
@RunWith(Parameterized::class)
class DataBindingLayoutTests(private val mode: DataBindingMode) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "{0}")
    val modes = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  private val projectRule = AndroidProjectRule.onDisk()

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
  fun fieldsCanBeFoundThroughShortNamesCache() {
    fixture.addFileToProject("res/layout/first_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="firstValue" type="String"/>
        </data>
      </layout>
    """.trimIndent())

    fixture.addFileToProject("res/layout/second_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="secondValue" type="Int"/>
        </data>
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class FirstLayout {}")

    // This has to be called to explicitly fetch resources as a side-effect, which are used by the
    // DataBindingShortNamesCache class.
    StudioResourceRepositoryManager.getInstance(androidFacet).moduleResources

    val contextScope = context.resolveScope
    val invalidScope = GlobalSearchScope.EMPTY_SCOPE
    val cache = PsiShortNamesCache.getInstance(projectRule.project) // Powered behind the scenes by DataBindingShortNamesCache

    assertThat(cache.allClassNames.asIterable()).containsAllIn(listOf("FirstLayoutBinding", "SecondLayoutBinding"))
    assertThat(cache.getClassesByName("FirstLayoutBinding", contextScope).toList().map { it.name }).contains("FirstLayoutBinding")
    assertThat(cache.getClassesByName("FirstLayoutBinding", invalidScope).toList()).isEmpty()

    // "Binding" shortcuts to all bindings
    assertThat(cache.getClassesByName("Binding", contextScope).toList().map { it.name })
      .containsAllIn(listOf("FirstLayoutBinding", "SecondLayoutBinding"))

    assertThat(cache.allMethodNames.asIterable()).containsAllIn(listOf("inflate", "bind"))
    assertThat(cache.getMethodsByName("inflate", contextScope).toList().map { it.name }).contains("inflate")
    assertThat(cache.getMethodsByNameIfNotMoreThan("inflate", contextScope, 0).toList()).isEmpty()
    assertThat(cache.getMethodsByName("inflate", invalidScope).toList()).isEmpty()
    run {
      var inflateCount = 0
      cache.processMethodsWithName("inflate", contextScope) { ++inflateCount; false }
      assertThat(inflateCount).isEqualTo(1)
    }

    assertThat(cache.allFieldNames.asIterable()).containsAllIn(listOf("firstValue", "secondValue"))
    assertThat(cache.getFieldsByName("firstValue", contextScope).toList().map { it.name }).contains("firstValue")
    assertThat(cache.getFieldsByNameIfNotMoreThan("firstValue", contextScope, 0).toList()).isEmpty()
    assertThat(cache.getFieldsByName("firstValue", invalidScope).toList()).isEmpty()
  }

  @Test
  fun layoutClassesCanBeFoundByPackage() {
    fixture.addFileToProject("res/layout/first_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="firstValue" type="String"/>
        </data>
      </layout>
    """.trimIndent())

    fixture.addFileToProject("res/layout/second_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data>
          <variable name="secondValue" type="Int"/>
        </data>
      </layout>
    """.trimIndent())

    val context = fixture.addClass("public class FirstLayout {}")

    // This has to be called to explicitly fetch resources as a side-effect, which are used by the
    // DataBindingShortNamesCache class.
    StudioResourceRepositoryManager.getInstance(androidFacet).moduleResources

    val contextScope = context.resolveScope
    val invalidScope = GlobalSearchScope.EMPTY_SCOPE

    val psiPackage = JavaPsiFacade.getInstance(projectRule.project).findPackage("test.db.databinding")!!
    psiPackage.getClasses(contextScope).let { classes ->
      assertThat(classes.map { it.name }).containsExactly("FirstLayoutBinding", "SecondLayoutBinding")
    }
    psiPackage.getClasses(invalidScope).let { classes -> assertThat(classes).isEmpty() }
  }

  @Test
  fun dataClassAttributeAllowsCreationOfCustomBindingClassNames() {
    fixture.addFileToProject("res/layout/layout_with_custom_binding_name.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <data class=".CustomBinding" />
      </layout>
    """.trimIndent())

    // This has to be called to explicitly fetch resources as a side-effect, which are used by the
    // DataBindingShortNamesCache class.
    StudioResourceRepositoryManager.getInstance(androidFacet).moduleResources

    val cache = PsiShortNamesCache.getInstance(projectRule.project) // Powered behind the scenes by DataBindingShortNamesCache
    assertThat(cache.allClassNames.asIterable()).containsAllIn(listOf("CustomBinding"))
  }

  @Test
  fun nestedFieldsForIncludedLayoutsAreGenerated() {
    fixture.addFileToProject("res/layout/included_layout.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">
        <LinearLayout android:orientation="vertical" android:layout_width="match_parent" android:layout_height="match_parent">
          <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:id="@+id/innerTextView" />
        </LinearLayout>
      </layout>
    """.trimIndent())

    fixture.addFileToProject("res/layout/main_layout.xml", """
        <?xml version="1.0" encoding="utf-8"?>
        <layout xmlns:android="http://schemas.android.com/apk/res/android">
          <FrameLayout android:layout_width="match_parent" android:layout_height="match_parent">
            <include layout="@layout/included_layout" android:id="@+id/includedLayout"/>
          </FrameLayout>
        </layout>
    """.trimIndent())

    // This has to be called to explicitly initialize the resource repository as a side-effect.
    StudioResourceRepositoryManager.getInstance(androidFacet).appResources

    val modelFile = fixture.addFileToProject("src/java/test/db/Model.java", """
      package test.db;

      import test.db.databinding.MainLayoutBinding;

      class Model {
        public static void doSomething(MainLayoutBinding binding) {
          binding.includedLayout.${caret}
        }
      }
    """.trimIndent())

    fixture.configureFromExistingVirtualFile(modelFile.virtualFile)
    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings!!).contains("innerTextView")
  }
}