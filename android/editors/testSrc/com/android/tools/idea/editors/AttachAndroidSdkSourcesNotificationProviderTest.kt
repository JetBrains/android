/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors

import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.AndroidVersion
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.sdk.AndroidSdkData
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AttachAndroidSdkSourcesNotificationProviderTest {
  // TODO(b/291755082): Update to 34 once 34 sources are published
  @get:Rule val projectRule = AndroidProjectRule.withSdk(AndroidVersion(33))

  private val mockFileEditor: FileEditor = mock()
  private val mockModelWizardDialog: ModelWizardDialog = mock()
  private val testCoroutineScope = TestScope()

  private val provider = TestAttachAndroidSdkSourcesNotificationProvider()

  private val repositoryPackages = RepositoryPackages()

  private var originalAndroidSdkData: AndroidSdkData? = null

  @Before
  fun setup() {
    val sdkRoot = createInMemoryFileSystemAndFolder("sdk")
    val repoManager = FakeRepoManager(sdkRoot, repositoryPackages)
    val sdkHandler = AndroidSdkHandler(sdkRoot, sdkRoot.root.resolve("avd"), repoManager)
    val sdkData: AndroidSdkData = mock()
    whenever(sdkData.sdkHandler).thenReturn(sdkHandler)

    repositoryPackages.setRemotePkgInfos(
      listOf(FakeRemotePackage("sources;android-30"), FakeRemotePackage("sources;android-33"))
    )

    val androidSdks = AndroidSdks.getInstance()
    originalAndroidSdkData = androidSdks.tryToChooseAndroidSdk()
    androidSdks.setSdkData(sdkData)
  }

  @After
  fun tearDown() {
    AndroidSdks.getInstance().setSdkData(originalAndroidSdkData)

    // Workaround for https://youtrack.jetbrains.com/issue/IJPL-149706:
    // test tear-down during indexing triggers flaky "already disposed" exceptions.
    IndexingTestUtil.waitUntilIndexesAreReady(projectRule.project)
  }

  @Test
  fun createNotificationPanel_fileIsNotJavaClass_returnsNull() {
    val javaFile = projectRule.fixture.createFile("somefile.java", "file contents")

    assertThat(javaFile.fileType).isEqualTo(JavaFileType.INSTANCE)
    val panel = invokeCreateNotificationPanel(javaFile)
    assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_javaClassNotInAndroidSdk_returnsNull() {
    val javaClassFile = projectRule.fixture.createFile("someclass.class", "")

    assertThat(javaClassFile.fileType).isEqualTo(JavaClassFileType.INSTANCE)
    val panel = invokeCreateNotificationPanel(javaClassFile)
    assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_javaClassInAndroidSdkAndSourcesAvailable_nullReturned() {
    val virtualFile = getSdkViewClassContainingFile()

    val panel = invokeCreateNotificationPanel(virtualFile)
    assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_virtualFileHasRequiredSourcesKeyButIsNull_nullReturned() {
    val javaFile = projectRule.fixture.createFile("somefile.java", "file contents")
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, null)

    val panel = invokeCreateNotificationPanel(javaFile)
    assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_panelHasCorrectLabel() {
    removeSourcesFromSdk()
    val panel = requireNotNull(invokeCreateNotificationPanel(getSdkViewClassContainingFile()))
    assertThat(panel.text).isEqualTo("Android SDK sources for API 33 are not available.")
  }

  @Test
  fun createNotificationPanel_downloadNotAvailable_panelHasCorrectLabel() {
    repositoryPackages.setRemotePkgInfos(listOf())

    removeSourcesFromSdk()
    val panel = requireNotNull(invokeCreateNotificationPanel(getSdkViewClassContainingFile()))
    assertThat(panel.text).isEqualTo("Android SDK sources for API 33 are not available.")
  }

  @Test
  fun createNotificationPanel_panelHasDownloadLink() {
    removeSourcesFromSdk()
    val panel = requireNotNull(invokeCreateNotificationPanel(getSdkViewClassContainingFile()))

    val links: Map<String, Runnable> = panel.links
    assertThat(links.keys).containsExactly("Download")
  }

  @Test
  fun createNotificationPanel_downloadNotAvailable_panelHasNoLinks() {
    repositoryPackages.setRemotePkgInfos(listOf())

    removeSourcesFromSdk()
    val panel = requireNotNull(invokeCreateNotificationPanel(getSdkViewClassContainingFile()))
    val links: Map<String, Runnable> = panel.links
    assertThat(links).isEmpty()
  }

  @Test
  fun createNotificationPanel_downloadLinkDownloadsSources() {
    whenever(mockModelWizardDialog.showAndGet()).thenReturn(true)
    removeSourcesFromSdk()
    val panel = requireNotNull(invokeCreateNotificationPanel(getSdkViewClassContainingFile()))

    val rootProvider = AndroidSdks.getInstance().allAndroidSdks[0].rootProvider
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0)

    // Invoke the "Download" link, which is first in the components.
    ApplicationManager.getApplication().invokeAndWait { panel.links["Download"]!!.run() }

    // Check that the link requested the correct paths, and that then sources became available.
    assertThat(provider.requestedPaths).isNotNull()
    assertThat(provider.requestedPaths).containsExactly("sources;android-33")
    assertThat(rootProvider.getFiles(OrderRootType.SOURCES).size).isGreaterThan(0)
  }

  @Test
  fun createNotificationPanel_virtualFileHasRequiredSourcesKey_downloadLinkHasRequestedSources() {
    val javaFile = projectRule.fixture.createFile("somefile.java", "file contents")
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, 30)

    val panel = requireNotNull(invokeCreateNotificationPanel(javaFile))
    ApplicationManager.getApplication().invokeAndWait { panel.links["Download"]!!.run() }

    // Check that the link requested the correct paths, and that then sources became available.
    assertThat(provider.requestedPaths).isNotNull()
    assertThat(provider.requestedPaths).containsExactly("sources;android-30")
  }

  private fun invokeCreateNotificationPanel(
    virtualFile: VirtualFile
  ): AttachAndroidSdkSourcesNotificationProvider.MyEditorNotificationPanel? {
    val panelCreationFunction =
      runReadAction { provider.collectNotificationData(projectRule.project, virtualFile) }
        ?: return null

    val panel = panelCreationFunction.apply(mockFileEditor)

    testCoroutineScope.testScheduler.advanceUntilIdle()
    ApplicationManager.getApplication().invokeAndWait {}

    return panel as AttachAndroidSdkSourcesNotificationProvider.MyEditorNotificationPanel?
  }

  private fun getSdkViewClassContainingFile(): VirtualFile = runReadAction {
    JavaPsiFacade.getInstance(projectRule.project)
      .findClass("android.view.View", GlobalSearchScope.allScope(projectRule.project))!!
      .containingFile
      .virtualFile
  }

  private fun removeSourcesFromSdk() {
    for (sdk in AndroidSdks.getInstance().allAndroidSdks) {
      val sdkModificator = sdk.sdkModificator
      sdkModificator.removeRoots(OrderRootType.SOURCES)
      WriteAction.runAndWait<Throwable>(sdkModificator::commitChanges)
    }
  }

  /**
   * Test implementation of [AttachAndroidSdkSourcesNotificationProvider] that mocks the call to
   * create an SDK download dialog.
   */
  private inner class TestAttachAndroidSdkSourcesNotificationProvider :
    AttachAndroidSdkSourcesNotificationProvider() {
    var requestedPaths: List<String>? = null
      private set

    override fun createSdkDownloadDialog(
      project: Project,
      requestedPaths: List<String>?,
    ): ModelWizardDialog {
      this.requestedPaths = requestedPaths
      return mockModelWizardDialog
    }

    override fun createCoroutineScopeForEditor(fileEditor: FileEditor) = testCoroutineScope
  }
}
