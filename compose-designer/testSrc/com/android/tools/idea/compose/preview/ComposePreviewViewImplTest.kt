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
import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.common.surface.NopInteractionHandler
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.util.SingleComposePreviewElementInstance
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.SourceProviderManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private class TestPreviewElementDataContext(
  private val project: Project,
  private val composePreviewManager: ComposePreviewManager,
  private val previewElement: ComposePreviewElementInstance
) : DataContext {
  override fun getData(dataId: String): Any? =
    when (dataId) {
      COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
      COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
}

private fun configureLayoutlibSceneManagerForPreviewElement(
  displaySettings: PreviewDisplaySettings,
  layoutlibSceneManager: LayoutlibSceneManager
) =
  configureLayoutlibSceneManager(
    layoutlibSceneManager,
    showDecorations = displaySettings.showDecoration,
    isInteractive = false,
    requestPrivateClassLoader = false
  )

/** Convers and [InstructionsPanel] into text that can be easily used in assertions. */
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
  @get:Rule val edtRule = EdtRule()

  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private val project: Project
    get() = projectRule.project
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val nopDataProvider = DataProvider {}
  private val nopAction =
    object : AnAction(null, null, null) {
      override fun actionPerformed(e: AnActionEvent) {}
    }

  private val statusManager =
    object : ProjectBuildStatusManager {
      override val isBuilding: Boolean = false
      override var status: ProjectStatus = ProjectStatus.Ready
    }
  private lateinit var mainFileSmartPointer: SmartPsiElementPointer<PsiFile>
  private lateinit var previewView: ComposePreviewView
  private lateinit var fakeUi: FakeUi

  @Before
  fun setUp() {
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
    """.trimIndent()
      )
    SourceProviderManager.replaceForTest(
      projectRule.module.androidFacet!!,
      fixture.projectDisposable,
      NamedIdeaSourceProviderBuilder.create("main", manifest.virtualFile.url).build()
    )

    val psiMainFile =
      fixture.addFileToProject("src/main/Test.kt", """
      fun main() {}
    """.trimIndent())

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
        sceneComponentProvider
      )
    val pinnedSurfaceBuilder =
      createPinnedDesignSurfaceBuilder(
        project,
        navigationHandler,
        interactionHandler,
        nopDataProvider,
        fixture.testRootDisposable,
        sceneComponentProvider
      )
    val composePreviewViewImpl =
      ComposePreviewViewImpl(
        project,
        mainFileSmartPointer,
        statusManager,
        nopDataProvider,
        mainSurfaceBuilder,
        listOf(pinnedSurfaceBuilder),
        fixture.testRootDisposable,
        nopAction,
        nopAction
      )

    previewView = composePreviewViewImpl
    fakeUi =
      FakeUi(
        JPanel().apply {
          layout = BorderLayout()
          size = Dimension(1000, 800)
          add(composePreviewViewImpl.component, BorderLayout.CENTER)
        },
        1.0,
        true
      )
    fakeUi.root.validate()
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PIN_PREVIEW.clearOverride()
  }

  /**
   * Updates the [ComposePreviewView] with the preview elements provided by the [previewProvider]. A
   * [composePreviewManager] is needed to determine the state.
   */
  private fun updatePreviewAndRefreshWithProvider(
    previewProvider: PreviewElementProvider<ComposePreviewElementInstance>,
    composePreviewManager: ComposePreviewManager,
    surface: NlDesignSurface = previewView.mainSurface
  ) {
    val testPreviewElementModelAdapter =
      object : ComposePreviewElementModelAdapter() {
        override fun toXml(previewElement: ComposePreviewElementInstance) =
          """
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
  android:layout_width="wrap_content"
  android:layout_height="wrap_content"
  android:text="Hello world ${previewElement.displaySettings.name}" />
"""

        override fun createDataContext(previewElement: ComposePreviewElementInstance) =
          TestPreviewElementDataContext(project, composePreviewManager, previewElement)
      }
    runBlocking(workerThread) {
      surface.updatePreviewsAndRefresh(
        true,
        previewProvider,
        Logger.getInstance(ComposePreviewViewImplTest::class.java),
        mainFileSmartPointer.element!!,
        fixture.testRootDisposable,
        EmptyProgressIndicator(),
        {
          previewView.hasRendered = true
          previewView.hasContent = true
        },
        testPreviewElementModelAdapter,
        ::configureLayoutlibSceneManagerForPreviewElement
      )
    }
    previewView.updateVisibilityAndNotifications()
    fakeUi.root.validate()
  }

  @RunsInEdt
  @Test
  fun `empty preview state`() {
    previewView.hasRendered = true
    previewView.hasContent = false
    previewView.updateVisibilityAndNotifications()
    fakeUi.root.validate()

    assertEquals(
      """
      No preview found.
      Add preview by annotating Composables with @Preview
      [Using the Compose preview]
    """.trimIndent(),
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!.toDisplayText()
    )
  }

  @RunsInEdt
  @Test
  fun `test compilation error state`() {
    previewView.hasRendered = true
    previewView.hasContent = false
    statusManager.status = ProjectStatus.NeedsBuild
    previewView.updateVisibilityAndNotifications()
    fakeUi.root.validate()

    val shortcutRegEx = Regex("\\(.+.\\)")
    val instructionsText =
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!
        .toDisplayText()
        .replace(shortcutRegEx, "(shortcut)")
    assertEquals(
      """
      A successful build is needed before the preview can be displayed
      [Build & Refresh... (shortcut)]
    """.trimIndent(),
      instructionsText
    )
  }

  @RunsInEdt
  @Test
  fun `create compose view with two elements`() {
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display1"),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2")
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<ComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
          previews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)
    previewView.mainSurface.zoomToFit()
    fakeUi.root.validate()

    assertEquals(2, fakeUi.findAllComponents<SceneViewPeerPanel>() { it.isShowing }.size)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display1" }!!.isShowing)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display2" }!!.isShowing)
  }

  @RunsInEdt
  @Test
  fun `show both main and pinned surfaces`() {
    StudioFlags.COMPOSE_PIN_PREVIEW.override(true)
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display1"),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2")
      )
    val pinnedPreviews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Pinned Display1"),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Pinned Display2")
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<ComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
          previews.asSequence()
      }
    val fakePinnedPreviewProvider =
      object : PreviewElementProvider<ComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
          pinnedPreviews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)
    updatePreviewAndRefreshWithProvider(
      fakePinnedPreviewProvider,
      composePreviewManager,
      previewView.pinnedSurface
    )
    previewView.mainSurface.zoomToFit()
    fakeUi.root.validate()

    assertEquals(2, fakeUi.findAllComponents<SceneViewPeerPanel>() { it.isShowing }.size)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display1" }!!.isShowing)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display2" }!!.isShowing)
    // Not visible by default
    assertFalse(fakeUi.findComponent<JLabel> { it.text == "Pinned Display1" }?.isShowing ?: false)
    assertFalse(fakeUi.findComponent<JLabel> { it.text == "Pinned Display2" }?.isShowing ?: false)

    previewView.setPinnedSurfaceVisibility(true)
    previewView.mainSurface.zoomToFit()
    fakeUi.root.validate()
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Pinned Display1" }!!.isShowing)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Pinned Display2" }!!.isShowing)
    assertEquals(4, fakeUi.findAllComponents<SceneViewPeerPanel>() { it.isShowing }.size)
  }

  @RunsInEdt
  @Test
  fun `open and close bottom panel`() {
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display1"),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2")
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<ComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
          previews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)

    previewView.bottomPanel =
      JPanel().apply {
        layout = BorderLayout()
        size = Dimension(100, 100)
        add(JLabel("Bottom panel"), BorderLayout.CENTER)
      }
    fakeUi.root.validate()
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Bottom panel" }!!.isShowing)

    previewView.bottomPanel = null
    fakeUi.root.validate()
    assertNull(fakeUi.findComponent<JLabel> { it.text == "Bottom panel" })
  }

  @RunsInEdt
  @Test
  fun `verify refresh cancellation`() {
    previewView.onRefreshCancelledByTheUser()
    fakeUi.root.validate()
    assertEquals(
      """
      Refresh was cancelled and needs to be completed before the preview can be displayed
      [Build & Refresh... (Ctrl+Shift+F5)]
    """.trimIndent(),
      (fakeUi.findComponent<InstructionsPanel> { it.isShowing })!!.toDisplayText()
    )
  }

  @RunsInEdt
  @Test
  fun `verify refresh cancellation with content available does not show error panel`() {
    val composePreviewManager = TestComposePreviewManager()
    val previews =
      listOf(
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display1"),
        SingleComposePreviewElementInstance.forTesting("Fake Test Method", "Display2")
      )
    val fakePreviewProvider =
      object : PreviewElementProvider<ComposePreviewElementInstance> {
        override suspend fun previewElements(): Sequence<ComposePreviewElementInstance> =
          previews.asSequence()
      }
    updatePreviewAndRefreshWithProvider(fakePreviewProvider, composePreviewManager)
    previewView.onRefreshCancelledByTheUser()
    fakeUi.root.validate()

    assertNull(fakeUi.findComponent<InstructionsPanel> { it.isShowing })
  }
}
