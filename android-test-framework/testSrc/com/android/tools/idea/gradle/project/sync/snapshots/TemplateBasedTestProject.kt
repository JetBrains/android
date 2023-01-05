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
import com.android.tools.idea.testing.AgpIntegrationTestUtil.maybeCreateJdkOverride
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.ResolvedAgpVersionSoftwareEnvironment
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.resolve
import com.android.tools.idea.testing.switchVariant
import com.android.tools.idea.testing.openProjectAndRunTestWithTestFixturesAvailable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
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

/**
 * A test project definition using a Gradle test project stored in test data.
 *
 * This interface is usually implemented by an enum class. See existing implementation.
 */
interface TemplateBasedTestProject : TestProjectDefinition {
  /**
   * The name of this test project.
   *
   * Note, it is usually implemented by [Enum.name].
   */
  val name: String

  /**
   * The path to the Gradle project relative to [getTestDataDirectoryWorkspaceRelativePath].
   */
  val template: String

  /**
   * The path under the project root to open as an IDE project.
   *
   * A non-empty value is useful when defining a test project based on a composite build Gradle project.
   */
  val pathToOpen: String get() = ""

  /**
   * For compatibility with existing tests.
   */
  val testName: String? get() = null

  override val isCompatibleWith: (AgpVersionSoftwareEnvironmentDescriptor) -> Boolean get() = { true }

  /**
   * If `true` the test framework will attempt, when needed, to migrate manifest package attributes to Gradle build configuration for
   * compatibility with AGP 8.0
   */
  val autoMigratePackageAttribute: Boolean get() = true

  /**
   * Additional setup logic required for this test project. Returns a function that should be used to undo any configuration changes made
   * by the setup logic.
   *
   * It is usually used to configure Studio flags and similar settings.
   */
  val setup: () -> () -> Unit get() = { {} }


  /**
   * An additional patch to be applied on top of an already prepared project.
   */
  val patch: AgpVersionSoftwareEnvironmentDescriptor.(projectRoot: File) -> Unit get() = {}


  /**
   * If the project is expected to sync with sync issues, the IDs of expected sync issues.
   */
  val expectedSyncIssues: Set<Int> get() = emptySet()

  /**
   * A function to check that the project was opened correctly. If not provided, the project is expected to open  with Gradle sync
   * succeeding without sync issues except [expectedSyncIssues].
   */
  val verifyOpened: ((Project) -> Unit)? get() = null

  class VariantSelection(val gradlePath: String, val variant: String)

  /**
   * If not null, the variant to select after opening the project.
   */
  val switchVariant: VariantSelection? get() = null

  /**
   * For compatibility with existing tests.
   */
  val projectName: String get() = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"

  /**
   * Returns the root directory of the source test project in the test data directory.
   */
  val templateAbsolutePath: File get() = resolveTestDataPath(template)

  /**
   * Returns the path to the test data directory containing test projects relative to the workspace. For example,
   * `tools/adt/idea/android/testData`.
   */
  fun getTestDataDirectoryWorkspaceRelativePath(): String


  /**
   * Returns the locations of additional Maven/Gradle repositories needed by this project.
   */
  fun getAdditionalRepos(): Collection<File>

  override fun prepareTestProject(
    integrationTestEnvironment: IntegrationTestEnvironment,
    name: String,
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
    ndkVersion: String?
  ): PreparedTestProject {
    val resolvedAgpVersion = agpVersion.resolve()
    val root = integrationTestEnvironment.prepareGradleProject(
      templateAbsolutePath,
      resolvedAgpVersion,
      getAdditionalRepos(),
      name,
      ndkVersion = ndkVersion
    )
    if (autoMigratePackageAttribute && agpVersion >= AgpVersionSoftwareEnvironmentDescriptor.AGP_80) {
      migratePackageAttribute(root)
    }
    patch(agpVersion, root)

    return PreparedTemplateBasedTestProject(this, root, resolvedAgpVersion, integrationTestEnvironment, name)
  }
}

private class PreparedTemplateBasedTestProject(
  private val templateBasedTestProject: TemplateBasedTestProject,
  override val root: File,
  private val resolvedAgpVersion: ResolvedAgpVersionSoftwareEnvironment,
  private val integrationTestEnvironment: IntegrationTestEnvironment,
  private val name: String
) : PreparedTestProject {
  override fun <T> open(
    updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions,
    body: PreparedTestProject.Context.(Project) -> T
  ): T {
    return openProjectAndRunTestWithTestFixturesAvailable(
      openProjectImplementation = { openProject(updateOptions, body = it)},
      testBody = body
    )
  }

  private fun <T> openProject(
    updateOptions: (OpenPreparedProjectOptions) -> OpenPreparedProjectOptions,
    body: (project: Project, projectRoot: File) -> T
  ) = maybeWithJdkOverride { jdkOverride ->
    templateBasedTestProject.usingTestProjectSetup {
      val options =
        updateOptions(templateBasedTestProject.defaultOpenPreparedProjectOptions().copy(overrideProjectJdk = jdkOverride))
      integrationTestEnvironment.openPreparedProject(
        name = "$name${templateBasedTestProject.pathToOpen}",
        options = options
      ) { project ->
        invokeAndWaitIfNeeded {
          AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
        }
        templateBasedTestProject.switchVariant?.let { switchVariant ->
          switchVariant(project, switchVariant.gradlePath, switchVariant.variant)
          invokeAndWaitIfNeeded {
            AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
          }
          templateBasedTestProject.verifyOpened?.invoke(project) // Second time.
        }
        body(project, root)
      }
    }
  }

  private inline fun <T> maybeWithJdkOverride(body: (Sdk?) -> T): T {
    val jdkOverride = maybeCreateJdkOverride(resolvedAgpVersion.jdkVersion)
    try {
      return body(jdkOverride)
    } finally {
      jdkOverride?.let {
        runWriteActionAndWait {
          ProjectJdkTable.getInstance().removeJdk(it)
        }
      }
    }
  }
}

private inline fun <T> TemplateBasedTestProject.usingTestProjectSetup(body: () -> T): T {
  val tearDown = setup()
  try {
    return body()
  } finally {
    tearDown()
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

    when (manifestPath.parent.fileName.toString()) {
      "main" -> Unit
      "androidTest" -> return@forEach // It is ignored and does not play the role of `testNamespace`.
      else -> return@forEach
    }

    val buildGradle = manifestPath.parent?.parent?.parent?.resolve("build.gradle")
    val buildGradleKts = manifestPath.parent?.parent?.parent?.resolve("build.gradle.kts")

    when {
      buildGradle?.exists() == true -> {
        buildGradle.replaceContent {
          it.placeNamespaceProperty(namespace)
        }
        VfsUtil.markDirtyAndRefresh(false, false, false, buildGradle.toFile())
      }

      buildGradleKts?.exists() == true -> {
        buildGradleKts.replaceContent {
          it.placeNamespaceProperty(namespace)
        }
        VfsUtil.markDirtyAndRefresh(false, false, false, buildGradleKts.toFile())
      }

      else -> {
        error("Cannot find a build file to store the value of 'package' attribute in $manifestPath")
      }
    }
  }
}

private fun String.placeNamespaceProperty(namespace: String): String {
  val marker = "\nandroid {\n"
  val firstIndex = indexOf(marker)
  val insertionIndex = if (firstIndex < 0) -1 else firstIndex + marker.length
  return if (insertionIndex >= 0) substring(0, insertionIndex) + "\n  namespace = \"$namespace\"\n" + substring(insertionIndex, length)
  else this + """
  |android {
  |  namespace = "$namespace"
  |}
  """.trimMargin()
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

private fun TemplateBasedTestProject.resolveTestDataPath(testDataPath: @SystemIndependent String): File {
  val testDataDirectory = TestUtils.getWorkspaceRoot()
    .resolve(FileUtil.toSystemDependentName(getTestDataDirectoryWorkspaceRelativePath()))
  return testDataDirectory.resolve(FileUtil.toSystemDependentName(testDataPath)).toFile()
}

private fun TemplateBasedTestProject.defaultOpenPreparedProjectOptions(): OpenPreparedProjectOptions {
  return OpenPreparedProjectOptions(expectedSyncIssues = expectedSyncIssues)
    .let {
      val verifyOpened = verifyOpened
      if (verifyOpened != null) it.copy(verifyOpened = verifyOpened) else it
    }
}
