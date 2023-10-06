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
package com.google.gct.studio.samples

import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.FN_GRADLE_WRAPPER_UNIX
import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.executeCapturingLoggedErrors
import com.appspot.gsamplesindex.samplesindex.model.Sample
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TEST_PROJECT_ID = "my/test/sample/project/id"
private const val LOCALHOST = "127.0.0.1"

class SampleModelTest {
  @get:Rule val tempFolder = TemporaryFolder()
  @get:Rule val applicationRule = ApplicationRule()

  private val openProjectRequests = mutableListOf<String>()
  private val model = SampleModel { openProjectRequests.add(it) }
  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private lateinit var server: HttpServer
  private lateinit var url: String
  private lateinit var projectFolder: File

  @Before
  fun setUp() {
    // Set up server.
    server = HttpServer.create()
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
      url = "http://$LOCALHOST:${address.port}"
    }
    UsageTracker.setWriterForTest(tracker)
    projectFolder = tempFolder.newFolder()
  }

  @After
  fun tearDown() {
    server.stop(0)
    FileUtil.deleteRecursively(File(FileUtil.getTempDirectory(), "github_cache").toPath())
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun `sample downloaded from repo with several projects`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = "project1"
        }
      )

    // creating project zip
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      zip.createZipEntry("project1/$FN_GRADLE_WRAPPER_UNIX", "//gradlew content".toByteArray())
      zip.createZipEntry("project1/$FN_BUILD_GRADLE", "//build file 1".toByteArray())
      zip.createZipEntry("project1/$FN_SETTINGS_GRADLE", "//build file 1".toByteArray())
      zip.createZipEntry("project1/src/source1.java", "//source java file 1".toByteArray())
      zip.createZipEntry("project2/$FN_GRADLE_WRAPPER_UNIX", "//gradlew content".toByteArray())
      zip.createZipEntry("project2/$FN_BUILD_GRADLE", "//build file 2".toByteArray())
      zip.createZipEntry("project2/$FN_SETTINGS_GRADLE", "//build file 2".toByteArray())
      zip.createZipEntry("project2/src/source2.java", "//source java file 2".toByteArray())
    }
    server.createContext("/test-sample-project/zipball/HEAD", zipBytes.toByteArray())

    testSuccessfulSampleImport(
      expectedFilesList =
        listOf(FN_BUILD_GRADLE, FN_GRADLE_WRAPPER_UNIX, FN_SETTINGS_GRADLE, "src/source1.java")
    )
  }

  @Test
  fun `sample downloaded from repo with single project`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = ""
        }
      )

    // creating project zip
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      zip.createZipEntry(FN_GRADLE_WRAPPER_UNIX, "//gradlew content".toByteArray())
      zip.createZipEntry(FN_BUILD_GRADLE, "//build file".toByteArray())
      zip.createZipEntry(FN_SETTINGS_GRADLE, "//build file".toByteArray())
      zip.createZipEntry("src/source1.java", "//source java file".toByteArray())
    }
    server.createContext("/test-sample-project/zipball/HEAD", zipBytes.toByteArray())

    testSuccessfulSampleImport(
      expectedFilesList =
        listOf(FN_BUILD_GRADLE, FN_GRADLE_WRAPPER_UNIX, FN_SETTINGS_GRADLE, "src/source1.java")
    )
  }

  @Test
  fun `sample without gradlew`() {
    Assume.assumeTrue(SystemInfo.isUnix)

    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = ""
        }
      )

    // creating project zip
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      zip.createZipEntry(FN_BUILD_GRADLE, "//build file".toByteArray())
      zip.createZipEntry(FN_SETTINGS_GRADLE, "//build file".toByteArray())
      zip.createZipEntry("src/source1.java", "//source java file".toByteArray())
    }
    server.createContext("/test-sample-project/zipball/HEAD", zipBytes.toByteArray())

    testSuccessfulSampleImport(
      expectedFilesList = listOf(FN_BUILD_GRADLE, FN_SETTINGS_GRADLE, "src/source1.java"),
      expectedMessages =
        listOf(
          "Could not make gradle wrapper executable for sample: myTestImportSampleProject. Command line builds may not work properly."
        ),
      expectedErrorsLogged =
        listOf("Could not find gradle wrapper. Command line builds may not work properly.")
    )
  }

  @Test
  fun `sample with specified path not found`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = "project3"
        }
      )

    // creating project zip
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      zip.createZipEntry("project1/$FN_GRADLE_WRAPPER_UNIX", "//gradlew content".toByteArray())
      zip.createZipEntry("project1/$FN_BUILD_GRADLE", "//build file 1".toByteArray())
      zip.createZipEntry("project1/$FN_SETTINGS_GRADLE", "//build file 1".toByteArray())
      zip.createZipEntry("project1/src/source1.java", "//source java file 1".toByteArray())
      zip.createZipEntry("project2/$FN_GRADLE_WRAPPER_UNIX", "//gradlew content".toByteArray())
      zip.createZipEntry("project2/$FN_BUILD_GRADLE", "//build file 2".toByteArray())
      zip.createZipEntry("project2/$FN_SETTINGS_GRADLE", "//build file 2".toByteArray())
      zip.createZipEntry("project2/src/source2.java", "//source java file 2".toByteArray())
    }
    server.createContext("/test-sample-project/zipball/HEAD", zipBytes.toByteArray())

    testFailedSampleImport(
      expectedMessages = listOf("Could not find sample root \"project3\" in Git repository"),
      expectedErrorsLogged = emptyList()
    )
  }

  @Test
  fun `no sample found in downloaded repo`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = "project3"
        }
      )

    // creating project zip
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      zip.createZipEntry("project1/src/source1.java", "//source java file 1".toByteArray())
    }
    server.createContext("/test-sample-project/zipball/HEAD", zipBytes.toByteArray())

    testFailedSampleImport(
      expectedMessages = listOf("Failed to find any projects in Git repository"),
      expectedErrorsLogged = emptyList()
    )
  }

  @Test
  fun `no samples repo download failed`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = ""
        }
      )

    testFailedSampleImport(
      expectedMessages =
        listOf(
          """
        Could not download specified project from Github. Check the URL and branch name.
        
        Cannot download '$url/test-sample-project/zipball/HEAD': Request failed with status code 404
      """
            .trimIndent()
        ),
      expectedErrorsLogged = emptyList()
    )
  }

  @Test
  fun `SampleImportWizard did not collect dir`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(null)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = ""
        }
      )

    testFailedSampleImport(
      expectedMessages = emptyList(),
      expectedErrorsLogged =
        listOf(
          "SampleImportWizard did not collect expected information and will not complete. Please report this error."
        )
    )
  }

  @Test
  fun `SampleImportWizard did not collect name`() {
    model.projectName().setNullableValue(null)
    model.dir().setNullableValue(projectFolder)
    model
      .sample()
      .setNullableValue(
        Sample().apply {
          id = TEST_PROJECT_ID
          cloneUrl = "$url/test-sample-project"
          path = ""
        }
      )

    testFailedSampleImport(
      expectedMessages = emptyList(),
      expectedErrorsLogged =
        listOf(
          "SampleImportWizard did not collect expected information and will not complete. Please report this error."
        )
    )
  }

  @Test
  fun `SampleImportWizard did not collect sample item`() {
    model.projectName().setNullableValue("myTestImportSampleProject")
    model.dir().setNullableValue(projectFolder)
    model.sample().setNullableValue(null)

    testFailedSampleImport(
      expectedMessages = emptyList(),
      expectedErrorsLogged =
        listOf(
          "SampleImportWizard did not collect expected information and will not complete. Please report this error."
        )
    )
  }

  private fun testSuccessfulSampleImport(
    expectedFilesList: List<String>,
    expectedMessages: List<String> = emptyList(),
    expectedErrorsLogged: List<String> = emptyList()
  ) {
    val messagesShown = mutableListOf<String>()
    TestDialogManager.setTestDialog { message ->
      messagesShown.add(message)
      Messages.OK
    }
    val errors = executeCapturingLoggedErrors { model.handleFinished() }

    Truth.assertThat(messagesShown).isEqualTo(expectedMessages)
    Truth.assertThat(errors).isEqualTo(expectedErrorsLogged)

    // Verify resulted unpacked project files
    Truth.assertThat(listFolderContents(projectFolder).sorted().joinToString(separator = "\n"))
      .isEqualTo(expectedFilesList.sorted().joinToString(separator = "\n"))

    // Verify project open request
    Truth.assertThat(openProjectRequests).isEqualTo(listOf(projectFolder.toString()))

    // Verify metrics sent
    Truth.assertThat(loggedUsages).isEqualTo(listOf(TEST_PROJECT_ID))
  }

  private fun testFailedSampleImport(
    expectedMessages: List<String>,
    expectedErrorsLogged: List<String>
  ) {
    val messagesShown = mutableListOf<String>()
    TestDialogManager.setTestDialog { message ->
      messagesShown.add(message)
      Messages.OK
    }
    val errors = executeCapturingLoggedErrors { model.handleFinished() }

    Truth.assertThat(messagesShown).isEqualTo(expectedMessages)
    Truth.assertThat(errors).isEqualTo(expectedErrorsLogged)

    // Verify no files in project dir
    Truth.assertThat(listFolderContents(projectFolder)).isEmpty()

    // Verify no project open request
    Truth.assertThat(openProjectRequests).isEmpty()

    // Verify no metrics sent
    Truth.assertThat(loggedUsages).isEmpty()
  }

  private fun HttpServer.createContext(path: String, content: ByteArray) {
    synchronized(this) {
      createContext(path) { exchange ->
        exchange.responseHeaders.set("Content-Type", "application/zip")
        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, content.size.toLong())
        exchange.responseBody.write(content)
        exchange.close()
      }
    }
  }

  private fun ZipOutputStream.createZipEntry(name: String, content: ByteArray) {
    val entry = ZipEntry(name)
    putNextEntry(entry)
    write(content)
    closeEntry()
  }

  private val loggedUsages: List<String>
    get() =
      tracker.usages
        .filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.IMPORT_SAMPLE_EVENT }
        .map { it.studioEvent.importSampleEvent.importSampleId }

  private fun listFolderContents(folder: File): List<String> {
    return folder
      .walkTopDown()
      .filterNot { it.isDirectory }
      .map { FileUtil.toSystemIndependentName(it.relativeTo(folder).toString()) }
      .toList()
  }
}
