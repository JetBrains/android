/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.android.dom.inspections.AndroidUnresolvableTagInspection
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Tests for functions defined in `MavenImportUtils.kt`.
 */
@RunsInEdt
class MavenImportUtilsKtTest {
  private val projectRule = AndroidProjectRule.withAndroidModel(
    createAndroidProjectBuilderForDefaultTestProjectStructure().let { builder ->
      builder.copy(agpProjectFlags = {
        builder.agpProjectFlags.invoke(this).copy(useAndroidX = true)
      })
    }
  )
  private lateinit var tracker: TestUsageTracker

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(
      MavenClassRegistryManager::class.java,
      fakeMavenClassRegistryManager,
      projectRule.fixture.testRootDisposable
    )
    tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
  }

  @After
  fun tearDown() {
    tracker.close()
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun verifyExpectedAnalytics_resolveCode() {
    val psiFile = projectRule.fixture.loadNewFile("app/src/main/java/test/pkg/imports/MainActivity2.kt", """
      package test.pkg.imports
      val view = PreviewView() // Here PreviewView is an unresolvable symbol
      """.trimIndent())

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    val action = AndroidMavenImportIntentionAction()
    val element = projectRule.fixture.moveCaret("PreviewView|")
    val available = action.isAvailable(projectRule.project, projectRule.fixture.editor, element)
    assertThat(available).isTrue()
    assertThat(action.text).isEqualTo("Add dependency on androidx.camera:camera-view (alpha) and import")

    action.perform(projectRule.project, projectRule.fixture.editor, element, false)
    verify("androidx.camera:camera-view")
  }

  @Test
  fun verifyExpectedAnalytics_resolveXmlTag() {
    projectRule.fixture.enableInspections(AndroidUnresolvableTagInspection::class.java)
    val psiFile = projectRule.fixture.addFileToProject(
      "res/layout/my_layout.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <FrameLayout android:id="@+id/container">
        <${
        "androidx.camera.view.PreviewView"
          .highlightedAs(HighlightSeverity.ERROR, "Cannot resolve class androidx.camera.view.PreviewView")
      } android:id="@+id/previewView" />
        </FrameLayout>
      """.trimIndent()
    )

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.checkHighlighting(true, false, false)
    projectRule.fixture.moveCaret("Preview|View")
    val action = projectRule.fixture.getIntentionAction("Add dependency on androidx.camera:camera-view (alpha)")!!

    WriteCommandAction.runWriteCommandAction(projectRule.project, Runnable {
      action.invoke(projectRule.project, projectRule.fixture.editor, projectRule.fixture.file)
    })

    verify("androidx.camera:camera-view")
  }

  @Test
  fun displayPreviewType_alpha() {
    val text = flagPreview("androidx.camera:camera-view", "1.0.0-alpha22")
    assertThat(text).isEqualTo("androidx.camera:camera-view (alpha)")
  }

  @Test
  fun displayPreviewType_beta() {
    val text = flagPreview("androidx.camera:camera-view", "1.0.0-beta01")
    assertThat(text).isEqualTo("androidx.camera:camera-view (beta)")
  }

  @Test
  fun displayPreviewType_none() {
    val text = flagPreview("androidx.camera:camera-view", "1.0.0")
    assertThat(text).isEqualTo("androidx.camera:camera-view")
  }

  private fun verify(artifactId: String) {
    val event = tracker.usages
      .map { it.studioEvent }
      .filter { it.kind == AndroidStudioEvent.EventKind.SUGGESTED_IMPORT_EVENT }
      .map { it.suggestedImportEvent }
      .single()

    assertThat(event.artifactId).isEqualTo(artifactId)
  }
}