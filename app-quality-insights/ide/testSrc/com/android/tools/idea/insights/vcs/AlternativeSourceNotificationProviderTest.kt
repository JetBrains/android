/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.vcs

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.Connection
import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.insights.ui.vcs.ContextDataForDiff
import com.android.tools.idea.insights.ui.vcs.InsightsDiffRequestChain
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.ui.flatten
import com.google.common.truth.Truth.assertThat
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffRequestProcessorEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.PathUtil
import javax.swing.JLabel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class AlternativeSourceNotificationProviderTest {
  private val ACTIVE_FLV1_APP_ID =
    "com.example.app.flv1.release" // We use this as the current app id for all tests.
  private val INACTIVE_FLV2_APP_ID = "com.example.app.flv2.release"

  private val PACKAGE_DIR = "com/example/app"
  private val SRC =
    """
    package com.example.app
    Class Foo {}
  """
      .trimIndent()

  private val projectRule =
    AndroidProjectRule.withAndroidModels(
      { root -> root.resolve("app/src").mkdirs() },
      JavaModuleModelBuilder.rootModuleBuilder,
      // We don't configure flavors or so for simplicity.
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        selectedBuildVariant = "release",
        projectBuilder = AndroidProjectBuilder(applicationIdFor = { ACTIVE_FLV1_APP_ID }),
      ),
    )

  @get:Rule val rule = RuleChain.outerRule(projectRule).around(EdtRule())

  private lateinit var provider: AlternativeSourceNotificationProvider

  @Before
  fun setUp() {
    provider = AlternativeSourceNotificationProvider()
  }

  @Test
  fun `show banner when there's alternatives`() {
    val flv1File = projectRule.fixture.addFileToProject("app/src/flv1/$PACKAGE_DIR/Foo.kt", SRC)
    val flv2File = projectRule.fixture.addFileToProject("app/src/flv2/$PACKAGE_DIR/Foo.kt", SRC)
    val flv3File = projectRule.fixture.addFileToProject("app/src/flv3/$PACKAGE_DIR/Foo.kt", SRC)

    val diffFile =
      createDiffFile(
        flv1File.virtualFile,
        projectRule.project,
        createConnection(INACTIVE_FLV2_APP_ID)
      )
    val fileEditor = diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable)
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    panel!!.assertContents()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isEqualTo(AlternativeSourceNotificationProvider.AppScopeMatchResult.MISMATCH)
  }

  @Test
  fun `do not show banner when app scopes are matching even ambiguities exist`() {
    val flv1File = projectRule.fixture.addFileToProject("app/src/flv1/$PACKAGE_DIR/Foo.kt", SRC)
    val flv2File = projectRule.fixture.addFileToProject("app/src/flv2/$PACKAGE_DIR/Foo.kt", SRC)
    val flv3File = projectRule.fixture.addFileToProject("app/src/flv3/$PACKAGE_DIR/Foo.kt", SRC)

    val diffFile =
      createDiffFile(
        flv1File.virtualFile,
        projectRule.project,
        createConnection(ACTIVE_FLV1_APP_ID)
      )
    val fileEditor = diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable)
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    assertThat(panel).isNull()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isEqualTo(AlternativeSourceNotificationProvider.AppScopeMatchResult.MATCH)
  }

  @Test
  fun `do not show banner when no alternatives`() {
    val flv1File = projectRule.fixture.addFileToProject("app/src/flv1/$PACKAGE_DIR/Foo.kt", SRC)

    val diffFile =
      createDiffFile(
        flv1File.virtualFile,
        projectRule.project,
        createConnection(INACTIVE_FLV2_APP_ID)
      )
    val fileEditor = diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable)
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    assertThat(panel).isNull()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isNull() // We don't reach the step to check scope.
  }

  @Test
  fun `do not show banner when no package name qualified alternatives`() {
    val flv1File =
      projectRule.fixture.addFileToProject(
        "app/src/flv1/Foo.kt",
        SRC
      ) // package name is "com.example.app"
    val flv2File =
      projectRule.fixture.addFileToProject("app/src/flv2/Foo.kt", "class Foo") // package name is ""

    val diffFile =
      createDiffFile(
        flv1File.virtualFile,
        projectRule.project,
        createConnection(ACTIVE_FLV1_APP_ID)
      )
    val fileEditor = diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable)
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    assertThat(panel).isNull()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isEqualTo(null) // we don't reach the step to check scope
  }

  @Test
  fun `do not show banner when app scope matching result is UNKNOWN even ambiguities exist`() {
    val flv1File = projectRule.fixture.addFileToProject("app/src/flv1/$PACKAGE_DIR/Foo.kt", SRC)
    val flv2File = projectRule.fixture.addFileToProject("app/src/flv2/$PACKAGE_DIR/Foo.kt", SRC)
    val flv3File = projectRule.fixture.addFileToProject("app/src/flv3/$PACKAGE_DIR/Foo.kt", SRC)

    val diffFile = createDiffFile(flv1File.virtualFile, projectRule.project, null)
    val fileEditor = diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable)
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    assertThat(panel).isNull()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isEqualTo(AlternativeSourceNotificationProvider.AppScopeMatchResult.UNKNOWN)
  }

  @Test
  fun `show banner based on the previously cached scope matching result`() {
    val flv1File = projectRule.fixture.addFileToProject("app/src/flv1/$PACKAGE_DIR/Foo.kt", SRC)
    val flv2File = projectRule.fixture.addFileToProject("app/src/flv2/$PACKAGE_DIR/Foo.kt", SRC)
    val flv3File = projectRule.fixture.addFileToProject("app/src/flv3/$PACKAGE_DIR/Foo.kt", SRC)

    val diffFile =
      createDiffFile(
        flv1File.virtualFile,
        projectRule.project,
        createConnection(ACTIVE_FLV1_APP_ID)
      )
    val fileEditor =
      diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable).apply {
        putUserData(
          provider.APP_SCOPE_MATCH_RESULT_KEY,
          AlternativeSourceNotificationProvider.AppScopeMatchResult.MISMATCH
        )
      }
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    panel!!.assertContents()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isEqualTo(AlternativeSourceNotificationProvider.AppScopeMatchResult.MISMATCH)
  }

  @Test
  fun `do not show banner based on the previously cached scope matching result`() {
    val flv1File = projectRule.fixture.addFileToProject("app/src/flv1/$PACKAGE_DIR/Foo.kt", SRC)
    val flv2File = projectRule.fixture.addFileToProject("app/src/flv2/$PACKAGE_DIR/Foo.kt", SRC)
    val flv3File = projectRule.fixture.addFileToProject("app/src/flv3/$PACKAGE_DIR/Foo.kt", SRC)

    val diffFile =
      createDiffFile(
        flv1File.virtualFile,
        projectRule.project,
        createConnection(INACTIVE_FLV2_APP_ID)
      )
    val fileEditor =
      diffFile.createFileEditor(projectRule.project, projectRule.testRootDisposable).apply {
        putUserData(
          provider.APP_SCOPE_MATCH_RESULT_KEY,
          AlternativeSourceNotificationProvider.AppScopeMatchResult.MATCH
        )
      }
    val panel = provider.collectNotificationData(projectRule.project, diffFile)?.apply(fileEditor)

    assertThat(panel).isNull()
    assertThat(fileEditor.getUserData(provider.APP_SCOPE_MATCH_RESULT_KEY))
      .isEqualTo(AlternativeSourceNotificationProvider.AppScopeMatchResult.MATCH)
  }

  private fun EditorNotificationPanel.assertContents() {
    val labels = flatten().filterIsInstance<JLabel>().map { it.text }.filterNot { it.isEmpty() }
    assertThat(labels).containsExactly(provider.TEXT_WARNING, "Alternative sources: ")

    val comboBoxes =
      flatten()
        .filterIsInstance<ComboBox<AlternativeSourceNotificationProvider.ComboBoxFileElement>>()
    assertThat(comboBoxes.size).isEqualTo(1)
    comboBoxes.single().assertContents()

    val links = flatten().filterIsInstance<HyperlinkLabel>().map { it.text }
    assertThat(links).containsExactly("Hide")
  }

  private fun ComboBox<AlternativeSourceNotificationProvider.ComboBoxFileElement>.assertContents() {
    val items =
      (0 until itemCount).map {
        getItemAt(it) as AlternativeSourceNotificationProvider.ComboBoxFileElement
      }
    assertThat(items.map { PathUtil.toSystemIndependentName(it.toString()) })
      .containsExactly("flv1/…/Foo.kt", "flv2/…/Foo.kt", "flv3/…/Foo.kt")
  }

  private fun createDiffFile(
    file: VirtualFile,
    project: Project,
    origin: Connection?
  ): ChainDiffVirtualFile {
    val contextDataForDiff =
      ContextDataForDiff(
        vcsKey = VCS_CATEGORY.TEST_VCS,
        revision = "123",
        filePath = file.toVcsFilePath(),
        lineNumber = 4,
        origin = origin
      )
    return ChainDiffVirtualFile(InsightsDiffRequestChain(contextDataForDiff, project), "")
  }

  private fun ChainDiffVirtualFile.createFileEditor(
    project: Project,
    parentDisposable: Disposable
  ): FileEditor {
    val processor = createProcessor(project).apply { Disposer.register(parentDisposable, this) }
    return DiffRequestProcessorEditor(this, processor).apply {
      Disposer.register(parentDisposable, this)
    }
  }

  private fun createConnection(appId: String): Connection {
    return object : Connection by mock() {
      override val appId = appId
    }
  }
}
