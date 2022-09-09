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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import org.jetbrains.annotations.SystemIndependent
import org.w3c.dom.Document
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.streams.asSequence

interface TemplateBasedTestProject : TestProjectDefinition {
  val template: String
  val pathToOpen: String
  val testName: String?
  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean
  val autoMigratePackageAttribute: Boolean
  val setup: () -> () -> Unit
  val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit
  val expectedSyncIssues: Set<Int>
  val verifyOpened: ((Project) -> Unit)?
  class VariantSelection(val gradlePath: String, val variant: String)
  val switchVariant: VariantSelection? get() = null

  val projectName: String  get() = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"
  val templateAbsolutePath: File get() = resolveTestDataPath(template)
  val additionalRepositories: Collection<File> get() = getAdditionalRepos()
  fun getTestDataDirectoryWorkspaceRelativePath(): String
  fun getAdditionalRepos(): Collection<File>

  fun resolveTestDataPath(testDataPath: @SystemIndependent String): File {
    val testDataDirectory = TestUtils.getWorkspaceRoot()
      .resolve(FileUtil.toSystemDependentName(getTestDataDirectoryWorkspaceRelativePath()))
    return testDataDirectory.resolve(FileUtil.toSystemDependentName(testDataPath)).toFile()
  }

  fun defaultOpenPreparedProjectOptions(): OpenPreparedProjectOptions {
    return OpenPreparedProjectOptions(expectedSyncIssues = expectedSyncIssues)
      .let {
        val verifyOpened = verifyOpened
        if (verifyOpened != null) it.copy(verifyOpened = verifyOpened) else it
      }
  }

  override fun preparedTestProject(
    integrationTestEnvironment: IntegrationTestEnvironment,
    name: String,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
    ndkVersion: String?
  ): PreparedTestProject {
    val root = integrationTestEnvironment.prepareGradleProject(
      templateAbsolutePath,
      additionalRepositories,
      name,
      agpVersion,
      ndkVersion = ndkVersion
    )
    if (autoMigratePackageAttribute && agpVersion >= AgpVersionSoftwareEnvironmentDescriptor.AGP_80_V1) {
      migratePackageAttribute(root)
    }
    patch(agpVersion, root)

    return object : PreparedTestProject {
      override val root: File = root
      override fun <T> open(updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions, body: (Project) -> T): T {
        val tearDown = setup()
        try {
          return integrationTestEnvironment.openPreparedProject(
            name = "$name$pathToOpen",
            options = updateOptions(defaultOpenPreparedProjectOptions())
          ) { project ->
            invokeAndWaitIfNeeded {
              AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
            }
            switchVariant?.let { switchVariant ->
              switchVariant(project, switchVariant.gradlePath, switchVariant.variant)
              invokeAndWaitIfNeeded {
                AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
              }
              verifyOpened?.invoke(project)// Second time.
            }
            body(project)
          }
        } finally {
          tearDown()
        }
      }
    }
  }
}

fun File.replaceContent(change: (String) -> String) {
  writeText(
    readText().let {
      val result = change(it)
      if (it == result) error("No replacements made")
      result
    }
  )
}

private fun Path.replaceContent(change: (String) -> String) {
  toFile().replaceContent(change)
}

fun File.replaceInContent(oldValue: String, newValue: String) {
  replaceContent { it.replace(oldValue, newValue) }
}

private fun Path.replaceInContent(oldValue: String, newValue: String) {
  toFile().replaceInContent(oldValue, newValue)
}

fun migratePackageAttribute(root: File) {
  Files.walk(root.toPath()).asSequence().filter { it.endsWith("AndroidManifest.xml") }.forEach { manifestPath ->
    val namespace = updateXmlDoc(manifestPath) { doc ->
      val attribute = doc.documentElement.getAttribute("package").takeUnless { it.isEmpty() } ?: return@updateXmlDoc null
      doc.documentElement.removeAttribute("package")
      attribute
    } ?: return@forEach

    val buildFileAttribute = when (manifestPath.parent.fileName.toString()) {
      "main" -> "namespace"
      "androidTest" -> null // It is ignored and does not play the role of `testNamespace`.
      else -> null
    } ?: return@forEach

    val buildGradle = manifestPath.parent?.parent?.parent?.resolve("build.gradle")
    val buildGradleKts = manifestPath.parent?.parent?.parent?.resolve("build.gradle.kts")

    when {
      buildGradle?.exists() == true -> {
        buildGradle.replaceContent {
          it + """
            android {
              $buildFileAttribute = "$namespace"
            }
             """
        }
      }

      buildGradleKts?.exists() == true -> {
        buildGradleKts.replaceContent {
          it + """
            android {
              $buildFileAttribute = "$namespace"
            }
             """
        }
      }

      else -> {
        error("Cannot find a build file to store the value of 'package' attribute in $manifestPath")
      }
    }
  }
}

private fun <T : Any> updateXmlDoc(manifestPath: Path, transform: (Document) -> T?): T? {
  val factory = DocumentBuilderFactory.newInstance()
  val dBuilder = factory.newDocumentBuilder()
  val doc: Document = dBuilder.parse(manifestPath.toFile())

  val result = transform(doc) ?: return null

  val transformerFactory = TransformerFactory.newInstance()
  val transformer: Transformer = transformerFactory.newTransformer()
  val source = DOMSource(doc)
  transformer.transform(source, StreamResult(manifestPath.toFile()))
  return result
}