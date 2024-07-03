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
package com.android.tools.idea.compose.preview

import com.android.SdkConstants
import com.android.flags.junit.FlagRule
import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.surface.NopInteractionHandler
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeScreenViewProvider
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.util.androidFacet
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel

private class TestPreviewElementDataContext(
  private val project: Project,
  private val composePreviewManager: ComposePreviewManager,
  private val previewElement: ComposePreviewElementInstance<*>,
) : DataContext {
  override fun getData(dataId: String): Any? =
    when (dataId) {
      COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
      PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
}

private fun configureLayoutlibSceneManagerForPreviewElement(
  displaySettings: PreviewDisplaySettings,
  layoutlibSceneManager: LayoutlibSceneManager,
) =
  configureLayoutlibSceneManager(
    layoutlibSceneManager,
    showDecorations = displaySettings.showDecoration,
    isInteractive = false,
    requestPrivateClassLoader = false,
    runAtfChecks = false,
    runVisualLinting = false,
    quality = 1f,
  )

/** Converts an [InstructionsPanel] into text that can be easily used in assertions. */
private fun InstructionsPanel.toDisplayText(): String =
  (0 until componentCount)
    .flatMap { getRenderInstructionsForComponent(it) }
    .mapNotNull {
      when (it) {
        is TextInstruction -> it.text
        is NewRowInstruction -> "\n"
        is HyperlinkInstruction -> "[${it.displayText}]"
        else -> null
      }
    }
    .joinToString("")

class ComposePreviewViewImplTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @get:Rule val flagRule = FlagRule(StudioFlags.COMPOSE_PREVIEW_GENERATE_ALL_PREVIEWS_FILE)

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val nopDataProvider = DataProvider { null }

  private val statusManager =
    object : ProjectBuildStatusManager {
      override val isBuilding: Boolean = false
      override val statusFlow: MutableStateFlow<ProjectStatus> =
        MutableStateFlow(ProjectStatus.Ready)
    }
  private lateinit var mainFileSmartPointer: SmartPsiElementPointer<PsiFile>
  private lateinit var previewView: ComposePreviewView
  private lateinit var fakeUi: FakeUi

  @Before
  fun setUp() =
    runBlocking(uiThread) {
      // Setup a fake manifest so rendering works correctly
      val manifest =
        fixture.addFileToProjectAndInvalidate(
          SdkConstants.FN_ANDROID_MANIFEST_XML,
          """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="java.google.simpleapplication">

          <application
              android:allowBackup="true"
              android:label="Simple Composable"
              android:theme="@android:style/Theme.Holo.Light.DarkActionBar" >
              <activity
                  android:name=".MainActivity"
                  android:exported="true"
                  android:label="Simple Composable" >
                  <intent-filter>
                      <action android:name="android.intent.action.MAIN" />

                      <category android:name="android.intent.category.LAUNCHER" />
                  </intent-filter>
              </activity>
          </application>

      </manifest>
    """
            .trimIndent(),
        )
      SourceProviderManager.replaceForTest(
        projectRule.module.androidFacet!!,
        fixture.projectDisposable,
        NamedIdeaSourceProviderBuilder.create("main", manifest.virtualFile.url).build(),
      )

      val psiMainFile =
        fixture.addFileToProject(
          "src/main/Test.kt",
          """
      fun main() {}
    """
            .trimIndent(),
        )

      val navigationHandler = ComposePreviewNavigationHandler()
      val interactionHandler = NopInteractionHandler
      val sceneComponentProvider = ComposeSceneComponentProvider()

      mainFileSmartPointer = SmartPointerManager.createPointer(psiMainFile)

      val mainSurfaceBuilder =
        createMainDesignSurfaceBuilder(
          project,
          navigationHandler,
          interactionHandler,
          nopDataProvider,
          fixture.testRootDisposable,
          sceneComponentProvider,
          ComposeScreenViewProvider(NopComposePreviewManager()),
          { false },
        )
      val composePreviewViewImpl =
        ComposePreviewViewImpl(
          project,
          mainFileSmartPointer,
          statusManager,
          nopDataProvider,
          mainSurfaceBuilder,
          fixture.testRootDisposable,
        )

      // Create VisualLintService early to avoid it being created at the time of project disposal
      VisualLintService.getInstance(project)

      previewView = composePreviewViewImpl
      fakeUi =
        FakeUi(
          JPanel().apply {
            layout = BorderLayout()
            size = Dimension(1000, 800)
            add(composePreviewViewImpl.component, BorderLayout.CENTER)
          },
          1.0,
          true,
        )
      previewView.component.findDescendant<SceneViewPanel>()!!.setNoComposeHeadersForTests()
      fakeUi.root.validate()
    }

  /**
   * Updates the [ComposePreviewView] with the preview elements provided by the [previewProvider]. A
   * [composePreviewManager] is needed to determine the state.
   */
  private fun updatePreviewAndRefreshWithProvider(
    previewProvider: PreviewElementProvider<PsiComposePreviewElementInstance>,
    composePreviewManager: ComposePreviewManager,
    surface: NlDesignSurface = previewView.mainSurface,
  ) {
    val testPreviewElementModelAdapter =
      object : ComposePreviewElementModelAdapter() {
        override fun toXml(previewElement: PsiComposePreviewElementInstance) =
          """
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="wrap_content"
  android:layout_height="wrap_content"
  android:text="Hello world ${previewElement.displaySettings.name}" />
"""

        override fun createDataContext(previewElement: PsiComposePreviewElementInstance) =
          TestPreviewElementDataContext(project, composePreviewManager, previewElement)
      }
    runBlocking(workerThread) {
      surface.updatePreviewsAndRefresh(
        tryReusingModels = true,
        reinflate = true,
        previewProvider.previewElements().toList(),
        Logger.getInstance(ComposePreviewViewImplTest::class.java),
        mainFileSmartPointer.element!!,
        fixture.testRootDisposable,
        EmptyProgressIndicator(),
        {
          previewView.hasRendered = true
          previewView.hasContent = true
        },
        testPreviewElementModelAdapter,
        DefaultModelUpdater(),
        navigationHandler = ComposePreviewNavigationHandler(),
        ::configureLayoutlibSceneManagerForPreviewElement,
        null,
      )
    }
    ApplicationManager.getApplication().invokeAndWait {
      previewView.updateVisibilityAndNotifications()
      fakeUi.root.validate()
    }
  }

  @Test
  fun `empty preview state when generate all previews is disabled`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_ALL_PREVIEWS_FILE.override(false)
    ApplicationManager.getApplication().invokeAndWait {
      previewView.hasRendered = true
      previewView.hasContent = false
      previewView.updateVisibilityAndNotifications()
      fakeUi.root.validate()
    }

    assertEquals(
      """
      No preview found.
      Add preview by annotating Composables with @Preview
      [Using the Compose preview]
    """
        .trimIndent(),
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!.toDisplayText(),
    )
  }

  @Test
  fun `empty preview state when generate all previews is enabled`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_ALL_PREVIEWS_FILE.override(true)
    ApplicationManager.getApplication().invokeAndWait {
      previewView.hasRendered = true
      previewView.hasContent = false
      previewView.updateVisibilityAndNotifications()
      fakeUi.root.validate()
    }

    assertEquals(
      """
      No preview found.
      Add preview by annotating Composables with @Preview
      [Using the Compose preview]
      [Generate Compose Previews for this file]
    """
        .trimIndent(),
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!.toDisplayText(),
    )
  }

  @Test
  fun `test compilation error state`() {
    ApplicationManager.getApplication().invokeAndWait {
      previewView.hasRendered = true
      previewView.hasContent = false
      statusManager.statusFlow.value = ProjectStatus.NeedsBuild
      previewView.updateVisibilityAndNotifications()
      fakeUi.root.validate()
    }

    val shortcutRegEx = Regex("\\(.+.\\)")
    val instructionsText =
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!
        .toDisplayText()
        .replace(shortcutRegEx, "(shortcut)")
    assertEquals(
      """
      A successful build is needed before the preview can be displayed
      [Build & Refresh... (shortcut)]
    """
        .trimIndent(),
      instructionsText,
    )
  }

  @Test
  fun `create compose view with two elements`() {
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
          "Fake Test Method",
          "Display1",
        ),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2"),
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<PsiComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<PsiComposePreviewElementInstance> =
          previews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)
    ApplicationManager.getApplication().invokeAndWait {
      previewView.mainSurface.zoomController.zoomToFit()
      fakeUi.root.validate()
    }

    assertEquals(2, fakeUi.findAllComponents<SceneViewPeerPanel>() { it.isShowing }.size)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display1" }!!.isShowing)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display2" }!!.isShowing)
  }

  @Test
  fun `open and close bottom panel`() {
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
          "Fake Test Method",
          "Display1",
        ),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2"),
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<PsiComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<PsiComposePreviewElementInstance> =
          previews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)

    ApplicationManager.getApplication().invokeAndWait {
      previewView.bottomPanel =
        JPanel().apply {
          layout = BorderLayout()
          size = Dimension(100, 100)
          add(JLabel("Bottom panel"), BorderLayout.CENTER)
        }
      fakeUi.root.validate()
    }
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Bottom panel" }!!.isShowing)

    ApplicationManager.getApplication().invokeAndWait {
      previewView.bottomPanel = null
      fakeUi.root.validate()
    }
    assertNull(fakeUi.findComponent<JLabel> { it.text == "Bottom panel" })
  }

  @Test
  fun `verify refresh cancellation`() {
    ApplicationManager.getApplication().invokeAndWait {
      previewView.onRefreshCancelledByTheUser()
      fakeUi.root.validate()
    }
    assertEquals(
      """
      Refresh was cancelled and needs to be completed before the preview can be displayed
      [Build & Refresh... (Ctrl+Shift+F5)]
    """
        .trimIndent(),
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!.toDisplayText(),
    )
  }

  @Test
  fun `verify refresh cancellation with content available does not show error panel`() {
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
          "Fake Test Method",
          "Display1",
        ),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2"),
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<PsiComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<PsiComposePreviewElementInstance> =
          previews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)
    ApplicationManager.getApplication().invokeAndWait {
      previewView.onRefreshCancelledByTheUser()
      fakeUi.root.validate()
    }

    assertNull(fakeUi.findComponent<InstructionsPanel> { it.isShowing })
  }
}
