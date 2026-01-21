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
package com.android.tools.idea.gradle.project.sync.jdk

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkListDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkPackageType
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkProduct
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import java.util.function.Consumer
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class JdkDownloadUtilsTest : LightPlatformTestCase() {

  private var capturedErrorDialogMessage: String? = null

  override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()
    configJdkListDownloaderWithPredefinedItems(
      simpleJdkItem("Oracle", "OpenJDK", 21),
      simpleJdkItem("JetBrains", "Runtime", 17),
      simpleJdkItem("Amazon", "Corretto", 21),
      simpleJdkItem("IBM", "Semeru", 21),
      simpleJdkItem("JetBrains", "Runtime", 21)
    )
    captureErrorDialogMessages()
  }

  override fun tearDown() = runBlocking {
    runWriteActionAndWait { cleanJdkTable() }
    super.tearDown()
  }

  fun `test Given JDK version unknown for downloaderModel When downloading JDK Then expected exception dialog is displayed`() = timeoutRunBlocking {
    val downloadedJdk = JdkDownloadUtils.downloadJdkWithVersion(project, 0)

    assertNull(downloadedJdk)
    assertEquals("Failed to locate and download a JDK matching criteria (version=0, vendor=Jetbrains). " +
        "Consider installing it manually and modify project's Gradle JDK configuration.", capturedErrorDialogMessage)
  }

  fun `test Given JDK version existing in downloaderModel When download fails Then expected exception dialog is displayed`() = timeoutRunBlocking {
    val actualJdkItemAndPath = JdkDownloadUtils.downloadJdkWithVersion(project, 17)

    assertNull(actualJdkItemAndPath)
    assertEquals("Failed to locate and download a JDK matching criteria (version=17, vendor=Jetbrains). " +
        "Consider installing it manually and modify project's Gradle JDK configuration.", capturedErrorDialogMessage)
  }

  fun `test Given JDK version existing in downloaderModel When download succeeds Then expected Sdk instance is returned`() = timeoutRunBlocking {
    val sdkDownloadTracker = spy(SdkDownloadTracker()).apply {
      doAnswer {
        (it.arguments.last() as Consumer<Boolean>).accept(true)
        true
      }.whenever(this).tryRegisterDownloadingListener(any(), any(), anyOrNull(), any())
    }
    application.replaceService(SdkDownloadTracker::class.java, sdkDownloadTracker, testRootDisposable)

    val downloadedSdk = JdkDownloadUtils.downloadJdkWithVersion(project, 17)

    assertNotNull(downloadedSdk)
    assertEquals(JavaSdkVersion.JDK_17, JavaSdk.getInstance().getVersion(downloadedSdk!!))
    assertNull(capturedErrorDialogMessage)
  }

  private fun simpleJdkItem(vendor: String, product: String, version: Int): JdkItem {
    val url = "https://sample-test/jdk.zip"
    val size = 249L
    return JdkItem(
      JdkProduct(vendor = vendor, product = product, flavour = null),
      isDefaultItem = false,
      isVisibleOnUI = true,
      isPreview = false,
      jdkMajorVersion = version,
      jdkVersion = version.toString(),
      jdkVendorVersion = null,
      suggestedSdkName = "$vendor-$version",
      arch = "jetbrains-hardware",
      os = "linux",
      packageType = JdkPackageType.ZIP,
      url = url,
      sha256 = "sha256",
      archiveSize = size,
      unpackedSize = 10 * size,
      packageRootPrefix = "",
      packageToBinJavaPrefix = "",
      archiveFileName = url.split("/").last(),
      installFolderName = url.split("/").last().removeSuffix(".zip"),
      sharedIndexAliases = listOf(),
      saveToFile = {}
    )
  }

  private fun configJdkListDownloaderWithPredefinedItems(vararg jdkItems: JdkItem) {
    val jdkListDownloader = mock<JdkListDownloader>().apply {
      whenever(downloadModelForJdkInstaller(anyOrNull(), any())).thenReturn(jdkItems.toList())
    }
    application.replaceService(JdkListDownloader::class.java, jdkListDownloader, testRootDisposable)
  }

  private fun captureErrorDialogMessages() {
    TestDialogManager.setTestDialog({
      capturedErrorDialogMessage = it
      Messages.OK
    }, testRootDisposable)
  }

  private fun cleanJdkTable() {
    ProjectJdkTable.getInstance().allJdks.forEach {
      ProjectJdkTable.getInstance().removeJdk(it)
    }
  }
}