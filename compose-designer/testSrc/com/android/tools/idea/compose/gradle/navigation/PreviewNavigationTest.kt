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
package com.android.tools.idea.compose.gradle.navigation

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.gradle.activateAndWaitForRender
import com.android.tools.idea.compose.gradle.clickPreviewImage
import com.android.tools.idea.compose.gradle.clickPreviewName
import com.android.tools.idea.compose.gradle.preview.TestComposePreviewView
import com.android.tools.idea.compose.gradle.preview.displayName
import com.android.tools.idea.compose.preview.ComposePreviewRepresentation
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.SingleComposePreviewElementInstance
import com.android.tools.idea.compose.preview.navigation.findComponentHits
import com.android.tools.idea.compose.preview.navigation.findNavigatableComponentHit
import com.android.tools.idea.compose.preview.parseViewInfo
import com.android.tools.idea.compose.preview.renderer.renderPreviewElementForResult
import com.android.tools.idea.compose.preview.util.getRootComponent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.CountDownLatch
import javax.swing.JPanel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class TestNavigationHandler(expectedInvocations: Int) : NavigationHandler {
  /**
   * [CountDownLatch] useful to verify that the suspendable [handleNavigate] methods are invoked as
   * many times as expected.
   */
  var expectedInvocationsCountDownLatch = CountDownLatch(expectedInvocations)

  fun resetExpectedInvocations(newExpectedInvocations: Int) {
    assertEquals(0, expectedInvocationsCountDownLatch.count)
    expectedInvocationsCountDownLatch = CountDownLatch(newExpectedInvocations)
  }

  override suspend fun handleNavigate(
    sceneView: SceneView,
    x: Int,
    y: Int,
    requestFocus: Boolean
  ): Boolean {
    assertTrue(expectedInvocationsCountDownLatch.count > 0)
    expectedInvocationsCountDownLatch.countDown()
    return true
  }

  override suspend fun handleNavigate(sceneView: SceneView, requestFocus: Boolean): Boolean {
    assertTrue(expectedInvocationsCountDownLatch.count > 0)
    expectedInvocationsCountDownLatch.countDown()
    return true
  }

  override fun dispose() {}
}

class PreviewNavigationTest {
  private val LOG = Logger.getInstance(PreviewNavigationTest::class.java)

  private val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  @get:Rule val rule = RuleChain(projectRule, FlagRule(StudioFlags.COMPOSE_PREVIEW_SELECTION, true))
  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testComposableNavigation() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.mainModule

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.TwoElementsPreview"
        )
      )
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          // Find the boundaries for the root element. This will cover the whole layout
          val bounds = parseViewInfo(rootView, logger = LOG).map { it.bounds }.first()

          // Check clicking outside of the boundaries
          assertTrue(findComponentHits(module, rootView, -30, -30).isEmpty())
          assertNull(findNavigatableComponentHit(module, rootView, -30, -30))
          assertTrue(findComponentHits(module, rootView, -1, 0).isEmpty())
          assertTrue(findComponentHits(module, rootView, bounds.right * 2, 10).isEmpty())
          assertTrue(findComponentHits(module, rootView, 10, bounds.bottom * 2).isEmpty())

          // Check filtering
          assertNotNull(findNavigatableComponentHit(module, rootView, 0, 0))
          assertNull(findNavigatableComponentHit(module, rootView, 0, 0) { false })

          // Click the Text("Hello 2") by clicking (0, 0)
          // The hits will be, in that other: Text > Column > MaterialTheme
          assertEquals(
            """
            MainActivity.kt:50
            MainActivity.kt:49
            MainActivity.kt:48
          """
              .trimIndent(),
            findComponentHits(module, rootView, 0, 0)
              .filter { it.fileName == "MainActivity.kt" }
              .joinToString("\n") { "${it.fileName}:${it.lineNumber}" }
          )

          // Click the Button by clicking (0, bounds.bottom / 4)
          // We aim for somewhere inside the button (hence the /4) and not the border since
          // different environments (Bazel vs Local) might have slightly different targets.
          // The hits will be, in that other: Button > Column > MaterialTheme
          assertEquals(
            """
            MainActivity.kt:51
            MainActivity.kt:49
            MainActivity.kt:48
          """
              .trimIndent(),
            findComponentHits(module, rootView, 0, bounds.bottom - bounds.bottom / 4)
              .filter { it.fileName == "MainActivity.kt" }
              .joinToString("\n") { "${it.fileName}:${it.lineNumber}" }
          )
        }
      }
      .join()
  }

  /** Checks the rendering of the default `@Preview` in the Compose template. */
  @Test
  fun testInProjectNavigation() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.mainModule

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.NavigatablePreview"
        )
      )
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          val descriptor =
            findNavigatableComponentHit(module, rootView, 0, 0) { it.fileName == "MainActivity.kt" }
              as OpenFileDescriptor
          assertEquals("MainActivity.kt", descriptor.file.name)
          // TODO(b/156744111)
          // assertEquals(46, descriptor.line)

          val descriptorInOtherFile =
            findNavigatableComponentHit(module, rootView, 0, 0) as OpenFileDescriptor
          assertEquals("OtherPreviews.kt", descriptorInOtherFile.file.name)
          // TODO(b/156744111)
          // assertEquals(31, descriptor.line)
        }
      }
      .join()
  }

  /**
   * Regression test for b/157129712 where we would navigate to the wrong file when the file names
   * were equal.
   */
  @Test
  fun testDuplicateFileNavigation() {
    val facet = projectRule.androidFacet(":app")
    val module = facet.mainModule

    renderPreviewElementForResult(
        facet,
        SingleComposePreviewElementInstance.forTesting(
          "google.simpleapplication.MainActivityKt.OnlyATextNavigation"
        )
      )
      .thenAccept { renderResult ->
        val rootView = renderResult!!.rootViews.single()!!
        ReadAction.run<Throwable> {
          // We click a Text() but we should not navigate to the local Text.kt file since it's not
          // related to the androidx.compose.ui.foundation.Text
          // Assert disabled for dev16 because of b/162066489
          // assertTrue(findComponentHits(module, rootView, 2, 2).any { it.fileName == "Text.kt" })
          assertTrue(
            (findNavigatableComponentHit(module, rootView, 2, 2) as OpenFileDescriptor).file.name ==
              "MainActivity.kt"
          )
        }
      }
      .join()
  }

  @Test
  fun testPreviewNavigation_nameLabelInteraction() {
    val mainFile =
      project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }

    // Create a preview representation with an associated fakeUi
    val myNavigationHandler = TestNavigationHandler(1)
    val previewView =
      TestComposePreviewView(fixture.testRootDisposable, project, myNavigationHandler)
    val composePreviewRepresentation =
      ComposePreviewRepresentation(psiMainFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        previewView
      }
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)
    lateinit var fakeUi: FakeUi
    runInEdtAndWait {
      fakeUi =
        FakeUi(
          JPanel().apply {
            layout = BorderLayout()
            size = Dimension(1000, 800)
            add(previewView, BorderLayout.CENTER)
          },
          1.0,
          true
        )
      fakeUi.root.validate()
    }
    runBlocking { composePreviewRepresentation.activateAndWaitForRender(fakeUi) }

    val panels = fakeUi.findAllComponents<SceneViewPeerPanel>()
    val sceneViewPanel = panels.single { it.displayName == "DefaultPreview" }

    // Click the label and verify that navigation was called
    assertEquals(1, myNavigationHandler.expectedInvocationsCountDownLatch.count)
    fakeUi.clickPreviewName(sceneViewPanel)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)
    assertEquals(0, sceneViewPanel.sceneView.surface.selectionModel.selection.size)
  }

  @Test
  fun testPreviewNavigation_imageInteraction() {
    val mainFile =
      project
        .guessProjectDir()!!
        .findFileByRelativePath(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)!!
    val psiMainFile = runReadAction { PsiManager.getInstance(project).findFile(mainFile)!! }

    // Create a preview representation with an associated fakeUi
    val myNavigationHandler = TestNavigationHandler(1)
    val previewView =
      TestComposePreviewView(
        fixture.testRootDisposable,
        project,
        myNavigationHandler,
      )
    val composePreviewRepresentation =
      ComposePreviewRepresentation(psiMainFile, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
        previewView
      }
    Disposer.register(fixture.testRootDisposable, composePreviewRepresentation)

    lateinit var fakeUi: FakeUi
    runInEdtAndWait {
      fakeUi =
        FakeUi(
          JPanel().apply {
            layout = BorderLayout()
            size = Dimension(1000, 800)
            add(previewView, BorderLayout.CENTER)
          },
          1.0,
          true
        )
      fakeUi.root.validate()
    }

    // Now modify some parts of the previewView according to the FakeUi and the
    // PreviewRepresentation
    previewView.interactionPaneProvider = { fakeUi.root as JPanel }
    previewView.delegateInteractionHandler.delegate =
      composePreviewRepresentation.staticPreviewInteractionHandler

    runBlocking { composePreviewRepresentation.activateAndWaitForRender(fakeUi) }

    val panels = fakeUi.findAllComponents<SceneViewPeerPanel>()
    val sceneViewPanel = panels.single { it.displayName == "DefaultPreview" }
    val otherSceneViewPanel = panels.single { it.displayName == "TwoElementsPreview" }

    // Left-click on a preview and verify that navigation and selection happened
    assertEquals(1, myNavigationHandler.expectedInvocationsCountDownLatch.count)
    assertEquals(0, sceneViewPanel.sceneView.surface.selectionModel.selection.size)
    fakeUi.clickPreviewImage(sceneViewPanel)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(
      sceneViewPanel.sceneView.getRootComponent(),
      sceneViewPanel.sceneView.surface.selectionModel.selection.single()
    )
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Left-click on an unselected preview should navigate and should set selection to only that
    // preview
    myNavigationHandler.resetExpectedInvocations(1)
    fakeUi.clickPreviewImage(otherSceneViewPanel)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(
      otherSceneViewPanel.sceneView.getRootComponent(),
      sceneViewPanel.sceneView.surface.selectionModel.selection.single()
    )
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Shift + Left-click on an unselected Preview shouldn't navigate and should add the preview to
    // the selections
    myNavigationHandler.resetExpectedInvocations(0)
    fakeUi.clickPreviewImage(sceneViewPanel, pressingShift = true)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(2, sceneViewPanel.sceneView.surface.selectionModel.selection.size)
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Left-click on a selected Preview should navigate and shouldn't affect selections
    myNavigationHandler.resetExpectedInvocations(1)
    fakeUi.clickPreviewImage(sceneViewPanel)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(2, sceneViewPanel.sceneView.surface.selectionModel.selection.size)
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Right-click on a selected Preview shouldn't navigate and shouldn't affect selections
    myNavigationHandler.resetExpectedInvocations(0)
    fakeUi.clickPreviewImage(otherSceneViewPanel, rightClick = true)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(2, sceneViewPanel.sceneView.surface.selectionModel.selection.size)
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Shift + Left-click on a selected Preview shouldn't navigate and should remove the preview
    // from the selections
    myNavigationHandler.resetExpectedInvocations(0)
    fakeUi.clickPreviewImage(sceneViewPanel, pressingShift = true)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(
      otherSceneViewPanel.sceneView.getRootComponent(),
      sceneViewPanel.sceneView.surface.selectionModel.selection.single()
    )
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Right-click on an unselected Preview shouldn't navigate and should set selection to only that
    // preview
    myNavigationHandler.resetExpectedInvocations(0)
    fakeUi.clickPreviewImage(sceneViewPanel, rightClick = true)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
    assertEquals(
      sceneViewPanel.sceneView.getRootComponent(),
      sceneViewPanel.sceneView.surface.selectionModel.selection.single()
    )
    assertEquals(0, myNavigationHandler.expectedInvocationsCountDownLatch.count)

    // Finally Left-click on a preview to make sure that the pop-up created due to the previous
    // right-click is closed before finishing the test (not doing this will cause a Project leak).
    myNavigationHandler.resetExpectedInvocations(1)
    fakeUi.clickPreviewImage(otherSceneViewPanel)
    myNavigationHandler.expectedInvocationsCountDownLatch.await()
  }
}
