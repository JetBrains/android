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
package com.android.tools.idea.tests.gui.emulator

import com.android.SdkConstants
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.google.common.truth.Truth
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.fest.swing.timing.Wait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(GuiTestRemoteRunner::class)
class ImportSampleProjectTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  private val LOCALHOST = "127.0.0.1"

  private lateinit var server: HttpServer
  private lateinit var url: String
  private lateinit var importProjectZipBytes: ByteArray

  /**
   * To verify that importing a sample project and deploying on test device.
   *
   *
   * This is run to qualify releases. Please involve the test team in substantial changes.
   *
   *
   * TT ID: ae1223a3-b42d-4c8f-8837-5c6f7e8c583a
   *
   * <pre>
   * Prepare:
   * 1. Run test-local http server to serve index and project zip.
   * 2. Via properties instruct SamplesService to send requests to the local server instead.
   * Test Steps:
   * 1. Open Android Studio
   * 2. Open Import samples wizard, select and import project crafted above
   * Verify:
   * 1. The sample project is imported and synced successfully.
   * </pre>
   *
   *
   *
   */
  @RunIn(TestGroup.SANITY_BAZEL) // b/76023451
  @Test
  fun importSampleProject() {
    val projectDir = guiTest.setUpProject("SimpleApplication")
    // We do not care about the contents of `gradlew` file here. Production code just needs to make it executable and throwing an error
    // if the file not found. `gradlew` is needed only for command line tools which is beyond the scope of this test.
    projectDir.resolve(SdkConstants.FN_GRADLE_WRAPPER_UNIX).createNewFile()
    importProjectZipBytes = createZipBytes(projectDir.toPath())
    FileUtilRt.delete(projectDir)

    val samplesWizard = guiTest.welcomeFrame()
      .importCodeSample()
    samplesWizard.selectSample("Test project/Simple Project for Test")
      .clickNext()
      .clickFinish()
    val syncSuccessful = guiTest.ideFrame().waitForGradleSyncToFinish(Wait.seconds(600))
    Truth.assertWithMessage("Sync should be successful").that(syncSuccessful).isTrue()
    // Wait only for sync, ignore indexing jobs.
    guiTest.ideFrame().closeProject()

  }

  private fun createZipBytes(sourceDirectory: Path): ByteArray {
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      Files.walkFileTree(sourceDirectory, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
          val relativePath = FileUtil.toSystemIndependentName(sourceDirectory.relativize(file).toString())
          createZipEntry(relativePath, Files.readAllBytes(file), zip)
          return FileVisitResult.CONTINUE
        }
      })
    }
    return zipBytes.toByteArray()
  }

  private fun createZipEntry(name: String, content: ByteArray, zip: ZipOutputStream) {
    val entry = ZipEntry(name)
    zip.putNextEntry(entry)
    zip.write(content)
    zip.closeEntry()
  }

  private val indexBytes get() = """
    {
     "items": [
      {
       "id": "simpleproject//main",
       "title": "Simple Project for Test",
       "status": "PUBLISHED",
       "level": "INTERMEDIATE",
       "technologies": [
        "android"
       ],
       "categories": [
        "test project"
       ],
       "languages": [
        "java",
        "kotlin"
       ],
       "solutions": [
        "mobile"
       ],
       "cloneUrl": "$url/simpleProject/",
       "github": "simpleProject",
       "branch": "main",
       "path": "",
       "description": "This is a simple project example used for tests.",
       "screenshots": [],
       "icon": "screenshots/icon-web.png",
       "apiRefs": [],
       "license": {
        "name": "apache2",
        "link": "http://www.apache.org/licenses/LICENSE-2.0.html"
       }
      }
     ]
    }
""".trimIndent().encodeToByteArray()

  @Before
  fun setUp() {
    // Set up server.
    server = HttpServer.create()
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
      url = "http://$LOCALHOST:${address.port}"

      createContext("/test") { he: HttpExchange ->
        println(he.requestMethod)
        println(he.requestURI)
        he.responseHeaders["Content-Type"] = "application/json; charset=utf-8"
        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, indexBytes.size.toLong())
        he.responseBody.write(indexBytes)
        he.close()
      }
      createContext("/simpleProject/zipball/HEAD") { he: HttpExchange ->
        println(he.requestMethod)
        println(he.requestURI)
        he.responseHeaders["Content-Type"] = "application/zip; charset=utf-8"
        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, importProjectZipBytes.size.toLong())
        he.responseBody.write(importProjectZipBytes)
        he.close()
      }
    }
    println(server.address)
    PropertiesComponent.getInstance().setValue("samples.service.use.local.port.for.test", server.address.port.toString())
  }

  @After
  fun tearDown() {
    server.stop(0)
    PropertiesComponent.getInstance().setValue("samples.service.use.local.port.for.test", null)
  }
}
