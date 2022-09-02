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
package com.android.tools.idea.compose.annotator

import com.android.tools.idea.compose.annotator.check.common.BadType
import com.android.tools.idea.compose.annotator.check.common.CheckResult
import com.android.tools.idea.compose.annotator.check.common.Failure
import com.android.tools.idea.compose.annotator.check.common.Missing
import com.android.tools.idea.compose.annotator.check.common.Repeated
import com.android.tools.idea.compose.annotator.check.common.Unknown
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.runInEdtAndGet
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.intellij.lang.annotations.Language
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class PreviewAnnotationCheckTest {

  @get:Rule val rule = AndroidProjectRule.inMemory()

  val fixture
    get() = rule.fixture

  @Before
  fun setup() {
    fixture.stubPreviewAnnotation()
    fixture.stubComposableAnnotation()
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_MULTIPREVIEW.clearOverride()
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }

  @Test
  fun testGoodAnnotations() {
    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320,id=fooBar 123")
        @Composable
        fun myFun() {}
      """.trimIndent()
      )
    assert(result.issues.isEmpty())

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "   ")
        @Composable
        fun myFun() {}
      """.trimIndent()
      )
    assert(result.issues.isEmpty())
  }

  @Test
  fun testAnnotationWithIssues() {
    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:shape=Tablet,shape=Normal,width=qwe,unit=sp,dpi=320,madeUpParam")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(6, result.issues.size)
    assertEquals(
      listOf(
        BadType::class,
        BadType::class,
        BadType::class,
        Unknown::class,
        Repeated::class,
        Missing::class
      ),
      result.issues.map { it::class }
    )
    assertEquals("spec:shape=Normal,width=411,unit=dp,dpi=320,height=891", result.proposedFix)

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = " abc ")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals(
      "Must be a Device ID or a Device specification: \"id:...\", \"spec:...\".",
      result.issues[0].parameterName
    )
    assertEquals("id:pixel_5", result.proposedFix)
  }

  @Test
  fun testAnnotationWithDeviceSpecLanguageIssues() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)

    // Missing height, no common unit defined
    var result: CheckResult =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:width=100,isRound=no")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(
      listOf(BadType::class, BadType::class, Missing::class),
      result.issues.map { it::class }
    )
    assertEquals("spec:width=100dp,isRound=false,height=891dp", result.proposedFix)

    // First valid unit is `dp`, other dimension parameters should have the same unit
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:width=100dp,height=400.56px,chinSize=30")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(listOf(BadType::class, BadType::class), result.issues.map { it::class })
    assertEquals("spec:width=100dp,height=400.6dp,chinSize=30dp", result.proposedFix)
  }

  @Test
  fun testFailure() {
    val vFile =
      rule.fixture.addFileToProject(
          "test.kt",
          // language=kotlin
          """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        @Composable
        fun myFun() {}
""".trimIndent()
        )
        .virtualFile

    val annotationEntry = runWriteActionAndWait {
      rule.fixture.openFileInEditor(vFile)
      rule.fixture.moveCaret("@Prev|iew").parentOfType<KtAnnotationEntry>()
    }
    assertNotNull(annotationEntry)

    val result = PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(annotationEntry)
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    assertEquals("No read access", (result.issues[0] as Failure).failureMessage)
  }

  @Test
  fun testBadTarget() {
    StudioFlags.COMPOSE_MULTIPREVIEW.override(true)
    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        class myNotAnnotation() {}
      """.trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    assertEquals(
      "Preview target must be a composable function or an annotation class",
      (result.issues[0] as Failure).failureMessage
    )
  }

  @Test
  fun testMultipreviewAnnotation_flagEnabled() {
    StudioFlags.COMPOSE_MULTIPREVIEW.override(true)
    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        annotation class myAnnotation() {}
      """.trimIndent()
      )
    assert(result.issues.isEmpty())
  }

  @Test
  fun testMultipreviewAnnotation_flagDisabled() {
    StudioFlags.COMPOSE_MULTIPREVIEW.override(false)
    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview

        @Preview(device = "spec:shape=Normal,width=1080,height=1920,unit=px,dpi=320")
        annotation class myAnnotation() {}
      """.trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    assertEquals(
      "Preview target must be a composable function",
      (result.issues[0] as Failure).failureMessage
    )
  }

  @Test
  fun testDeviceIdFailure() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)

    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "id:device_1")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Failure::class, result.issues[0]::class)
    // Memory test by default do not include an instance of the Sdk
    assertEquals("Default Device: pixel_5 not found", (result.issues[0] as Failure).failureMessage)
  }

  @Test
  fun testDeviceIdCheck() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "id:device_1")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("id:pixel_5", result.proposedFix)

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "id:pixel_4")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertFalse(result.hasIssues)
  }

  @Test
  fun testDeviceNameCheck() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "name:Nexus 11")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("id:pixel_5", result.proposedFix)

    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "name:Nexus 10")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertFalse(result.hasIssues)
  }

  @Test
  fun testDeviceNameCheckWithNoDevicesFails() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)

    val result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "name:Nexus 11")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals("Default Device: pixel_5 not found", (result.issues[0] as Failure).failureMessage)
  }

  @Test
  fun testParentIdCheck() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    runWriteActionAndWait { Sdks.addLatestAndroidSdk(rule.fixture.projectDisposable, rule.module) }

    // Provide an incorrect ID
    var result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:parent=device_1")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(BadType::class, result.issues[0]::class)
    assertEquals("spec:parent=pixel_5", result.proposedFix)

    // Correct parent ID and orientation
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:parent=pixel_4,orientation=portrait")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(0, result.issues.size)

    // Correct parent ID, orientation and unknown
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:parent=pixel_6,orientation=portrait,foo=bar")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("spec:parent=pixel_6,orientation=portrait", result.proposedFix)

    // Correct parent ID, with all other parameters, parent takes priority
    result =
      addKotlinFileAndCheckPreviewAnnotation(
        """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:parent=pixel_4_xl,width=1080px,height=1920px,isRound=true,dpi=320,chinSize=20px,orientation=portrait")
        @Composable
        fun myFun() {}
""".trimIndent()
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
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "spec:width=1080px,parent=pixel_4_xl")
        @Composable
        fun myFun() {}
""".trimIndent()
      )
    assertEquals(1, result.issues.size)
    assertEquals(Unknown::class, result.issues[0]::class)
    assertEquals("spec:parent=pixel_4_xl", result.proposedFix)
  }

  /**
   * Adds file with the given [fileContents] and runs
   * [PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded] on the first Preview annotation found.
   */
  private fun addKotlinFileAndCheckPreviewAnnotation(
    @Language("kotlin") fileContents: String
  ): CheckResult {
    rule.fixture.configureByText(KotlinFileType.INSTANCE, fileContents)

    val annotationEntry = runInEdtAndGet {
      rule.fixture.moveCaret("@Prev|iew").parentOfType<KtAnnotationEntry>()
    }
    assertNotNull(annotationEntry)

    return runReadAction { PreviewAnnotationCheck.checkPreviewAnnotationIfNeeded(annotationEntry) }
  }
}
