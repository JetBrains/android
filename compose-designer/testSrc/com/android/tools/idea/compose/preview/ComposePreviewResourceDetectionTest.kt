/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.resources.ResourceType
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposePreviewResourceDetectionTest {

  private val logger = Logger.getInstance(ComposePreviewResourceDetectionTest::class.java)

  @get:Rule val projectRule = ComposeProjectRule(AndroidProjectRule.withSdk())

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private val buildSystemServices
    get() = projectRule.buildSystemServices

  private lateinit var mainSurface: NlDesignSurface
  private lateinit var preview: ComposePreviewRepresentation
  private lateinit var composeView: TestComposePreviewView
  private lateinit var newModelAddedLatch: CountDownLatch

  @Before
  fun setup() {
    logger.setLevel(LogLevel.ALL)
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(FastPreviewManager::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(RenderingBuildStatus::class.java).setLevel(LogLevel.ALL)

    mainSurface = NlSurfaceBuilder.builder(project, fixture.testRootDisposable, false).build()
    Disposer.register(fixture.testRootDisposable, mainSurface)
  }

  @After
  fun tearDown() {
    if (::preview.isInitialized) {
      preview.onDeactivate()
    }
  }

  @Test
  fun testReproduceResourceMissingInPreview() = runTest {
    // 1. Setup: Create AndroidManifest.xml to define package name
    runWriteActionAndWait {
      fixture.addFileToProjectAndInvalidate(
        "AndroidManifest.xml",
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="com.example">
            <application>
            </application>
        </manifest>
        """
          .trimIndent(),
      )
    }

    val facet = AndroidFacet.getInstance(projectRule.module)!!
    logger.info("Resource folders: ${org.jetbrains.android.facet.ResourceFolderManager.getInstance(facet).folders}")

    // Create a dummy strings.xml
    val stringsXml = runWriteActionAndWait {
      fixture.addFileToProjectAndInvalidate(
        "res/values/strings.xml",
        """
        <resources>
            <string name="app_name">MyApp</string>
        </resources>
        """
          .trimIndent(),
      )
    }

    runWriteActionAndWait {
      stringsXml.virtualFile.parent.refresh(false, true)
      stringsXml.virtualFile.parent.parent.refresh(false, true)
    }

    // Flush EDT events to propagate VFS changes
    withContext(Dispatchers.EDT) { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }

    // Wait for resource repository to detect the new strings.xml
    waitForResourceRepositoryUpdates(facet)
    org.jetbrains.android.facet.ResourceFolderManager.getInstance(facet).checkForChanges()
    StudioResourceRepositoryManager.getInstance(facet).resetAllCaches()
    val moduleResources = StudioResourceRepositoryManager.getInstance(facet).moduleResources

    // 2. Create Compose file with unresolved R.string.new_string
    val composeFile = runWriteActionAndWait {
      fixture.addFileToProjectAndInvalidate(
        "src/com/example/MainActivity.kt",
        """
        package com.example

        import com.example.R
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.material.Text
        import androidx.compose.ui.res.stringResource

        @Preview
        @Composable
        fun MyPreview() {
            Text(stringResource(R.string.new_string))
        }
        """
          .trimIndent(),
      )
    }

    fixture.configureFromExistingVirtualFile(composeFile.virtualFile)

    newModelAddedLatch = CountDownLatch(1)
    val collectJob = launch {
      mainSurface.modelChanged.collect { models ->
        if (models.isNotEmpty()) {
          newModelAddedLatch.countDown()
        }
      }
    }

    // Initialize ComposePreviewRepresentation
    composeView = TestComposePreviewView(mainSurface)
    preview = ComposePreviewRepresentation(composeFile) { _, _, _, _, _, _ -> composeView }
    Disposer.register(fixture.testRootDisposable, preview)

    // Activate preview and "compile" (simulate build success)
    withContext(Dispatchers.Default) {
      logger.info("compile")
      buildSystemServices.simulateArtifactBuild(ProjectSystemBuildManager.BuildStatus.SUCCESS)
      logger.info("activate")
      preview.onActivate()

      newModelAddedLatch.await()
      collectJob.cancel()
      delayWhileRefreshingOrDumb()
    }

    // Verify it rendered (even if with errors)
    assertTrue(composeView.hasContent)

    // Trigger highlighting to update intentions and apply quickfix on EDT
    withContext(Dispatchers.EDT) {
      PsiManager.getInstance(project).dropResolveCaches()
      fixture.doHighlighting()

      // Find the caret position for "new_string"
      val fileText = composeFile.text
      val offset = fileText.indexOf("new_string")
      assertTrue("Could not find 'new_string' in file", offset >= 0)
      fixture.editor.caretModel.moveToOffset(offset)

      // Find the quickfix
      val intentions = fixture.availableIntentions
      val quickFix = intentions.find { it.text.startsWith("Create string value resource") }
      if (quickFix == null) {
        val packageName = projectRule.module.getModuleSystem().getPackageName()
        logger.info("Package name: $packageName")

        val highlights = fixture.doHighlighting()
        logger.info("Highlight errors: ${highlights.map { "${it.description} (${it.severity})" }}")
        logger.info("Available intentions: ${intentions.map { it.text }}")
      }
      assertNotNull("Create string value resource quickfix not found", quickFix)

      // 3. Apply QuickFix
      WriteCommandAction.runWriteCommandAction(project) { quickFix!!.invoke(project, fixture.editor, composeFile) }
    }

    // Flush EDT events and save documents to propagate VFS changes from quickfix
    withContext(Dispatchers.EDT) {
      com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveAllDocuments()
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    // Wait for resource repository to detect the new resource
    waitForResourceRepositoryUpdates(facet)

    // 4. Verify Resource Creation in strings.xml
    val updatedStringsXml = PsiManager.getInstance(project).findFile(stringsXml.virtualFile)!!
    val updatedText = updatedStringsXml.text
    assertTrue(
      "Resource 'new_string' was not created in strings.xml. Current text:\n$updatedText",
      updatedText.contains("name=\"new_string\""),
    )

    // 5. Verify Resource is in ResourceRepository
    val resources =
      moduleResources.getResources(com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO, ResourceType.STRING, "new_string")
    assertThat(resources).isNotEmpty()

    // 6. Wait for the automatic refresh triggered by resource change to complete
    withContext(Dispatchers.Default) {
      kotlinx.coroutines.delay(500)
      delayWhileRefreshingOrDumb()
    }

    // 7. Trigger highlighting again to verify R.string.new_string resolves now
    withContext(Dispatchers.EDT) {
      PsiManager.getInstance(project).dropResolveCaches()
      val highlights = fixture.doHighlighting()
      val hasUnresolvedNewString =
        highlights.any {
          it.description?.contains("Unresolved reference 'new_string'") == true ||
            it.description?.contains("Unresolved reference 'string'") == true
        }
      assertFalse("R.string.new_string should be resolved now. Highlights: ${highlights.map { it.description }}", hasUnresolvedNewString)
    }

    // 8. Check render results for errors.
    val debugStatus = preview.debugStatusForTesting()

    val resourceErrors =
      debugStatus.renderResult
        .flatMap { it.logger.messages }
        .filter { it.html.contains("Failed to resolve") || it.html.contains("Resource not found") || it.html.contains("new_string") }

    assertThat(resourceErrors).isEmpty()
  }

  private suspend fun delayWhileRefreshingOrDumb() {
    delayUntilCondition(250) { !(preview.status().isRefreshing || DumbService.getInstance(fixture.project).isDumb) }
  }
}
