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
package com.android.tools.idea.databinding.validation

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.TestDataPaths
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.facet.FacetManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests for inspections in data binding expressions. */
@RunWith(Parameterized::class)
class DataBindingInspectionTest(private val mode: DataBindingMode) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modes() = listOf(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX)
  }

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val fixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    fixture.addFileToProject(
      "AndroidManifest.xml",
      // language=xml
      """<?xml version="1.0" encoding="utf-8"?>
         <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="test.db">
           <application />
         </manifest>
    """
        .trimIndent()
    )

    // Add a fake "BindingAdapter" to this project so the tests resolve the dependency; this is
    // easier than finding a way to add a real dependency on the data binding library, which
    // usually requires Gradle plugin support.
    val databindingPackage = mode.packageName.removeSuffix(".") // Without trailing '.'
    with(
      fixture.addFileToProject(
        "src/${databindingPackage.replace('.', '/')}/BindingAdapter.java",
        // language=java
        """
        package $databindingPackage;

        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;

        @Target(ElementType.METHOD)
        public @interface BindingAdapter {
          String[] value();
        }
      """
          .trimIndent()
      )
    ) {
      // The following line is needed or else we get an error for referencing a file out of bounds
      fixture.allowTreeAccessForFile(this.virtualFile)
    }

    val androidFacet = FacetManager.getInstance(projectRule.module).getFacetByType(AndroidFacet.ID)
    LayoutBindingModuleCache.getInstance(androidFacet!!).dataBindingMode = mode
  }

  @Test
  fun testDataBindingInspection_kotlinShowsWarningIfKaptNotApplied() {
    val useBindingAdapterFile =
      fixture.addFileToProject(
        "src/test/db/UseBindingAdapter.kt",
        // language=kotlin
        """
        package test.langdb
        import ${mode.bindingAdapter}

        <error descr="To use data binding annotations in Kotlin, apply the 'kotlin-kapt' plugin in your module's build.gradle">@BindingAdapter("sampleValue")</error>
        fun sampleFunction() {
        }
      """
          .trimIndent()
      )

    fixture.configureFromExistingVirtualFile(useBindingAdapterFile.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testDataBindingInspection_javaDoesntShowWarningBecauseAnnotationProcessorsRunAutomatically() {
    val useBindingAdapterFile =
      fixture.addFileToProject(
        "src/test/db/UseBindingAdapter.java",
        // language=java
        """
        package test.langdb;
        import ${mode.bindingAdapter};

        class TagUtils {
          @BindingAdapter("sampleValue")
          public void unusedFunction() {
          }
        }
      """
          .trimIndent()
      )

    fixture.configureFromExistingVirtualFile(useBindingAdapterFile.virtualFile)
    fixture.checkHighlighting()
  }
}
