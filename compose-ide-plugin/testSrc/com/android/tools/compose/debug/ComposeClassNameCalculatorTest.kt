/*
 * Copyright (C) 2024 The Android Open Source Project
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
@file:Suppress("UsagesOfObsoleteApi")

package com.android.tools.compose.debug

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.assertInstanceOf
import org.jetbrains.kotlin.idea.debugger.base.util.ClassNameCalculator
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

class ComposeClassNameCalculatorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setup() {
    /* Mock composable annotation */
    projectRule.fixture.addFileToProject(
      "src/Composable.kt",
      """
      package androidx.compose.runtime

        @Target(
          AnnotationTarget.FUNCTION,
          AnnotationTarget.TYPE,
          AnnotationTarget.TYPE_PARAMETER,
          AnnotationTarget.PROPERTY_GETTER
        )
        annotation class Composable
    """
        .trimIndent(),
    )
  }

  @Test
  fun testTopLevelComposableFunction_noAdditionalClassName() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/App.kt",
        """
      import androidx.compose.runtime.Composable

      @Composable
      fun MyComposable() {}
    """
          .trimIndent(),
      )

    runReadAction {
      val classNames = ComposeClassNameCalculator().getClassNames(file as KtFile)
      Assert.assertEquals(emptyMap<KtElement, String>(), classNames)
    }
  }

  @Test
  fun testComposableLambdaArgument_providesAdditionalClass() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/a/App.kt",
        """
      package a
      import androidx.compose.runtime.Composable

      @Composable
      fun MyComposable(child: @Composable () -> Unit) {}

       @Composable
      fun App() {
        MyComposable { } // <- This Lambda is expected to generate a special class by compose
      }
    """
          .trimIndent(),
      )

    runReadAction {
      val classNames = ComposeClassNameCalculator().getClassNames(file as KtFile)
      if (classNames.isEmpty()) {
        fail("No class name provided by ${ComposeClassNameCalculator::class.simpleName}")
      }

      if (classNames.size != 1) {
        fail("Expected only a single class name, but got $classNames")
      }

      val (element, className) = classNames.entries.first()
      assertInstanceOf<KtLambdaExpression>(element)
      Assert.assertEquals("a.ComposableSingletons\$AppKt\$lambda-1$1", className)
    }
  }

  @Test
  fun testComposableLambdaArgument_rootPackage_providesAdditionalClass() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/a/App.kt",
        """
      import androidx.compose.runtime.Composable

      @Composable
      fun MyComposable(child: @Composable () -> Unit) {}

       @Composable
      fun App() {
        MyComposable { } // <- This Lambda is expected to generate a special class by compose
      }
    """
          .trimIndent(),
      )

    runReadAction {
      val classNames = ComposeClassNameCalculator().getClassNames(file as KtFile)
      if (classNames.isEmpty()) {
        fail("No class name provided by ${ComposeClassNameCalculator::class.simpleName}")
      }

      if (classNames.size != 1) {
        fail("Expected only a single class name, but got $classNames")
      }

      val (element, className) = classNames.entries.first()
      assertInstanceOf<KtLambdaExpression>(element)
      Assert.assertEquals("ComposableSingletons\$AppKt\$lambda-1$1", className)
    }
  }

  @Test
  fun testIntegrationWithExtensionPoint() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/a/App.kt",
        """
      import androidx.compose.runtime.Composable

      @Composable
      fun MyComposable(child: @Composable () -> Unit) {}

       @Composable
      fun App() {
        MyComposable { } // <- This Lambda is expected to generate a special class by compose
      }
    """
          .trimIndent(),
      )

    runReadAction {
      val allClassNames = ClassNameCalculator.getClassNames(file as KtFile)
      Assert.assertEquals(
        mapOf(
          file to "AppKt",
          file.findDescendantOfType<KtLambdaExpression>() to
            "ComposableSingletons\$AppKt\$lambda-1\$1",
        ),
        allClassNames,
      )
    }
  }

  @Test
  fun cachesResult() {
    val file =
      projectRule.fixture.addFileToProject(
        "src/a/App.kt",
        """
      package a
      import androidx.compose.runtime.Composable

      @Composable
      fun MyComposable(child: @Composable () -> Unit) {}

       @Composable
      fun App() {
        MyComposable { } // <- This Lambda is expected to generate a special class by compose
      }
    """
          .trimIndent(),
      ) as KtFile

    runReadAction {
      val calculated = ComposeClassNameCalculator().getClassNames(file)
      val cached = ComposeClassNameCalculator().getClassNames(file)

      Assert.assertSame(calculated, cached)
    }
  }
}
