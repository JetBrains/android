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

import com.android.SdkConstants
import com.android.testutils.TestUtils
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.FileSubject
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.OpenPreparedProjectOptions
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import org.jetbrains.android.AndroidTestBase
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

  val projectName: String  get() = "${template.removePrefix("projects/")}$pathToOpen${if (testName == null) "" else " - $testName"}"
  val templateAbsolutePath: File  get() = resolveTestDataPath(template)
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
    agpVersion: AgpVersionSoftwareEnvironmentDescriptor
  ): PreparedTestProject {
    val root = integrationTestEnvironment.prepareGradleProject(
      templateAbsolutePath,
      additionalRepositories,
      name,
      agpVersion,
      ndkVersion = SdkConstants.NDK_DEFAULT_VERSION
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
            body(project)
          }
        } finally {
          tearDown()
        }
      }
    }
  }
}

class SnapshotContext(
  projectName: String,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  workspace: String,
) : SnapshotComparisonTest {

  private val name: String =
    "$projectName${agpVersion.agpSuffix()}${agpVersion.gradleSuffix()}${agpVersion.modelVersion}"

  override val snapshotDirectoryWorkspaceRelativePath: String = workspace
  override fun getName(): String = name
}

internal fun File.replaceContent(change: (String) -> String) {
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

internal fun File.replaceInContent(oldValue: String, newValue: String) {
  replaceContent { it.replace(oldValue, newValue) }
}

private fun Path.replaceInContent(oldValue: String, newValue: String) {
  toFile().replaceInContent(oldValue, newValue)
}

internal fun truncateForV2(settingsFile: File) {
  val patchedText = settingsFile.readLines().takeWhile { !it.contains("//-v2:truncate-from-here") }.joinToString("\n")
  Truth.assertThat(patchedText.trim()).isNotEqualTo(settingsFile.readText().trim())
  settingsFile.writeText(patchedText)
}

internal fun moveGradleRootUnderGradleProjectDirectory(root: File, makeSecondCopy: Boolean = false) {
  val testJdkName = IdeSdks.getInstance().jdk?.name ?: error("No JDK in test")
  val newRoot = root.resolve(if (makeSecondCopy) "gradle_project_1" else "gradle_project")
  val newRoot2 = root.resolve("gradle_project_2")
  val tempRoot = File(root.path + "_tmp")
  val ideaDirectory = root.resolve(".idea")
  val gradleXml = ideaDirectory.resolve("gradle.xml")
  val miscXml = ideaDirectory.resolve("misc.xml")
  Files.move(root.toPath(), tempRoot.toPath())
  Files.createDirectory(root.toPath())
  Files.move(tempRoot.toPath(), newRoot.toPath())
  if (makeSecondCopy) {
    FileUtils.copyDirectory(newRoot, newRoot2)
    newRoot2
      .resolve("settings.gradle")
      .replaceContent { "rootProject.name = 'gradle_project_name'\n$it" } // Give it a name not matching the directory name.
  }
  Files.createDirectory(ideaDirectory.toPath())

  fun gradleSettingsFor(rootName: String): String {
    return """
      <GradleProjectSettings>
        <option name="testRunner" value="GRADLE" />
        <option name="distributionType" value="DEFAULT_WRAPPED" />
        <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}/$rootName" />
      </GradleProjectSettings>
    """
  }

  gradleXml.writeText(
    """
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleMigrationSettings" migrationVersion="1" />
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
        ${gradleSettingsFor(newRoot.name)}
        ${if (makeSecondCopy) gradleSettingsFor(newRoot2.name) else ""}
    </option>
  </component>
</project>
    """.trim()
  )

  miscXml.writeText(
    """
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
<component name="ProjectRootManager" version="2" project-jdk-name="$testJdkName" project-jdk-type="JavaSDK" />
</project>
    """.trim()
  )
}

internal fun patchMppProject(
  projectRoot: File,
  enableHierarchicalSupport: Boolean,
  convertAppToKmp: Boolean = false,
  addJvmTo: List<String> = emptyList(),
  addIntermediateTo: List<String> = emptyList(),
  addJsModule: Boolean = false
) {
  if (enableHierarchicalSupport) {
    projectRoot.resolve("gradle.properties").replaceInContent(
      "kotlin.mpp.hierarchicalStructureSupport=false",
      "kotlin.mpp.hierarchicalStructureSupport=true"
    )
  }
  if (convertAppToKmp) {
    projectRoot.resolve("app").resolve("build.gradle").replaceInContent(
      """
        plugins {
            id 'com.android.application'
            id 'kotlin-android'
        }
      """.trimIndent(),
      """
        plugins {
            id 'com.android.application'
            id 'kotlin-multiplatform'
        }
        kotlin {
            android()
        }
     """.trimIndent(),
    )
  }
  for (module in addJvmTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceInContent(
      "android()",
      "android()\njvm()"
    )
  }
  for (module in addIntermediateTo) {
    projectRoot.resolve(module).resolve("build.gradle").replaceInContent(
      """
        |sourceSets {
      """.trimMargin(),
      """
        |sourceSets {
        |  create("jvmAndAndroid") {
        |    dependsOn(commonMain)
        |    androidMain.dependsOn(it)
        |    jvmMain.dependsOn(it)
        |  }
      """.trimMargin()
    )
  }
  if (addJsModule) {
    projectRoot.resolve("settings.gradle")
      .replaceInContent("//include ':jsModule'", "include ':jsModule'")
    // "org.jetbrains.kotlin.js" conflicts with "clean" task.
    projectRoot.resolve("build.gradle")
      .replaceInContent("task clean(type: Delete)", "task clean1(type: Delete)")
  }
}

internal fun AgpVersionSoftwareEnvironmentDescriptor.updateProjectJdk(projectRoot: File) {
  val jdk = IdeSdks.getInstance().jdk ?: error("${SyncedProjectTest::class} requires a valid JDK")
  val miscXml = projectRoot.resolve(".idea").resolve("misc.xml")
  miscXml.writeText(miscXml.readText().replace("""project-jdk-name="1.8"""", """project-jdk-name="${jdk.name}""""))
}

internal fun createEmptyGradleSettingsFile(projectRootPath: File) {
  val settingsFilePath = File(projectRootPath, SdkConstants.FN_SETTINGS_GRADLE)
  Truth.assertThat(FileUtil.delete(settingsFilePath)).isTrue()
  FileUtils.writeToFile(settingsFilePath, " ")
  Truth.assertAbout(FileSubject.file()).that(settingsFilePath).isFile()
  AndroidTestBase.refreshProjectFiles()
}

private fun AgpVersionSoftwareEnvironmentDescriptor.agpSuffix(): String = when (this) {
  AgpVersionSoftwareEnvironmentDescriptor.AGP_80 -> "_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_80_V1 -> "_NewAgp_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_31 -> "_Agp_3.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_33_WITH_5_3_1 -> "_Agp_3.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_33 -> "_Agp_3.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_35 -> "_Agp_3.5_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_40 -> "_Agp_4.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_41 -> "_Agp_4.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_42 -> "_Agp_4.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_70 -> "_Agp_7.0_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_71 -> "_Agp_7.1_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72_V1 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_72 -> "_Agp_7.2_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_73 -> "_Agp_7.3_"
  AgpVersionSoftwareEnvironmentDescriptor.AGP_74 -> "_Agp_7.4_"
}

private fun AgpVersionSoftwareEnvironmentDescriptor.gradleSuffix(): String {
  return gradleVersion?.let { "Gradle_${it}_" }.orEmpty()
}

internal fun migratePackageAttribute(root: File) {
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

