// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.compose

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.getPsiElement
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Method
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.EdtAndroidGradleProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunsInEdt
class KotlinMultiplatformRememberInCompositionTest {

  @get:Rule
  val projectRule: EdtAndroidGradleProjectRule = AndroidGradleProjectRule(
    agpVersionSoftwareEnvironment = AgpVersionSoftwareEnvironmentDescriptor.AGP_8_11,
  ).onEdt()

  @Before
  fun setUp() {
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(true)
    projectRule.loadProject(PROJECT_PATH)
  }

  @After
  fun tearDown() {
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
  }

  @Test
  fun `FocusRequester triggers RememberInComposition lint in commonMain`() = runBlockingWithTimeout {
    assertRememberInCompositionWarning(FOCUS_REQUESTER_METHOD)
  }

  @Test
  fun `movableContentOf triggers RememberInComposition lint in commonMain`() = runBlockingWithTimeout {
    assertRememberInCompositionWarning(MOVABLE_CONTENT_METHOD)
  }

  @Test
  fun `Animatable triggers RememberInComposition lint in commonMain`() = runBlockingWithTimeout {
    assertRememberInCompositionWarning(ANIMATABLE_METHOD)
  }

  @Test
  fun `remember does not trigger RememberInComposition lint in commonMain`() = runBlockingWithTimeout {
    assertRememberInCompositionWarning(CORRECT_METHOD, expectWarning = false)
  }

  private fun assertRememberInCompositionWarning(methodLocator: Method, expectWarning: Boolean = true) {
    val testMethod = projectRule.project.getPsiElement(methodLocator)
    assertNotNull(testMethod, "Could not find PSI element for method: ${methodLocator.methodName}")

    val virtualFile = testMethod.containingFile.virtualFile
    projectRule.fixture.openFileInEditor(virtualFile)

    val warnings = projectRule.fixture.doHighlighting(HighlightSeverity.WARNING)
      .mapNotNull { it.description }

    val hasWarning = warnings.any { it.contains("without using `remember`") }

    if (expectWarning) assertTrue(hasWarning, "Expected RememberInComposition warning, but found: $warnings")
    else assertFalse(hasWarning, "Expected no warnings, but found: $warnings")
  }
}

private const val PROJECT_PATH = "projects/androidKotlinMultiplatformRememberInComposition"
private val FOCUS_REQUESTER_METHOD = Method("org.example.project.FocusRequesterTestKt", "FocusRequesterTest")
private val MOVABLE_CONTENT_METHOD = Method("org.example.project.MovableContentTestKt", "MovableContentTest")
private val ANIMATABLE_METHOD = Method("org.example.project.AnimatableTestKt", "AnimatableTest")
private val CORRECT_METHOD = Method("org.example.project.ValidRememberUsageTestKt", "ValidRememberUsageTest")