/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.preview.util.device.check

import com.android.tools.idea.preview.util.device.check.DeviceSpecCheck.hasIssues
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.moveCaret
import com.android.tools.preview.config.DEFAULT_DEVICE_ID
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.runInEdtAndGet
import kotlin.test.assertNotNull
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class DeviceSpecCheckTest {

  @get:Rule val rule = AndroidProjectRule.inMemory()

  val fixture
    get() = rule.fixture

  private lateinit var inspectionManager: InspectionManager

  @Before
  fun setup() {
    fixture.addFileToProject(
      "src/test/Preview.kt",
      // language=kotlin
      """
    package test

    @Repeatable
    annotation class Preview(
      val device: String = ""
    )
    """
        .trimIndent(),
    )
    inspectionManager = InspectionManager.getInstance(fixture.project)
  }

  @Test
  fun testValidDeviceSpecs() {
    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320,id=fooBar 123")
        fun myFun() {}
      """
          .trimIndent()
      )
    assertTrue(result.issues.isEmpty())

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "   ")
        fun myFun() {}
      """
          .trimIndent()
      )
    assertTrue(result.issues.isEmpty())
  }

  @Test
  fun testValidDeviceSpecs_java() {
    var result =
      addJavaFileAndCheckPreviewAnnotation(
        """
        package example;
        import test.Preview;

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320,id=fooBar 123")
        void myFun() {}
      """
          .trimIndent()
      )
    assertTrue(result.issues.isEmpty())

    result =
      addJavaFileAndCheckPreviewAnnotation(
        """
        package example;
        import test.Preview;

        @Preview(device = "   ")
        void myFun() {}
      """
          .trimIndent()
      )
    assertTrue(result.issues.isEmpty())
  }

  @Test
  fun testDeviceSpecWithIssues() {
    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:shape=Tablet,shape=Normal,width=qwe,unit=sp,dpi=320,madeUpParam")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(6, result.issues.size)
    assertEquals(
      listOf(
        BadType::class,
        BadType::class,
        BadType::class,
        Unknown::class,
        Repeated::class,
        Missing::class,
      ),
      result.issues.map { it::class },
    )
    assertEquals("spec:shape=Normal,width=411,unit=dp,dpi=320,height=891", result.proposedFix)

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = " abc ")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals(
      "Must be a Device ID or a Device specification: \"id:...\", \"spec:...\".",
      result.issues[0].parameterName,
    )
    assertEquals("id:pixel_5", result.proposedFix)
  }

  @Test
  fun testDeviceSpecWithIssues_java() {
    var result =
      addJavaFileAndCheckPreviewAnnotation(
        """
        package example;
        import test.Preview;

        @Preview(device = "spec:shape=Tablet,shape=Normal,width=qwe,unit=sp,dpi=320,madeUpParam")
        void myFun() {}
"""
          .trimIndent()
      )
    assertEquals(6, result.issues.size)
    assertEquals(
      listOf(
        BadType::class,
        BadType::class,
        BadType::class,
        Unknown::class,
        Repeated::class,
        Missing::class,
      ),
      result.issues.map { it::class },
    )
    assertEquals("spec:shape=Normal,width=411,unit=dp,dpi=320,height=891", result.proposedFix)

    result =
      addJavaFileAndCheckPreviewAnnotation(
        """
        package example;
        import test.Preview;

        @Preview(device = " abc ")
        void myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals(
      "Must be a Device ID or a Device specification: \"id:...\", \"spec:...\".",
      result.issues[0].parameterName,
    )
    assertEquals("id:pixel_5", result.proposedFix)
  }

  @Test
  fun testAnnotationWithDeviceSpecLanguageIssues() {
    // Missing height, no common unit defined
    var result: CheckResult =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:width=100,isRound=no")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(
      listOf(BadType::class, BadType::class, Missing::class),
      result.issues.map { it::class },
    )
    assertEquals("spec:width=100dp,isRound=false,height=891dp", result.proposedFix)

    // First valid unit is `dp`, other dimension parameters should have the same unit
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:width=100dp,height=400.56px,chinSize=30")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(listOf(BadType::class, BadType::class), result.issues.map { it::class })
    assertEquals("spec:width=100dp,height=400.6dp,chinSize=30dp", result.proposedFix)
  }

  @Test
  fun testFailure() {
    val vFile =
      rule.fixture
        .addFileToProject(
          "test.kt",
          // language=kotlin
          """
        package example
        import test.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        fun myFun() {}
"""
            .trimIndent(),
        )
        .virtualFile

    val annotation = runWriteActionAndWait {
      rule.fixture.openFileInEditor(vFile)
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<KtAnnotationEntry>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(annotation)

    val result = DeviceSpecCheck.checkDeviceSpec(annotation, DEFAULT_DEVICE_ID)
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    assertEquals("No read access", (result.issues[0] as Failure).failureMessage)
  }

  @Test
  fun testDeviceIdFailure() {
    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "id:device_1")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    // Memory test by default do not include an instance of the Sdk
    assertEquals("Default Device: pixel_5 not found", (result.issues[0] as Failure).failureMessage)
  }

  @Test
  fun testDeviceIdCheck() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "id:device_1")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("id:pixel_5", result.proposedFix)

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "id:pixel_4")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertFalse(result.hasIssues)
  }

  @Test
  fun testDeviceIdCheckWithDefaultDeviceIdOverride() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }
    val defaultDeviceId = "wearos_large_round"

    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "id:device_1")
        fun myFun() {}
"""
          .trimIndent(),
        defaultDeviceId = defaultDeviceId,
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("id:${defaultDeviceId}", result.proposedFix)
  }

  @Test
  fun deprecatedDevicesShouldBeValid() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "id:Nexus 5")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertFalse(result.hasIssues)
  }

  @Test
  fun testDeviceNameCheck() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "name:Nexus 11")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("id:pixel_5", result.proposedFix)

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "name:Pixel Tablet")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertFalse(result.hasIssues)
  }

  @Test
  fun testDeviceNameCheckWithNoDevicesFails() {
    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "name:Nexus 11")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals("Default Device: pixel_5 not found", (result.issues[0] as Failure).failureMessage)
  }

  @Test
  fun testParentIdCheck() {
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    // Provide an incorrect ID
    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:parent=device_1")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(BadType::class, result.issues[0]::class)
    assertEquals("spec:parent=pixel_5", result.proposedFix)

    // Correct parent ID and orientation
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:parent=pixel_4,orientation=portrait")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(0, result.issues.size)

    // Correct parent ID, orientation and unknown
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:parent=pixel_6,orientation=portrait,foo=bar")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("spec:parent=pixel_6,orientation=portrait", result.proposedFix)

    // Correct parent ID, with all other parameters, parent takes priority
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:parent=pixel_4_xl,width=1080px,height=1920px,isRound=true,dpi=320,chinSize=20px,orientation=portrait")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(5, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals(Unknown::class, result.issues[1]::class)
    assertEquals(Unknown::class, result.issues[2]::class)
    assertEquals(Unknown::class, result.issues[3]::class)
    assertEquals(Unknown::class, result.issues[4]::class)
    assertEquals("spec:parent=pixel_4_xl,orientation=portrait", result.proposedFix)

    // Width and parent ID, missing height, parent takes priority
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import test.Preview

        @Preview(device = "spec:width=1080px,parent=pixel_4_xl")
        fun myFun() {}
"""
          .trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("spec:parent=pixel_4_xl", result.proposedFix)
  }

  @Test
  fun testCheckAnnotation() {
    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        package example
        import test.Preview

        @Preview(device = "spec:shape=Tablet,shape=Normal,width=qwe,unit=sp,dpi=320,madeUpParam")
        fun myFun() {}
"""
        .trimIndent(),
    )

    val annotation = runInEdtAndGet {
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<KtAnnotationEntry>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(annotation)

    val problem = runReadAction {
      DeviceSpecCheck.checkAnnotation(annotation, inspectionManager, true)
    }

    assertNotNull(problem)
    assertEquals(
      """
      Bad value type for: shape, width, unit.

      Parameter: shape should be one of: Normal, Round.
      Parameter: width should have Integer value.
      Parameter: unit should be one of: px, dp.

      Unknown parameter: madeUpParam.


      Parameters should not be repeated: shape.


      Missing parameter: height.
    """
        .trimIndent(),
      problem.descriptionTemplate,
    )
    val fix = problem.fixes?.singleOrNull()
    assertNotNull(fix)

    WriteCommandAction.runWriteCommandAction(fixture.project) {
      (fix as LocalQuickFixOnPsiElement).applyFix()
    }
    val fixedAnnotationEntry = runInEdtAndGet {
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<KtAnnotationEntry>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(fixedAnnotationEntry)
    val hasIssues = runReadAction { fixedAnnotationEntry.hasIssues() }
    assertFalse(hasIssues)
  }

  @Test
  fun testCheckAnnotation_java() {
    rule.fixture.configureByText(
      JavaFileType.INSTANCE,
      // language=java
      """
        package example;
        import test.Preview;

        @Preview(device = "spec:shape=Tablet,shape=Normal,width=qwe,unit=sp,dpi=320,madeUpParam")
        void myFun() {}
"""
        .trimIndent(),
    )

    val annotation = runInEdtAndGet {
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<PsiAnnotation>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(annotation)

    val problem = runReadAction {
      DeviceSpecCheck.checkAnnotation(annotation, inspectionManager, true)
    }

    assertNotNull(problem)
    assertEquals(
      """
      Bad value type for: shape, width, unit.

      Parameter: shape should be one of: Normal, Round.
      Parameter: width should have Integer value.
      Parameter: unit should be one of: px, dp.

      Unknown parameter: madeUpParam.


      Parameters should not be repeated: shape.


      Missing parameter: height.
    """
        .trimIndent(),
      problem.descriptionTemplate,
    )
    val fix = problem.fixes?.singleOrNull()
    assertNotNull(fix)

    WriteCommandAction.runWriteCommandAction(fixture.project) {
      (fix as LocalQuickFixOnPsiElement).applyFix()
    }
    val fixedAnnotation = runInEdtAndGet {
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<PsiAnnotation>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(fixedAnnotation)
    val hasIssues = runReadAction { fixedAnnotation.hasIssues() }
    assertFalse(hasIssues)
  }

  /**
   * Adds file with the given [fileContents] and runs [DeviceSpecCheck.checkDeviceSpec] on the first
   * Preview annotation found.
   */
  private fun addKotlinFileAndCheckPreviewAnnotation(
    @Language("kotlin") fileContents: String,
    defaultDeviceId: String = DEFAULT_DEVICE_ID,
  ): CheckResult {
    rule.fixture.configureByText(KotlinFileType.INSTANCE, fileContents)

    val annotation = runInEdtAndGet {
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<KtAnnotationEntry>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(annotation)

    return runReadAction { DeviceSpecCheck.checkDeviceSpec(annotation, defaultDeviceId) }
  }

  /**
   * Adds file with the given [fileContents] and runs [DeviceSpecCheck.checkDeviceSpec] on the first
   * Preview annotation found.
   */
  private fun addJavaFileAndCheckPreviewAnnotation(
    @Language("JAVA") fileContents: String
  ): CheckResult {
    rule.fixture.configureByText(JavaFileType.INSTANCE, fileContents)

    val annotation = runInEdtAndGet {
      rule.fixture
        .moveCaret("@Prev|iew")
        .parentOfType<PsiAnnotation>()
        ?.toUElement(UAnnotation::class.java)
    }
    assertNotNull(annotation)

    return runReadAction { DeviceSpecCheck.checkDeviceSpec(annotation, DEFAULT_DEVICE_ID) }
  }
}
