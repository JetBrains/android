/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.toolchain.runsGradleTemplates

import com.android.tools.idea.gradle.extensions.getPropertyPath
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.toolchain.GradleDaemonJvmCriteriaTemplatesManager
import com.android.tools.idea.gradle.toolchain.GradleDaemonJvmCriteriaTemplatesManager.TEMPLATE_METADATA_FILE
import com.android.tools.idea.gradle.util.PropertiesFiles
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getTopLevelBuildScriptSettingsPsiFile
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.getFoojayPluginVersion
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText

/**
 * Test for Daemon JVM criteria templates stored under /resources/templates/project/toolchain used for NPW projects. The properties
 * file storing the criteria are named as 'gradle-daemon-jvm-X.properties' where X represents the JVM version.
 *
 * Asserts that templates got generated based on expected default foojay plugin version, otherwise, will be required to run target
 * locally using '-DUPDATE_DAEMON_JVM_CRITERIA_TEMPLATES' to the jvm option from IDE or from bazel using:
 * bazel test [target]  \
 *      --jvmopt="-DUPDATE_DAEMON_JVM_CRITERIA_TEMPLATES=$(bazel info workspace)" \
 *      --sandbox_writable_path=$(bazel info workspace) \
 *      --test_strategy=standalone \
 *      --nocache_test_results \
 *
 * To be able to validate existing templates the 'updateDaemonJvm' Gradle task is executed using the applied default foojay plugin
 * version which end up making a network request to Disco API.
 */
private const val UPDATE_DAEMON_JVM_CRITERIA_TEMPLATES = "UPDATE_DAEMON_JVM_CRITERIA_TEMPLATES"
private const val TEMPLATE_METADATA_FOOJAY_PROPERTY = "foojayPluginVersion"
private const val TEMPLATE_WORKSPACE_PATH = "tools/adt/idea/project-system-gradle/resources/templates/project/toolchain"

@RunWith(Parameterized::class)
class GradleDaemonJvmCriteriaTemplatesTest(private val javaVersion: JavaVersion) {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "template {0} version")
    fun data(): Set<JavaVersion> {
      return JavaSdkVersion.entries
        .filter {
          try {
            GradleDaemonJvmCriteriaTemplatesManager.getTemplateCriteriaPropertiesContent(it.maxLanguageLevel.toJavaVersion()) != null
          } catch (_: Exception) {
            return@filter false
          }
        }
        .map { it.maxLanguageLevel.toJavaVersion() }
        // DEFAULT_JDK_VERSION is always added since validates missing default template given updated version
        .plus(IdeSdks.DEFAULT_JDK_VERSION.maxLanguageLevel.toJavaVersion()).toSet()
    }

    @AfterClass
    @JvmStatic
    fun afterClass() {
      if (System.getProperty(UPDATE_DAEMON_JVM_CRITERIA_TEMPLATES) != null) {
        updateTemplatesMetadataVersionOfFoojay()
      }
    }

    private fun getTemplatesBasedFooJayPluginVersion(): String {
      val properties = Properties()
      GradleDaemonJvmCriteriaTemplatesManager.getTemplateMetadata().openStream().use {
        properties.load(it)
      }
      return properties.getProperty(TEMPLATE_METADATA_FOOJAY_PROPERTY)
    }

    private fun updateTemplatesMetadataVersionOfFoojay() {
      val properties = Properties().apply {
        put(TEMPLATE_METADATA_FOOJAY_PROPERTY, getFoojayPluginVersion())
      }
      val metadataFile = workspaceTemplateFile(TEMPLATE_METADATA_FILE)
      PropertiesFiles.savePropertiesToFile(properties, metadataFile, """
        Represents the version of 'org.gradle.toolchains.foojay-resolver-convention' which was
        used to generate the different 'gradle-daemon-jvm-X.properties' template files.
      """.trimIndent())
    }

    private fun workspaceTemplateFile(fileName: String): File {
      val templateWorkspacePath = File(System.getProperty(UPDATE_DAEMON_JVM_CRITERIA_TEMPLATES))
      return templateWorkspacePath.resolve(TEMPLATE_WORKSPACE_PATH).resolve(fileName)
    }
  }

  @Test
  fun assertDaemonJvmCriteriaTemplateIsValid() {
    when {
      System.getProperty(UPDATE_DAEMON_JVM_CRITERIA_TEMPLATES) != null -> updateGradleDaemonJvmCriteriaTemplates()
      getTemplatesBasedFooJayPluginVersion() != getFoojayPluginVersion() ->
        error("""
          Daemon JVM criteria templates stored under 'templates/resources/toolchain' with the format 'gradle-daemon-jvm-X.properties'
          and used for NPW, have been created with different version of foojay resolver plugin.

          Re-run test to update templates in place using -DUPDATE_DAEMON_JVM_CRITERIA_TEMPLATES to the jvm options in the IDEA test
          configuration or from bazel using: --jvmopt=\"-DUPDATE_DAEMON_JVM_CRITERIA_TEMPLATES=$(bazel info workspace)\"
        """.trimIndent())
      else -> return // Templates are valid
    }
  }

  private fun updateGradleDaemonJvmCriteriaTemplates() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open {
      addFoojayPluginToGradleSettings(project, projectRoot.path)

      // Invoke updateDaemonJvm Gradle task which generates gradle-daemon-jvm.properties based on applied foojay resolved plugin
      val daemonJvmCriteria = GradleDaemonJvmCriteria(javaVersion.feature.toString(), null)
      GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, projectRoot.path, daemonJvmCriteria)
        .get(5, TimeUnit.MINUTES)

      // Update gradle-daemon-jvm-X.properties file located under /templates/project/toolchain
      val expectedProperties = GradleDaemonJvmPropertiesFile.getPropertyPath(projectRoot.absolutePath)
      val fileName = GradleDaemonJvmCriteriaTemplatesManager.getTemplatePropertiesFileName(javaVersion)
      workspaceTemplateFile(fileName).run {
        writeText(expectedProperties.readText())
      }
    }
  }

  private fun addFoojayPluginToGradleSettings(project: Project, externalProjectPath: String) =
    WriteCommandAction.runWriteCommandAction(project) {
      val settingsFile = getTopLevelBuildScriptSettingsPsiFile(project, externalProjectPath)!!
      val buildScriptSupport = GradleBuildScriptSupport.getManipulator(settingsFile)
      buildScriptSupport.addFoojayPlugin(settingsFile)
    }
}