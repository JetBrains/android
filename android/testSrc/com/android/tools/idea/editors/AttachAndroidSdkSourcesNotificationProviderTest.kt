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

import com.android.flags.junit.RestoreFlagRule
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withSdk
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit

@RunWith(JUnit4::class)
class AttachAndroidSdkSourcesNotificationProviderTest {
  @Rule
  val myAndroidProjectRule = withSdk()

  @Rule
  val myMockitoRule = MockitoJUnit.rule()

  @Rule
  val myRestoreFlagRule: RestoreFlagRule<*> = RestoreFlagRule<Any?>(StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE)

  @Mock
  private val myFileEditor: FileEditor? = null

  @Mock
  private val myModelWizardDialog: ModelWizardDialog? = null
  private var myProvider: TestAttachAndroidSdkSourcesNotificationProvider? = null
  @Before
  fun setup() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(true)
    Mockito.`when`(myModelWizardDialog!!.showAndGet()).thenReturn(true)
    myProvider = TestAttachAndroidSdkSourcesNotificationProvider(myAndroidProjectRule.project)
  }

  @get:Test
  val key: Unit
    get() {
      Truth.assertThat(myProvider!!.key.toString()).isEqualTo("add sdk sources to class")
    }

  @Test
  fun createNotificationPanel_fileIsNotJavaClass_returnsNull() {
    val javaFile = myAndroidProjectRule.fixture.createFile("somefile.java", "file contents")
    Truth.assertThat(javaFile.fileType).isEqualTo(JavaFileType.INSTANCE)
    val panel = invokeCreateNotificationPanel(javaFile)
    Truth.assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_javaClassNotInAndroidSdk_returnsNull() {
    val javaClassFile = myAndroidProjectRule.fixture.createFile("someclass.class", "file contents")
    Truth.assertThat(javaClassFile.fileType).isEqualTo(JavaClassFileType.INSTANCE)
    val panel = invokeCreateNotificationPanel(javaClassFile)
    Truth.assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_javaClassInAndroidSdkAndSourcesAvailable_nullReturned() {
    val virtualFile = ApplicationManager.getApplication().runReadAction(
      Computable {
        val cls = JavaPsiFacade.getInstance(myAndroidProjectRule.project)
          .findClass("android.view.View", GlobalSearchScope.allScope(myAndroidProjectRule.project))
        val file = cls!!.containingFile
        file.virtualFile
      } as Computable<VirtualFile>)
    val panel = invokeCreateNotificationPanel(virtualFile)
    Truth.assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_virtualFileHasRequiredSourcesKeyButIsNull_nullReturned() {
    val javaFile = myAndroidProjectRule.fixture.createFile("somefile.java", "file contents")
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, null)
    val panel = invokeCreateNotificationPanel(javaFile)
    Truth.assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_virtualFileHasRequiredSourcesKeyButIsEmpty_nullReturned() {
    val javaFile = myAndroidProjectRule.fixture.createFile("somefile.java", "file contents")
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, ImmutableList.of())
    val panel = invokeCreateNotificationPanel(javaFile)
    Truth.assertThat(panel).isNull()
  }

  @Test
  fun createNotificationPanel_flagOff_panelHasCorrectLabel() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false)
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    Truth.assertThat(panel).isNotNull()
    Truth.assertThat(panel!!.text).isEqualTo("Sources for 'SDK' not found.")
  }

  @Test
  fun createNotificationPanel_panelHasCorrectLabel() {
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    Truth.assertThat(panel).isNotNull()
    Truth.assertThat(panel!!.text).isEqualTo("Android SDK sources not found.")
  }

  @Test
  fun createNotificationPanel_flagOff_panelHasDownloadAndRefreshLinks() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false)
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    val links: Map<String?, Runnable> = panel!!.links
    Truth.assertThat(links.keys).containsExactly("Download", "Refresh (if already downloaded)")
  }

  @Test
  fun createNotificationPanel_panelHasDownloadLink() {
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    val links: Map<String?, Runnable> = panel!!.links
    Truth.assertThat(links.keys).containsExactly("Download SDK Sources")
  }

  @Test
  fun createNotificationPanel_flagOff_downloadLinkDownloadsSources() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false)
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    val rootProvider = AndroidSdks.getInstance().allAndroidSdks[0].rootProvider
    Truth.assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0)

    // Invoke the "Download" link, which is first in the components.
    ApplicationManager.getApplication().invokeAndWait { panel!!.links["Download"]!!.run() }

    // Check that the link requested the correct paths, and that then sources became available.
    Truth.assertThat(myProvider.getRequestedPaths()).isNotNull()
    Truth.assertThat(myProvider.getRequestedPaths()).containsExactly("sources;android-32")
    Truth.assertThat(rootProvider.getFiles(OrderRootType.SOURCES).size).isGreaterThan(0)
  }

  @Test
  fun createNotificationPanel_downloadLinkDownloadsSources() {
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    val rootProvider = AndroidSdks.getInstance().allAndroidSdks[0].rootProvider
    Truth.assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0)

    // Invoke the "Download" link, which is first in the components.
    ApplicationManager.getApplication().invokeAndWait {
      panel!!.links["Download SDK Sources"]!!
        .run()
    }

    // Check that the link requested the correct paths, and that then sources became available.
    Truth.assertThat(myProvider.getRequestedPaths()).isNotNull()
    Truth.assertThat(myProvider.getRequestedPaths()).containsExactly("sources;android-32")
    Truth.assertThat(rootProvider.getFiles(OrderRootType.SOURCES).size).isGreaterThan(0)
  }

  @Test
  fun createNotificationPanel_virtualFileHasRequiredSourcesKey_downloadLinkHasRequestedSources() {
    val requiredSourceVersions: List<AndroidVersion> = ImmutableList.of(
      AndroidVersion(30),
      AndroidVersion(31)
    )
    val javaFile = myAndroidProjectRule.fixture.createFile("somefile.java", "file contents")
    javaFile.putUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY, requiredSourceVersions)
    val panel = invokeCreateNotificationPanel(javaFile)
    ApplicationManager.getApplication().invokeAndWait {
      panel!!.links["Download SDK Sources"]!!
        .run()
    }

    // Check that the link requested the correct paths, and that then sources became available.
    Truth.assertThat(myProvider.getRequestedPaths()).isNotNull()
    Truth.assertThat(myProvider.getRequestedPaths()).containsExactly("sources;android-30", "sources;android-31")
  }

  @Test
  fun createNotificationPanel_flagOff_refreshLinkUpdatesSources() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false)
    val virtualFile = androidSdkClassWithoutSources
    val panel = invokeCreateNotificationPanel(virtualFile)
    val rootProvider = AndroidSdks.getInstance().allAndroidSdks[0].rootProvider
    Truth.assertThat(rootProvider.getFiles(OrderRootType.SOURCES)).hasLength(0)

    // Invoke the "Refresh" link, which is second in the components.
    ApplicationManager.getApplication().invokeAndWait {
      panel!!.links["Refresh (if already downloaded)"]!!
        .run()
    }
    Truth.assertThat(rootProvider.getFiles(OrderRootType.SOURCES).size).isGreaterThan(0)
  }

  private fun invokeCreateNotificationPanel(virtualFile: VirtualFile): AttachAndroidSdkSourcesNotificationProvider.MyEditorNotificationPanel? {
    return ApplicationManager.getApplication().runReadAction(
      Computable {
        myProvider!!.createNotificationPanel(
          virtualFile,
          myFileEditor!!
        ) as AttachAndroidSdkSourcesNotificationProvider.MyEditorNotificationPanel?
      })
  }

  private val androidSdkClassWithoutSources: VirtualFile
    private get() {
      for (sdk in AndroidSdks.getInstance().allAndroidSdks) {
        val sdkModificator = sdk.sdkModificator
        sdkModificator.removeRoots(OrderRootType.SOURCES)
        ApplicationManager.getApplication().invokeAndWait { sdkModificator.commitChanges() }
      }
      return ApplicationManager.getApplication().runReadAction(
        Computable {
          val cls = JavaPsiFacade.getInstance(myAndroidProjectRule.project)
            .findClass("android.view.View", GlobalSearchScope.allScope(myAndroidProjectRule.project))
          val file = cls!!.containingFile
          file.virtualFile
        } as Computable<VirtualFile>)
    }

  /**
   * Test implementation of [AttachAndroidSdkSourcesNotificationProvider] that mocks the call to create an SDK download dialog.
   */
  private inner class TestAttachAndroidSdkSourcesNotificationProvider(project: Project) :
    AttachAndroidSdkSourcesNotificationProvider(project) {
    var requestedPaths: List<String>? = null
      private set

    override fun createSdkDownloadDialog(requestedPaths: List<String>?): ModelWizardDialog? {
      this.requestedPaths = requestedPaths
      return myModelWizardDialog
    }
  }
}