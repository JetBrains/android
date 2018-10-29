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
import com.android.testutils.TestUtils
import com.android.tools.idea.assistant.AssistantBundleCreator
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.ProjectManager
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.net.URL

class WhatsNewAssistantBundleCreatorTest : AndroidTestCase() {
  private lateinit var mockUrlProvider: WhatsNewAssistantURLProvider

  override fun setUp() {
    super.setUp()
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(true)
    StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.override(true)

    // Mock url provider to simulate webserver and also class resource file
    mockUrlProvider = mock(WhatsNewAssistantURLProvider::class.java)

    val serverFile = File(myFixture.testDataPath).resolve("whatsnewassistant/server-3.3.0.xml")
    `when`(mockUrlProvider.getWebConfig(ArgumentMatchers.anyString())).thenReturn(URL("file:" + serverFile.path))

    val resourceFile = File(myFixture.testDataPath).resolve("whatsnewassistant/defaultresource-3.3.0.xml")
    `when`(mockUrlProvider.getResourceFileAsStream(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
      .thenReturn(URL("file:" + resourceFile.path).openStream())

    val tmpDir = TestUtils.createTempDirDeletedOnExit()
    val localPath = tmpDir.toPath().resolve("local-3.3.0.xml")
    `when`(mockUrlProvider.getLocalConfig(ArgumentMatchers.anyString())).thenReturn(URL("file:" + localPath.toString()))
  }

  override fun tearDown() {
    super.tearDown()
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.clearOverride()
    StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.clearOverride()
  }

  @Test
  fun testDisabled() {
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(false)

    TestCase.assertFalse(WhatsNewAssistantBundleCreator.shouldShowReleaseNotes())
  }

  @Test
  fun testEnabled() {
    val mockBundler = mock(AssistantBundleCreator::class.java)
    `when`(mockBundler.bundleId).thenReturn(WhatsNewAssistantBundleCreator.BUNDLE_ID)
    `when`(mockBundler.config).thenReturn(URL("file:test.file"))
    WhatsNewAssistantBundleCreator.setTestCreator(mockBundler)

    TestCase.assertTrue(WhatsNewAssistantBundleCreator.shouldShowReleaseNotes())

    WhatsNewAssistantBundleCreator.setTestCreator(null)
  }

  /**
   * Test with a file that exists, simulating good internet connection
   */
  @Test
  fun testDownloadSuccess() {
    // Expected bundle file is server-3.3.0.xml
    val bundleCreator = WhatsNewAssistantBundleCreator(mockUrlProvider)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    TestCase.assertNotNull(bundle)
    if (bundle != null) {
      TestCase.assertEquals(150, bundle.version)
      TestCase.assertEquals("Test What's New from Server", bundle.name)
    }
  }

  /**
   * Test with a file that does not exist, simulating no internet, and also
   * without an already downloaded/unpacked file, so the bundle file will
   * be from the class resource
   */
  @Test
  fun testDownloadDoesNotExist() {
    `when`(mockUrlProvider.getWebConfig(ArgumentMatchers.anyString())).thenReturn(URL("file:server-doesnotexist-3.3.0.xml"))

    // Expected bundle file is defaultresource-3.3.0.xml
    val bundleCreator = WhatsNewAssistantBundleCreator(mockUrlProvider)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    TestCase.assertNotNull(bundle)
    if (bundle != null) {
      TestCase.assertEquals(100, bundle.version)
      TestCase.assertEquals("Test What's New from Class Resource", bundle.name)
    }
  }

  /**
   * First test a downloaded file, then with one that doesn't exist, simulating
   * losing internet connection after having it earlier
   */
  @Test
  fun testDownloadDoesNotExistWithExistingDownloaded() {
    // First expected bundle file is server-3.3.0.xml
    val bundleCreator = WhatsNewAssistantBundleCreator(mockUrlProvider)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    TestCase.assertNotNull(bundle)
    if (bundle != null) {
      TestCase.assertEquals(150, bundle.version)
      TestCase.assertEquals("Test What's New from Server", bundle.name)
    }

    // Change server file to one that doesn't exist, meaning no connection
    `when`(mockUrlProvider.getWebConfig(ArgumentMatchers.anyString())).thenReturn(URL("file:server-doesnotexist-3.3.0.xml"))
    // Expected bundle file is still server-3.3.0.xml because it was downloaded on the first fetch
    val newBundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    TestCase.assertNotNull(newBundle)
    if (newBundle != null) {
      TestCase.assertEquals(150, newBundle.version)
      TestCase.assertEquals("Test What's New from Server", newBundle.name)
    }
  }

  /**
   * Test that disabling the download flag will not fetch from "server"
   */
  @Test
  fun testDownloadFlagDisabled() {
    StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.override(false)

    // Expected bundle file is defaultresource-3.3.0.xml
    val bundleCreator = WhatsNewAssistantBundleCreator(mockUrlProvider)
    val bundle = bundleCreator.getBundle(ProjectManager.getInstance().defaultProject)
    TestCase.assertNotNull(bundle)
    if (bundle != null) {
      TestCase.assertEquals(100, bundle.version)
      TestCase.assertEquals("Test What's New from Class Resource", bundle.name)
    }
  }

  /**
   * Test that a WNA bundle file exists for the current Android Studio version.
   * When version is updated, the bundle must also be updated to ensure there is
   * a fallback to a local file when it cannot be downloaded
   */
  @Test
  fun testConfigExistsForCurrentVersion() {
    val revision = Revision.parseRevision(ApplicationInfo.getInstance().strictVersion)
    val version =  String.format("%d.%d.%d", revision.major, revision.minor, revision.micro)
    val bundleCreator = WhatsNewAssistantBundleCreator()
    TestCase.assertNotNull(bundleCreator.javaClass.getResource("/$version.xml"))
  }
}
