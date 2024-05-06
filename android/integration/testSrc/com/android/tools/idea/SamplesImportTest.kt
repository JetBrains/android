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
package com.android.tools.idea

import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.FileServer
import com.android.tools.asdriver.tests.MavenRepo
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.writeBytes

class SamplesImportTest {

  @get:Rule
  val system = AndroidSystem.standard()

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun importSampleProjectTest() {

    system.installRepo(
      MavenRepo(
        "tools/adt/idea/android/integration/openproject_deps.manifest"
      )
    )

    // Attempting to import a project on a fresh installation of Android Studio will produce an
    // error saying that no SDK has been configured, so we configure it first.
    system.installation.setGlobalSdk(system.sdk)
    val project = AndroidProject("tools/adt/idea/android/integration/testData/minapp")
    val serverFiles = tempFolder.newFolder().toPath()
    val preparedProjectFiles = project.install(serverFiles)
    val indexFile = serverFiles.resolve("index.json")
    val projectZipFile = serverFiles.resolve("minapp.zip")

    FileServer().use { fileServer ->
      fileServer.start()
      indexFile.writeBytes(generateIndex(fileServer.origin).encodeToByteArray())
      projectZipFile.writeBytes(createZipBytes(preparedProjectFiles))

      fileServer.registerFile("/samplesindex/v1/sample", indexFile)
      fileServer.registerFile("/simpleProject/zipball/HEAD", projectZipFile)

      system.installation.addVmOption(
        "-Dsamples.service.use.local.port.for.test=${fileServer.port}"
      )
      system.runStudioWithoutProject().use { studio ->
        studio.executeAction("WelcomeScreen.GoogleCloudTools.SampleImport")

        studio.waitForComponentWithTextContaining("Next")
        studio.invokeComponent("Next")
        studio.invokeComponent("Finish")
        studio.invokeComponent("Trust Project")
        studio.waitForSync()
      }
    }

    val expectedProjectDir = system.installation.androidStudioProjectsDir.resolve("SimpleProjectforTest").toFile()
    Truth.assertThat(expectedProjectDir.exists()).isTrue()
    val projectFilesList = expectedProjectDir.walkTopDown()
      .onEnter { it.name != ".idea" && it.name != ".gradle" }
      .filterNot { it.isDirectory }
      .filterNot { it.name == "local.properties" }
      .map { FileUtil.toSystemIndependentName(it.relativeTo(expectedProjectDir).toString()) }
      .sorted()
      .joinToString(separator = "\n")

    val originalFilesList = preparedProjectFiles.toFile().walkTopDown()
      .filterNot { it.isDirectory }
      .map { FileUtil.toSystemIndependentName(it.relativeTo(preparedProjectFiles.toFile()).toString()) }
      .sorted()
      .joinToString(separator = "\n")

    Truth.assertThat(projectFilesList).isEqualTo(originalFilesList)
  }

  private fun createZipBytes(sourceDirectory: Path): ByteArray {
    val zipBytes = ByteArrayOutputStream()
    ZipOutputStream(zipBytes).use { zip ->
      Files.walkFileTree(
        sourceDirectory,
        object : SimpleFileVisitor<Path>() {
          override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val relativePath =
              FileUtil.toSystemIndependentName(sourceDirectory.relativize(file).toString())
            createZipEntry(relativePath, Files.readAllBytes(file), zip)
            return FileVisitResult.CONTINUE
          }
        }
      )
    }
    return zipBytes.toByteArray()
  }

  private fun createZipEntry(name: String, content: ByteArray, zip: ZipOutputStream) {
    val entry = ZipEntry(name)
    zip.putNextEntry(entry)
    zip.write(content)
    zip.closeEntry()
  }

  private fun generateIndex(url: String) =
    """
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
    """
      .trimIndent()
}