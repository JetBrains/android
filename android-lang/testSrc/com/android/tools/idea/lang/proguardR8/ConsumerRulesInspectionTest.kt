/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lang.com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.lang.proguardR8.inspections.ConsumerRulesInspection
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.highlightedAs
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File

@RunWith(/* value = */ Parameterized::class)
@RunsInEdt
class ConsumerRulesInspectionTest(val projectType: IdeAndroidProjectType) {

  @get:Rule
  val projectRule: AndroidProjectRule = AndroidProjectRule.withAndroidModels(
    prepareProjectSources = { dir ->
      assertThat(File(/* parent = */ dir, /* child = */ "src").mkdirs()).isTrue()
    },
    AndroidModuleModelBuilder(
      gradlePath = ":",
      selectedBuildVariant = "debug",
      projectBuilder = createAndroidProjectBuilderForDefaultTestProjectStructure(
        projectType = projectType
      )
    )
  )

  val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameters(name = "androidProjectType={0}")
    val data = listOf(
      arrayOf(IdeAndroidProjectType.PROJECT_TYPE_APP),
      arrayOf(IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)
    )
  }

  @Before
  fun setUp() {
    // Enable our inspections
    myFixture.enableInspections(/* ...inspections = */ ConsumerRulesInspection::class.java)
  }

  @Test
  fun testBannedRulesInOtherFiles() {
    val rules = listOf(
      "-dontobfuscate",
      "-dontoptimize",
      "-dontshrink",
      "-repackageclasses",
      "-flattenpackagehierarchy",
      "-allowaccessmodification",
      "-renamesourcefileattribute",
    )

    rules.forEach { rule ->
      myFixture.configureByText(
        // Intentionally pick a name that does not match our pattern for consumer rules.
        /* fileName = */ "proguard-rules.pro",
        /* text = */ """
          $rule
        """.trimIndent()
      )

      myFixture.checkHighlighting()
    }
  }

  @Test
  fun testBannedRulesInConsumerRulesPro() {
    val rules = listOf(
      "-dontobfuscate",
      "-dontoptimize",
      "-dontshrink",
      "-repackageclasses",
      "-flattenpackagehierarchy",
      "-allowaccessmodification",
      "-renamesourcefileattribute",
    )

    rules.forEach { rule ->
      val highlightedRule = if (projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY) {
        rule.highlightedAs(
          level = ERROR,
          message =
            "Global flags should never be placed in library consumer rules, since they prevent optimizations in apps using the library"
        )
      }
      else {
        rule
      }

      myFixture.configureByText(
        /* fileName = */ "consumer-rules.pro",
        /* text = */ """
          $highlightedRule
        """.trimIndent()
      )

      myFixture.checkHighlighting()
    }
  }

  @Test
  fun testKeepAttributes() {
    val rulesAndErrorHighlights = listOf(
      "-keepattributes RuntimeInvisibleAnnotations" to """
        -keepattributes <error descr="Attribute RuntimeInvisibleAnnotations should never be placed in library consumer rules, since it prevents optimizations in apps using the library">RuntimeInvisibleAnnotations</error>
      """.trimIndent(),
      "-keepattributes LineNumberTable" to """
        -keepattributes <error descr="Attribute LineNumberTable should never be placed in library consumer rules, since it prevents optimizations in apps using the library">LineNumberTable</error>
      """.trimIndent(),
      "-keepattributes SourceFile" to """
        -keepattributes <error descr="Attribute SourceFile should never be placed in library consumer rules, since it prevents optimizations in apps using the library">SourceFile</error>
      """.trimIndent(),
      "-keepattributes *Annotations" to """
        -keepattributes <error descr="Attribute *Annotations should never be placed in library consumer rules, since it prevents optimizations in apps using the library">*Annotations</error>
      """.trimIndent(),
      "-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleTypeAnnotations" to """
        -keepattributes <error descr="Attribute RuntimeInvisibleAnnotations should never be placed in library consumer rules, since it prevents optimizations in apps using the library">RuntimeInvisibleAnnotations</error>, <error descr="Attribute RuntimeInvisibleTypeAnnotations should never be placed in library consumer rules, since it prevents optimizations in apps using the library">RuntimeInvisibleTypeAnnotations</error>
      """.trimIndent(),
      "-keepattributes RuntimeInvisibleParameterAnnotations, RuntimeInvisibleTypeAnnotations" to """
        -keepattributes <error descr="Attribute RuntimeInvisibleParameterAnnotations should never be placed in library consumer rules, since it prevents optimizations in apps using the library">RuntimeInvisibleParameterAnnotations</error>, <error descr="Attribute RuntimeInvisibleTypeAnnotations should never be placed in library consumer rules, since it prevents optimizations in apps using the library">RuntimeInvisibleTypeAnnotations</error>
      """.trimIndent(),
      "-keepattributes Exceptions" to "-keepattributes Exceptions" // No error highlights expected
    )

    rulesAndErrorHighlights.forEach { (rule, errorHighlight) ->
      val highlightedRule = if (projectType == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY) {
        check(errorHighlight.isNotBlank()) // Bad test case
        errorHighlight
      }
      else {
        rule
      }

      myFixture.configureByText(
        /* fileName = */ "consumer-rules.pro",
        /* text = */ """
          $highlightedRule
        """.trimIndent()
      )

      myFixture.checkHighlighting()
    }
  }
}
