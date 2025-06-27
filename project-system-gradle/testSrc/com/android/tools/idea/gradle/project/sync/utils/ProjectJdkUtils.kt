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
package com.android.tools.idea.gradle.project.sync.utils
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.sync.extensions.getOptionElement
import com.android.tools.idea.gradle.project.sync.extensions.getOptionElementName
import com.android.tools.idea.gradle.project.sync.model.GradleDaemonToolchain
import com.android.tools.idea.gradle.project.sync.model.GradleRoot
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.gradle.util.GradleProperties
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsComponentLoader
import org.jetbrains.plugins.gradle.properties.GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.properties.GRADLE_FOLDER
import org.jetbrains.plugins.gradle.properties.GRADLE_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_PROPERTIES_FILE_NAME
import java.io.File
import java.nio.file.Paths

private const val PROJECT_DIR = "${'$'}PROJECT_DIR${'$'}"
private const val PROJECT_IDEA_GRADLE_XML_PATH = "$DIRECTORY_STORE_FOLDER/gradle.xml"
private const val PROJECT_IDEA_MISC_XML_PATH = "$DIRECTORY_STORE_FOLDER/misc.xml"

object ProjectJdkUtils {

  fun setProjectGradleLocalJavaHome(projectRoot: File, javaHome: String) {
    GradleConfigProperties(projectRoot).run {
      this.javaHome = File(javaHome)
      save()
    }
  }

  fun setProjectGradleDaemonJvmCriteria(projectRoot: File, gradleDaemonToolchain: GradleDaemonToolchain) {
    createProjectFile(
      projectRoot = projectRoot,
      relativePath = Paths.get(GRADLE_FOLDER, GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME).toString(),
      text = GradleUtils.buildDamonJvmCriteriaProperties(gradleDaemonToolchain)
    )

    val gradlePropertiesFile = projectRoot.resolve(GRADLE_PROPERTIES_FILE_NAME)
    addPropertiesToFile(
      propertiesFile = gradlePropertiesFile,
      "org.gradle.java.installations.auto-detect" to gradleDaemonToolchain.autoDetectionEnabled.toString(),
      "org.gradle.java.installations.auto-download" to gradleDaemonToolchain.autoProvisioningEnabled.toString(),
      "org.gradle.java.installations.paths" to gradleDaemonToolchain.customToolchainInstallationsPath.joinToString(","),
      "org.gradle.java.installations.fromEnv" to gradleDaemonToolchain.customToolchainInstallationsEnv?.joinToString(",")
    )
  }

  fun setProjectGradlePropertiesJavaHome(projectRoot: File, javaHome: String) {
    val gradlePropertiesFile = projectRoot.resolve(GRADLE_PROPERTIES_FILE_NAME)
    addPropertiesToFile(gradlePropertiesFile, GRADLE_JAVA_HOME_PROPERTY to javaHome)
  }

  fun setProjectIdeaGradleJdk(projectRoot: File, gradleRoots: List<GradleRoot>) = createProjectFile(
    projectRoot = projectRoot,
    relativePath = PROJECT_IDEA_GRADLE_XML_PATH,
    text = ProjectIdeaConfigFilesUtils.buildGradleXmlConfig(gradleRoots)
  )

  fun setProjectIdeaMiscJdk(projectRoot: File, jdkName: String) = createProjectFile(
    projectRoot = projectRoot,
    relativePath = PROJECT_IDEA_MISC_XML_PATH,
    text = ProjectIdeaConfigFilesUtils.buildMiscXmlConfig(jdkName)
  )

  fun getGradleRootJdkNameInMemory(project: Project, gradleRootName: String): String? {
    val linkedProjectPath = File(project.basePath.orEmpty()).resolve(gradleRootName).absolutePath
    val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(linkedProjectPath)
    return settings?.gradleJvm
  }

  fun getGradleRootJdkNameFromIdeaGradleXmlFile(projectRoot: File, gradleRootName: String): String? {
    val gradleXml = projectRoot.resolve(PROJECT_IDEA_GRADLE_XML_PATH)
    val gradleXmlRootElement = JpsComponentLoader.tryLoadRootElement(gradleXml.toPath())
    val gradleSettings = JDomSerializationUtil.findComponent(gradleXmlRootElement, "GradleSettings")
    val linkedExternalProjectsSettings = gradleSettings?.getOptionElement("linkedExternalProjectsSettings")
    return linkedExternalProjectsSettings?.content?.firstOrNull { gradleProjectSettings ->
      val externalProjectPath = gradleProjectSettings?.getOptionElementName("externalProjectPath")?.getAttributeValue("value")
      externalProjectPath == null || externalProjectPath == PROJECT_DIR || externalProjectPath == "$PROJECT_DIR/$gradleRootName"
    }?.getOptionElementName("gradleJvm")?.getAttributeValue("value")
  }

  fun getProjectJdkNameInMemory(project: Project): String? {
    return ProjectRootManager.getInstance(project).projectSdk?.name
  }

  fun getProjectJdkNameInIdeaXmlFile(projectRoot: File): String? {
    val projectConfigFile = projectRoot.resolve(PROJECT_IDEA_MISC_XML_PATH).toPath()
    val rootElement = JpsComponentLoader.tryLoadRootElement(projectConfigFile)
    val projectRootManagerComponent = JDomSerializationUtil.findComponent(rootElement, "ProjectRootManager")
    return projectRootManagerComponent?.getAttributeValue("project-jdk-name")
  }

  fun getGradleDaemonExecutionJdkPath(project: Project, gradleRootPath: @SystemIndependent String): String? {
    val gradleInstallation = (GradleInstallationManager.getInstance() as AndroidStudioGradleInstallationManager)
    return gradleInstallation.getGradleJvmPath(project, gradleRootPath)
  }

  fun setUserHomeGradlePropertiesJdk(jdkPath: String, disposable: Disposable) {
    val gradlePropertiesFile = GradleUtils.getUserGradlePropertiesFile()
    addPropertiesToFile(gradlePropertiesFile, GRADLE_JAVA_HOME_PROPERTY to jdkPath)
    Disposer.register(disposable) {
      clearUserHomeGradleProperties()
    }
  }

  private fun clearUserHomeGradleProperties() {
    val gradlePropertiesFile = GradleUtils.getUserGradlePropertiesFile()
    if (gradlePropertiesFile.exists()) {
      GradleProperties(gradlePropertiesFile).run {
        clear()
        save()
      }
    }
  }

  private fun createProjectFile(projectRoot: File, relativePath: String, text: String) {
    FileUtil.writeToFile(projectRoot.resolve(relativePath), text)
  }

  private fun addPropertiesToFile(propertiesFile: File, vararg newProperties: Pair<String, String?>) {
    FileUtil.createIfNotExists(propertiesFile)
    GradleProperties(propertiesFile).run {
      newProperties.forEach { (key, value) ->
        value?.let { properties.setProperty(key, it) }
      }
      save()
    }
  }
}