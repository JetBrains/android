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
import com.android.testutils.delayUntilCondition
import com.android.testutils.retryUntilPassing
import com.android.tools.adtui.instructions.HyperlinkInstruction
import com.android.tools.adtui.instructions.InstructionsPanel
import com.android.tools.adtui.instructions.NewRowInstruction
import com.android.tools.adtui.instructions.TextInstruction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.findDescendant
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.surface.NopInteractionHandler
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SceneViewPeerPanel
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeScreenViewProvider
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.build.RenderingBuildStatusManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gemini.GeminiPluginApi
import com.android.tools.idea.gemini.LlmPrompt
import com.android.tools.idea.preview.createOrReuseModelForPreviewElement
import com.android.tools.idea.preview.find.PreviewElementProvider
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.util.androidFacet
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.registerOrReplaceServiceInstance
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private fun configureLayoutlibSceneManagerForPreviewElement(
  displaySettings: PreviewDisplaySettings,
  layoutlibSceneManager: LayoutlibSceneManager,
) =
  configureLayoutlibSceneManager(
    layoutlibSceneManager,
    showDecorations = displaySettings.showDecoration,
    isInteractive = false,
    requestPrivateClassLoader = false,
    runVisualAnalysis = false,
    quality = 1f,
    disableAnimation = false,
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

  @get:Rule val flagRule = FlagRule(StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW)

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private val nopDataProvider = DataProvider { null }

  private val statusManager =
    object : RenderingBuildStatusManager {
      override val isBuilding: Boolean = false
      override val statusFlow: MutableStateFlow<RenderingBuildStatus> =
        MutableStateFlow(RenderingBuildStatus.Ready)
    }
  private lateinit var mainFileSmartPointer: SmartPsiElementPointer<PsiFile>
  private lateinit var previewView: ComposePreviewView
  private lateinit var fakeUi: FakeUi
  private val fakeStudioBotActionFactory = FakeStudioBotActionFactory()

  private val geminiPluginApi =
    object : GeminiPluginApi {
      var contextAllowed = false

      override val MAX_QUERY_CHARS = Int.MAX_VALUE

      override fun isAvailable() = true

      override fun isContextAllowed(project: Project) = contextAllowed

      override fun sendChatQuery(
        project: Project,
        prompt: LlmPrompt,
        displayText: String?,
        requestSource: GeminiPluginApi.RequestSource,
      ) {}

      override fun stageChatQuery(
        project: Project,
        prompt: String,
        requestSource: GeminiPluginApi.RequestSource,
      ) {}
    }

  @Before
  fun setUp() {
    ApplicationManager.getApplication()
      .registerExtension(GeminiPluginApi.EP_NAME, geminiPluginApi, projectRule.testRootDisposable)
    ExtensionTestUtil.maskExtensions(
      ComposeStudioBotActionFactory.EP_NAME,
      listOf(fakeStudioBotActionFactory),
      projectRule.testRootDisposable,
    )
    runBlocking(Dispatchers.EDT) {
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

      fixture.addFileToProject(
        "src/main/androidx/compose/runtime/Composable.kt",
        // language=kotlin
        """
      package androidx.compose.runtime

      annotation class Composable
      """
          .trimIndent(),
      )

      val psiMainFile =
        fixture.addFileToProject(
          "src/main/Test.kt",
          """
        import $COMPOSABLE_ANNOTATION_FQ_NAME

        @Composable
        fun Preview1() {
        }
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
  }

  /**
   * Updates the [ComposePreviewView] with the preview elements provided by the [previewProvider]. A
   * [composePreviewManager] is needed to determine the state.
   */
  private fun updatePreviewAndRefreshWithProvider(
    previewProvider: PreviewElementProvider<PsiComposePreviewElementInstance>,
    composePreviewManager: ComposePreviewManager,
    surface: NlDesignSurface = previewView.mainSurface,
    configureLayoutlibSceneManager:
      (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager =
      ::configureLayoutlibSceneManagerForPreviewElement,
  ) {
    val testPreviewElementModelAdapter = createPreviewElementModelAdapter(composePreviewManager)
    runBlocking(Dispatchers.Default) {
      surface.updatePreviewsAndRefresh(
        reinflate = true,
        previewProvider.previewElements().toList(),
        Logger.getInstance(ComposePreviewViewImplTest::class.java),
        mainFileSmartPointer.element!!,
        fixture.testRootDisposable,
        EmptyProgressIndicator(),
        testPreviewElementModelAdapter,
        DefaultModelUpdater(),
        navigationHandler = ComposePreviewNavigationHandler(),
        configureLayoutlibSceneManager = configureLayoutlibSceneManager,
        null,
      )
      previewView.hasRendered = true
      previewView.hasContent = true
    }
    ApplicationManager.getApplication().invokeAndWait {
      previewView.updateVisibilityAndNotifications()
      fakeUi.root.validate()
    }
  }

  private fun createPreviewElementModelAdapter(
    composePreviewManager: ComposePreviewManager
  ): ComposePreviewElementModelAdapter =
    object : ComposePreviewElementModelAdapter() {
      override fun toXml(previewElement: PsiComposePreviewElementInstance) =
        """
  <TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Hello world ${previewElement.displaySettings.name}" />
  """

      override fun createDataProvider(previewElement: PsiComposePreviewElementInstance) =
        object :
          NlDataProvider(COMPOSE_PREVIEW_MANAGER, PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE, PROJECT) {
          override fun getData(dataId: String): Any? =
            when (dataId) {
              COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
              PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> previewElement
              PROJECT.name -> project
              else -> null
            }
        }
    }

  @Test
  fun `empty preview state when flag is disabled`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.override(false)
    geminiPluginApi.contextAllowed = true
    checkEmptyPreviewState(showAutoGenerateAction = false, showSyntaxErrorNote = false)
  }

  @Test
  fun `empty preview state when context-sharing is disabled`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.override(true)
    geminiPluginApi.contextAllowed = false
    checkEmptyPreviewState(showAutoGenerateAction = false, showSyntaxErrorNote = false)
  }

  @Test
  fun `empty preview state when preview generator is null`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.override(true)
    geminiPluginApi.contextAllowed = true
    fakeStudioBotActionFactory.isNullPreviewGeneratorAction = true
    checkEmptyPreviewState(showAutoGenerateAction = false, showSyntaxErrorNote = false)
  }

  @Test
  fun `empty preview state when flag and context-sharing are enabled`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.override(true)
    geminiPluginApi.contextAllowed = true
    checkEmptyPreviewState(showAutoGenerateAction = true, showSyntaxErrorNote = false)
  }

  @Test
  fun `empty preview state when there are syntax errors`() {
    StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.override(false)
    val wolfTheProblemSolver =
      object : MockWolfTheProblemSolver() {
        override fun hasProblemFilesBeneath(scope: Module): Boolean = true
      }
    projectRule.project.registerOrReplaceServiceInstance(
      WolfTheProblemSolver::class.java,
      wolfTheProblemSolver,
      fixture.testRootDisposable,
    )
    checkEmptyPreviewState(showAutoGenerateAction = false, showSyntaxErrorNote = true)
  }

  private fun checkEmptyPreviewState(
    showAutoGenerateAction: Boolean,
    showSyntaxErrorNote: Boolean,
  ) = runBlocking {
    previewView.hasRendered = true
    previewView.hasContent = false
    runBlocking { previewView.updateVisibilityAndNotifications() }
    var instructionPanel: InstructionsPanel? = null
    delayUntilCondition(250) {
      instructionPanel = (fakeUi.findComponent<InstructionsPanel> { it.isShowing })
      instructionPanel != null
    }

    retryUntilPassing(2.seconds) {
      assertEquals(
        listOfNotNull(
            "No preview found.",
            "Add preview by annotating Composables with @Preview.",
            if (showSyntaxErrorNote)
              "Note: syntax errors could cause existing previews not to be found."
            else null,
            "[Using the Compose preview]",
            if (showAutoGenerateAction) "[Auto-generate Compose Previews for this file]" else null,
          )
          .joinToString("\n"),
        instructionPanel?.toDisplayText(),
      )
    }
  }

  @Test
  fun `test compilation error state`() = runBlocking {
    previewView.hasRendered = true
    previewView.hasContent = false
    statusManager.statusFlow.value = RenderingBuildStatus.NeedsBuild

    runBlocking { previewView.updateVisibilityAndNotifications() }
    var instructionPanel: InstructionsPanel? = null
    delayUntilCondition(250) {
      instructionPanel = (fakeUi.findComponent<InstructionsPanel> { it.isShowing })
      instructionPanel != null
    }

    retryUntilPassing(2.seconds) {
      val shortcutRegEx = Regex("\\(.+.\\)")
      val instructionsText = instructionPanel?.toDisplayText()?.replace(shortcutRegEx, "(shortcut)")
      assertEquals(
        """
        A successful build is needed before the preview can be displayed
        [Build & Refresh... (shortcut)]
      """
          .trimIndent(),
        instructionsText,
      )
    }
  }

  @Test
  fun `create compose view with two elements`() = runBlocking {
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
    delayUntilCondition(100, 1.seconds) { fakeUi.findAllComponents<SceneViewPeerPanel>().size == 2 }

    assertEquals(2, fakeUi.findAllComponents<SceneViewPeerPanel> { it.isShowing }.size)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display1" }!!.isShowing)
    assertTrue(fakeUi.findComponent<JLabel> { it.text == "Display2" }!!.isShowing)
  }

  @Test
  fun `open and close bottom panel`() = runBlocking {
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
    var bottomPanel: JLabel? = null

    // Find the bottom panel in fake ui
    delayUntilCondition(250) {
      bottomPanel = fakeUi.findComponent<JLabel> { it.text == "Bottom panel" }
      bottomPanel != null
    }
    assertNotNull(bottomPanel)

    // Wait until the bottom panel is shown
    delayUntilCondition(250) { bottomPanel!!.isShowing }
    assertTrue(bottomPanel!!.isShowing)

    // Close the bottom panel
    ApplicationManager.getApplication().invokeAndWait {
      previewView.bottomPanel = null
      fakeUi.root.validate()
    }

    delayUntilCondition(250) {
      bottomPanel = fakeUi.findComponent<JLabel> { it.text == "Bottom panel" }
      bottomPanel == null
    }
    assertNull(bottomPanel)
  }

  @Test
  fun `verify refresh cancellation`() {
    ApplicationManager.getApplication().invokeAndWait {
      previewView.onRefreshCancelledByTheUser()
      fakeUi.root.validate()
    }
    val shortcut = if (SystemInfo.isMac) "⌥⇧⌘R" else "Ctrl+Shift+F5"
    assertEquals(
      """
      Refresh was cancelled and needs to be completed before the preview can be displayed
      [Build & Refresh... ($shortcut)]
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

  @Test
  fun `test reusing model resets Configuration`() {
    val composePreviewManager = TestComposePreviewManager()
    val fakePreviewElement =
      SingleComposePreviewElementInstance.forTesting<SmartPsiElementPointer<PsiElement>>(
        "Fake Test Method",
        "Display1",
      )
    val testPreviewElementModelAdapter = createPreviewElementModelAdapter(composePreviewManager)
    val configurationManager = ConfigurationManager.getOrCreateInstance(projectRule.module)

    runBlocking {
      val modelToCreate =
        previewView.mainSurface.createOrReuseModelForPreviewElement(
          reinflate = true,
          previewElement = fakePreviewElement,
          previewElementModelAdapter = testPreviewElementModelAdapter,
          debugLogger = null,
          modelToReuse = null,
          psiFile = mainFileSmartPointer.element!!,
          configurationManager = configurationManager,
          parentDisposable = fixture.testRootDisposable,
          facet = projectRule.module.androidFacet!!,
        )
      val configuration = modelToCreate.configuration

      val modelToReuse =
        previewView.mainSurface.createOrReuseModelForPreviewElement(
          reinflate = true,
          previewElement = fakePreviewElement,
          previewElementModelAdapter = testPreviewElementModelAdapter,
          debugLogger = null,
          modelToReuse = modelToCreate,
          psiFile = mainFileSmartPointer.element!!,
          configurationManager = configurationManager,
          parentDisposable = fixture.testRootDisposable,
          facet = projectRule.module.androidFacet!!,
        )
      assertEquals(modelToReuse, modelToCreate)
      assertNotEquals(configuration, modelToReuse.configuration)
    }
  }
}

class FakeStudioBotActionFactory : ComposeStudioBotActionFactory {

  var isNullPreviewGeneratorAction = false

  private val fakeAction =
    object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {}
    }

  override fun createPreviewGenerator() = if (isNullPreviewGeneratorAction) null else fakeAction

  override fun transformPreviewAction() = fakeAction

  override fun alignUiToTargetImageAction(): AnAction? = fakeAction

  override fun previewAgentsDropDownAction(): AnAction? = fakeAction
}
