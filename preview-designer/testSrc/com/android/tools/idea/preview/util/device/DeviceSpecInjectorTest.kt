/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.util.device

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class DeviceSpecInjectorTest {
  @get:Rule val rule = AndroidProjectRule.inMemory()

  val fixture
    get() = rule.fixture

  private val injectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(fixture)

  @Before
  fun setup() {
    fixture.addFileToProject(
      "test/Preview.kt",
      // language=kotlin
      """
    package test

    annotation class Preview(
      val device: String = ""
    )
    """
        .trimIndent(),
    )

    val deviceSpecInjectionContributor =
      object : DeviceSpecInjectionContributor() {
        override fun isPreviewAnnotation(annotation: UAnnotation): Boolean {
          return annotation.qualifiedName == "test.Preview"
        }
      }
    LanguageInjectionContributor.INJECTOR_EXTENSION.addExplicitExtension(
      KotlinLanguage.INSTANCE,
      deviceSpecInjectionContributor,
    )
    LanguageInjectionContributor.INJECTOR_EXTENSION.addExplicitExtension(
      JavaLanguage.INSTANCE,
      deviceSpecInjectionContributor,
    )
  }

  @Test
  fun injectedForDeviceParameter() {
    rule.fixture.configureByText(
      "test.kt",
      // language=kotlin
      """
        package example
        import test.Preview

        @Preview(device = "id:device$caret name")
        fun myFun() {}
      """
        .trimIndent(),
    )
    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }
  }

  @Test
  fun injectedForDeviceParameterJava() {
    rule.fixture.configureByText(
      "Test.java",
      // language=java
      """
        package example;
        import test.Preview;

        class Test {
            @Preview(device = "id:device$caret name")
            public void myFun() {}
        }
      """
        .trimIndent(),
    )
    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }
  }

  @Test
  fun notInjectedForNonDeviceParameters() {
    assertFailsOnPreviewParameter("name")
    assertFailsOnPreviewParameter("group")
    assertFailsOnPreviewParameter("locale")
  }

  @Test
  fun notInjectedForNonDeviceParametersJava() {
    assertFailsOnPreviewParameterInJava("name")
    assertFailsOnPreviewParameterInJava("group")
    assertFailsOnPreviewParameterInJava("locale")
  }

  @Test
  fun injectedInConcatenatedExpressionForDeviceParameter() {
    // Concatenated expressions are only supported on kotlin
    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import test.Preview

        const val heightDp = "841dp"
        const val specPref = "spec:"
        const val heightPx = "1900px"

        @Preview(device = "spec:width=673.5dp," + "height=" + heightDp + "" + ""${'"'},chinSize=11dp""${'"'})
        @Preview(device = specPref + "width=10dp,height=" + heightDp)
        @Preview(device = "spec:width=1080px," + "height=" + heightPx)
        fun preview1() {}
      """
        .trimIndent(),
    )
    val injectedElementsAndTexts = runReadAction {
      injectionFixture.getAllInjections().map { Pair(it.first.text, it.second.text) }
    }
    assertEquals(3, injectedElementsAndTexts.size)
    // Assert the text of the elements marked for Injection
    assertEquals(""""spec:width=673.5dp,"""", injectedElementsAndTexts[0].first)
    assertEquals(""""width=10dp,height="""", injectedElementsAndTexts[1].first)
    assertEquals(""""spec:width=1080px,"""", injectedElementsAndTexts[2].first)

    // Assert the contents of the Injected file, should reflect the resolved text in the `device`
    // parameter
    assertEquals(
      "spec:width=673.5dp,height=841dp,chinSize=11dp",
      injectedElementsAndTexts[0].second,
    )
    assertEquals("spec:width=10dp,height=841dp", injectedElementsAndTexts[1].second)
    assertEquals("spec:width=1080px,height=1900px", injectedElementsAndTexts[2].second)
  }

  @Test
  fun onlyOneExpressionInjectedInConcatenatedDeviceSpec() {
    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import test.Preview

        const val heightPx = "1900px"

        @Preview(device = "spec$caret:width=1080px," + "height=" + heightPx)
        fun preview1() {}
      """
        .trimIndent(),
    )
    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }

    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import test.Preview

        const val heightPx = "1900px"

        @Preview(device = "spec:width=1080px," + "height$caret=" + heightPx)
        fun preview1() {}
      """
        .trimIndent(),
    )
    runReadAction {
      assertThrows(Throwable::class.java) {
        injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
      }
    }
  }

  @Test
  fun onlyOneExpressionInjectedInConcatenatedDeviceSpecJava() {
    rule.fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
          import test.Preview;

          class Test {
            private final static String HEIGHT_PX = "1900px";

            @Preview(device = "spec$caret:width=1080px," + "height=" + HEIGHT_PX)
            private void preview1() {}
          }
        """
        .trimIndent(),
    )
    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }

    rule.fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
          import test.Preview;

          class Test {
            private static final String HEIGHT_PX = "1900px";

            @Preview(device = "spec:width=1080px," + "height$caret=" + HEIGHT_PX)
            private void preview1() {}
          }
        """
        .trimIndent(),
    )
    runReadAction {
      assertThrows(Throwable::class.java) {
        injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
      }
    }
  }

  @Test
  fun notInjectedForPreviewFromDifferentPackage() {
    rule.fixture.addFileToProject(
      "anotherpackage/Preview.kt",
      // language=kotlin
      """
        package anotherpackage

        annotation class Preview(
          val device: String = ""
        )
      """
        .trimIndent(),
    )

    rule.fixture.configureByText(
      "test.kt",
      // language=kotlin
      """
        import anotherpackage.Preview

        @Preview(device = "id:device$caret name")
        fun myFun() {}
      """
        .trimIndent(),
    )
    runReadAction {
      assertThrows(Throwable::class.java) {
        injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
      }
    }
  }

  @Test
  fun notInjectedForPreviewFromDifferentPackageJava() {
    rule.fixture.addFileToProject(
      "anotherpackage/Preview.kt",
      // language=kotlin
      """
        package anotherpackage

        annotation class Preview(
          val device: String = ""
        )
      """
        .trimIndent(),
    )

    rule.fixture.configureByText(
      "Test.java",
      // language=java
      """
        import anotherpackage.Preview;

        class Test {
          @Preview(device = "id:device$caret name")
          public void myFun() {}
        }
      """
        .trimIndent(),
    )
    runReadAction {
      assertThrows(Throwable::class.java) {
        injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
      }
    }
  }

  private fun assertFailsOnPreviewParameter(parameterName: String) {
    require(parameterName != "device")
    rule.fixture.configureByText(
      "test.kt",
      // language=kotlin
      """
        package example
        import test.Preview

        @Preview($parameterName = "id:device$caret name")
        fun myFun() {}
      """
        .trimIndent(),
    )
    runReadAction {
      assertThrows(Throwable::class.java) {
        injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
      }
    }
  }

  private fun assertFailsOnPreviewParameterInJava(parameterName: String) {
    require(parameterName != "device")
    rule.fixture.configureByText(
      "Test.java",
      // language=java
      """
          package example;
          import test.Preview;

          class Test {
            @Preview($parameterName = "id:device$caret name")
            public void myFun() {}
          }
        """
        .trimIndent(),
    )
    runReadAction {
      assertThrows(Throwable::class.java) {
        injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
      }
    }
  }
}
