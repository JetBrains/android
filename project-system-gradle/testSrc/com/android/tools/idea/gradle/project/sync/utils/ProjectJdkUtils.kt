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
import com.android.tools.idea.gradle.util.GradleProperties
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsLoaderBase
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GRADLE_JAVA_HOME_PROPERTY
import org.jetbrains.plugins.gradle.util.PROPERTIES_FILE_NAME
import java.io.File
import java.nio.file.Path

private const val PROJECT_IDEA_GRADLE_XML_PATH = ".idea/gradle.xml"
private const val PROJECT_IDEA_MISC_XML_PATH = ".idea/misc.xml"

class ProjectJdkUtils(
  private val disposable: Disposable,
  private val projectPath: Path,
  private val projectModules: List<String>
) {

  fun setProjectGradlePropertiesJdk(jdkPath: String) {
    val gradlePropertiesFile = projectPath.resolve(PROPERTIES_FILE_NAME).toFile()
    setGradlePropertiesJdk(gradlePropertiesFile, jdkPath)
  }

  fun setProjectIdeaGradleJdk(jdkName: String) = createProjectFile(
    relativePath = PROJECT_IDEA_GRADLE_XML_PATH,
    text = ProjectIdeaConfigFilesUtils.buildGradleXmlConfig(
      jdkName = jdkName,
      projectModules = projectModules
    )
  )

  fun setProjectIdeaJdk(jdkName: String) = createProjectFile(
    relativePath = PROJECT_IDEA_MISC_XML_PATH,
    text = ProjectIdeaConfigFilesUtils.buildMiscXmlConfig(jdkName)
  )

  fun getGradleJdkNameInMemory(project: Project): String? {
    val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath.toString())
    return settings?.gradleJvm
  }

  fun getGradleJdkNameFromIdeaGradleXmlFile(): String? {
    val gradleXml = projectPath.resolve(PROJECT_IDEA_GRADLE_XML_PATH)
    val gradleXmlRootElement = JpsLoaderBase.tryLoadRootElement(gradleXml)
    val gradleSettings = JDomSerializationUtil.findComponent(gradleXmlRootElement, "GradleSettings")
    val linkedExternalProjectsSettings = gradleSettings?.getOptionElement("linkedExternalProjectsSettings")
    val gradleProjectSettings = linkedExternalProjectsSettings?.getChild("GradleProjectSettings")
    val gradleJvm = gradleProjectSettings?.getOptionElement("gradleJvm")
    return gradleJvm?.getAttributeValue("value")
  }

  fun getProjectJdkNameInMemory(project: Project): String? {
    return ProjectRootManager.getInstance(project).projectSdk?.name
  }

  fun getProjectJdkNameInIdeaXmlFile(): String? {
    val projectConfigFile = projectPath.resolve(PROJECT_IDEA_MISC_XML_PATH)
    val rootElement = JpsLoaderBase.tryLoadRootElement(projectConfigFile)
    val projectRootManagerComponent = JDomSerializationUtil.findComponent(rootElement, "ProjectRootManager")
    return projectRootManagerComponent?.getAttributeValue("project-jdk-name")
  }

  fun getGradleDaemonExecutionJdkPath(project: Project): String? {
    val gradleInstallation = (GradleInstallationManager.getInstance() as AndroidStudioGradleInstallationManager)
    return gradleInstallation.getGradleJvmPath(project, projectPath.toString())
  }

  fun setUserHomeGradlePropertiesJdk(jdkPath: String) {
    val gradlePropertiesFile = GradleUtils.getUserGradlePropertiesFile()
    setGradlePropertiesJdk(gradlePropertiesFile, jdkPath)
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

  private fun createProjectFile(relativePath: String, text: String) {
    VfsUtil.findFile(projectPath, true)?.let {
      VfsTestUtil.createFile(it, relativePath, text)
    } ?: run {
      throw RuntimeException("Unable to find Vfs project file")
    }
  }

  private fun setGradlePropertiesJdk(gradlePropertiesFile: File, jdkPath: String) {
    FileUtil.createIfNotExists(gradlePropertiesFile)
    GradleProperties(gradlePropertiesFile).run {
      properties.setProperty(GRADLE_JAVA_HOME_PROPERTY, jdkPath)
      save()
    }
  }
}