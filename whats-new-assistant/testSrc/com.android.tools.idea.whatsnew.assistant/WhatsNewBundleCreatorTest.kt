/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant

import com.android.repository.Revision
import com.android.testutils.MockitoKt.whenever
import com.android.test.testutils.TestUtils
import com.android.tools.idea.assistant.AssistantBundleCreator
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeoutException

@RunWith(JUnit4::class)
class WhatsNewBundleCreatorTest {
  @get:Rule var projectRule = AndroidProjectRule.inMemory()

  private lateinit var mockUrlProvider: WhatsNewURLProvider
  private lateinit var localPath: Path

  private val studioRevision = Revision.parseRevision("3.3.0")

  @Before
  fun setUp() {
    // Mock url provider to simulate webserver and also class resource file
    mockUrlProvider = mock(WhatsNewURLProvider::class.java)

    val serverFile = getTestDataPath().resolve("whatsnewassistant/server-3.3.0.xml")
    whenever(mockUrlProvider.getWebConfig(Mockito.anyString()))
      .thenReturn(URL("file:$serverFile"))

    val resourceFile = getTestDataPath().resolve("whatsnewassistant/defaultresource-3.3.0.xml")
    whenever(
        mockUrlProvider.getResourceFileAsStream(
          Mockito.any(),
          Mockito.anyString()
        )
      )
      .thenAnswer { URL("file:$resourceFile").openStream() }

    val tmpDir = TestUtils.createTempDirDeletedOnExit()
    localPath = tmpDir.resolve("local-3.3.0.xml")
    whenever(mockUrlProvider.getLocalConfig(Mockito.anyString())).thenReturn(localPath)
  }

  @Test
  fun enabled() {
    val mockBundler = mock(AssistantBundleCreator::class.java)
    whenever(mockBundler.bundleId).thenReturn(WhatsNewBundleCreator.BUNDLE_ID)
    whenever(mockBundler.config).thenReturn(URL("file:test.file"))
    WhatsNewBundleCreator.setTestCreator(mockBundler)

    assertTrue(WhatsNewBundleCreator.shouldShowReleaseNotes())

    WhatsNewBundleCreator.setTestCreator(null)
  }

  /** Test with a file that exists, simulating good internet connection */
  @Test
  fun testDownloadSuccess() {
    // Expected bundle file is server-3.3.0.xml
    val bundleCreator = WhatsNewBundleCreator(mockUrlProvider, studioRevision)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(bundle)
    if (bundle != null) {
      assertEquals("3.3.10", bundle.version.toString())
      assertEquals("Test What's New from Server", bundle.name)
    }
  }

  /**
   * Test with a file that does not exist, simulating no internet, and also without an already
   * downloaded/unpacked file, so the bundle file will be from the class resource
   */
  @Test
  fun downloadDoesNotExist() {
    whenever(mockUrlProvider.getWebConfig(Mockito.anyString()))
      .thenReturn(URL("file:server-doesnotexist-3.3.0.xml"))

    // Expected bundle file is defaultresource-3.3.0.xml
    val bundleCreator = WhatsNewBundleCreator(mockUrlProvider, studioRevision)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(bundle)
    if (bundle != null) {
      assertEquals("3.3.0", bundle.version.toString())
      assertEquals("Test What's New from Class Resource", bundle.name)
    }
  }

  /**
   * First test a downloaded file, then with one that doesn't exist, simulating losing internet
   * connection after having it earlier
   */
  @Test
  fun downloadDoesNotExistWithExistingDownloaded() {
    // First expected bundle file is server-3.3.0.xml
    val bundleCreator = WhatsNewBundleCreator(mockUrlProvider, studioRevision)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(bundle)
    if (bundle != null) {
      assertEquals("3.3.10", bundle.version.toString())
      assertEquals("Test What's New from Server", bundle.name)
    }

    // Change server file to one that doesn't exist, meaning no connection
    whenever(mockUrlProvider.getWebConfig(Mockito.anyString()))
      .thenReturn(URL("file:server-doesnotexist-3.3.0.xml"))
    // Expected bundle file is still server-3.3.0.xml because it was downloaded on the first fetch
    val newBundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(newBundle)
    if (newBundle != null) {
      assertEquals("3.3.10", newBundle.version.toString())
      assertEquals("Test What's New from Server", newBundle.name)
    }
  }

  /** Test that disabling the download flag will not fetch from "server" */
  @Test
  fun downloadFlagDisabled() {
    // Expected bundle file is defaultresource-3.3.0.xml
    val bundleCreator =
      WhatsNewBundleCreator(mockUrlProvider, studioRevision, WhatsNewConnectionOpener(), false)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(bundle)
    if (bundle != null) {
      assertEquals("3.3.0", bundle.version.toString())
      assertEquals("Test What's New from Class Resource", bundle.name)
    }
  }

  @Test
  fun downloadTimeout() {
    val mockConnectionOpener = mock(WhatsNewConnectionOpener::class.java)
    whenever(
        mockConnectionOpener.openConnection(Mockito.isNotNull(), Mockito.anyInt())
      )
      .thenThrow(TimeoutException())

    // Expected bundle file is defaultresource-3.3.0.xml
    val bundleCreator =
      WhatsNewBundleCreator(mockUrlProvider, studioRevision, mockConnectionOpener, true)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(bundle)
    if (bundle != null) {
      assertEquals("3.3.0", bundle.version.toString())
      assertEquals("Test What's New from Class Resource", bundle.name)
    }
  }

  /** Test that parseBundle correctly deletes local cache and retries once when the parse fails */
  @Test
  fun parseBundleRetry() {
    // Trying to read empty xml will cause parser to throw exception...
    val emptyFile = getTestDataPath().resolve("whatsnewassistant/empty.xml")
    FileUtil.copy(emptyFile.toFile(), localPath.toFile())
    val bundleCreator =
      WhatsNewBundleCreator(mockUrlProvider, studioRevision, WhatsNewConnectionOpener(), false)

    // So parseBundle should delete the empty file and retry, resulting in defaultresource-3.3.0.xml
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    assertNotNull(bundle)
    if (bundle != null) {
      assertEquals("3.3.0", bundle.version.toString())
      assertEquals("Test What's New from Class Resource", bundle.name)
    }
  }

  /**
   * Test that WNA bundle creator correctly identifies when an updated config has a higher version
   * field than the previous existing config
   */
  @Test
  fun newConfigVersion() {
    // Since download is disabled, the current file will be defaultresource-3.3.0.xml, version
    // "3.3.0"
    val bundleCreator =
      WhatsNewBundleCreator(mockUrlProvider, studioRevision, WhatsNewConnectionOpener(), false)

    // Disabled download means last seen version is from default resource, so "3.3.0" = "3.3.0"
    assertFalse(bundleCreator.isNewConfigVersion)

    // After enabling download, the new file will be server-3.3.0.xml, version "3.3.10"
    bundleCreator.setAllowDownload(true)
    assertTrue(bundleCreator.isNewConfigVersion)

    // And running once again should be false because last seen is now "3.3.10"
    assertFalse(bundleCreator.isNewConfigVersion)

    // Disabling download again should use local-3.3.0.xml, cached from the download, version
    // "3.3.10"
    bundleCreator.setAllowDownload(false)
    assertFalse(bundleCreator.isNewConfigVersion)
  }

  @Test
  fun hasResourceConfig() {
    val bundleCreator =
      WhatsNewBundleCreator(mockUrlProvider, studioRevision, WhatsNewConnectionOpener(), false)

    // Both the resource file and the Studio version are 3.3.0
    assertTrue(bundleCreator.hasResourceConfig())

    // Different versions
    bundleCreator.setStudioRevision(Revision.parseRevision("3.4.1rc0"))
    assertFalse(bundleCreator.hasResourceConfig())

    // Should return true for 0.0.0 because of dev build
    bundleCreator.setStudioRevision(Revision.parseRevision("0.0.0rc0"))
    assertTrue(bundleCreator.hasResourceConfig())
  }

  private fun getTestDataPath(): Path {
    return Path.of(AndroidTestBase.getTestDataPath())
  }
}
