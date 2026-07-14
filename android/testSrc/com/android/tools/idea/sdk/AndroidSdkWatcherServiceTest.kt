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
package com.android.tools.idea.sdk

import com.android.repository.impl.manager.LocalRepoLoaderImpl
import com.android.testutils.waitForCondition
import com.android.tools.idea.avdmanager.AvdScannerService
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.sdk.AndroidSdkData
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidSdkWatcherServiceTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val disposableRule = DisposableRule()

  private lateinit var testScope: CoroutineScope

  private fun createPackageXml(path: String, displayName: String, apiLevel: Int) =
    """
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <ns2:sdk-repository xmlns:ns2="http://schemas.android.com/sdk/android/repo/repository2/01" xmlns:ns3="http://schemas.android.com/sdk/android/repo/sys-img2/01" xmlns:ns4="http://schemas.android.com/repository/android/common/01" xmlns:ns5="http://schemas.android.com/sdk/android/repo/addon2/01">
      <localPackage path="$path" obsolete="false">
        <type-details xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="ns2:platformDetailsType">
          <api-level>$apiLevel</api-level>
          <layoutlib api="4"/>
        </type-details>
        <revision><major>1</major></revision>
        <display-name>$displayName</display-name>
      </localPackage>
    </ns2:sdk-repository>
  """
      .trimIndent()

  @Before
  fun setUp() {
    testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @After
  fun tearDown() {
    testScope.cancel()
  }

  @Test
  fun testWatcherDetectsPackageCreationAndTriggersListeners() {
    val sdkDir = FileUtil.createTempDirectory("sdk", null).canonicalFile.toPath()
    Files.createDirectories(sdkDir.resolve("platforms"))

    val watcherService = AndroidSdkWatcherService(testScope)
    disposableRule.register { watcherService.dispose() }
    watcherService.registerProject(projectRule.project, sdkDir)

    val sdkHandler = AndroidSdkData.getSdkData(sdkDir)!!.sdkHandler
    val repoManager = sdkHandler.getRepoManager(StudioLoggerProgressIndicator(AndroidSdkWatcherServiceTest::class.java))

    val listenerFired = AtomicBoolean(false)
    repoManager.addLocalChangeListener { listenerFired.set(true) }

    // Create a new package directory with valid package.xml
    val pkgDir = Files.createDirectories(sdkDir.resolve("platforms").resolve("android-34"))
    Files.writeString(
      pkgDir.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN),
      createPackageXml("platforms;android-34", "Android SDK Platform 34", 34),
    )

    // Verify watcher service detects disk change and notifies RepoManager listeners
    waitForCondition(10.seconds) { listenerFired.get() }
  }

  @Test
  fun testAvdScannerServiceIntegrationWithWatcher() {
    val sdkDir = FileUtil.createTempDirectory("sdk", null).canonicalFile.toPath()
    Files.createDirectories(sdkDir.resolve("platforms"))

    val watcherService = AndroidSdkWatcherService(testScope)
    disposableRule.register { watcherService.dispose() }
    watcherService.registerProject(projectRule.project, sdkDir)

    val avdScanner = AvdScannerService(testScope)
    disposableRule.register { avdScanner.dispose() }
    avdScanner.onSdkPathChanged(sdkDir)

    val scanCount = AtomicInteger(0)
    val collectJob = testScope.launch { avdScanner.avdFlow.collect { scanCount.incrementAndGet() } }
    disposableRule.register { collectJob.cancel() }

    // Wait for initial scan upon flow collection
    waitForCondition(5.seconds) { scanCount.get() >= 1 }
    val initialCount = scanCount.get()

    // Create package on disk to trigger file watcher -> RepoManager -> AvdScannerService.rescanAsync()
    val pkgDir = Files.createDirectories(sdkDir.resolve("platforms").resolve("android-35"))
    Files.writeString(
      pkgDir.resolve(LocalRepoLoaderImpl.PACKAGE_XML_FN),
      createPackageXml("platforms;android-35", "Android SDK Platform 35", 35),
    )

    // Verify that a new scan emission was triggered by the watch event
    waitForCondition(10.seconds) { scanCount.get() > initialCount }
  }
}
