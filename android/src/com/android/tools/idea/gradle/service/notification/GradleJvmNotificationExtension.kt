/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification

import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.sdk.IdeSdks
import com.android.utils.FileUtils
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_INTERNAL_JAVA
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.externalSystem.service.notification.NotificationData
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.notification.GradleNotificationExtension
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.io.File
import java.nio.file.Paths

/**
 * Adds more information to a notification when the selected Gradle JVM is not valid. If the notification message hast this pattern
 * "Invalid Gradle JDK configuration found.<Notification message suffix>", then this extension will replace this message with this:
 *
 * "Invalid Gradle JDK configuration found.<Cause of invalid JDK error, if a reason was found>\n
 *  <Notification message suffix>\n
 *  <Use embedded JDK quickfix (if applicable)\n>
 *  <Use default JDK quickfix (if applicable)\n>
 *  <Change JDK location link (if not added already)\n>
 * "
 *
 * For example, this message:
 * """
 * Invalid Gradle JDK configuration found. <a href='#open_external_system_settings'>Open Gradle Settings</a>
 * """
 *
 * will be replace with:
 * """
 * Invalid Gradle JDK configuration found. ProjectJdkTable table does not have an entry of type JavaSDK named JDK.
 * <a href='#open_external_system_settings'>Open Gradle Settings</a>
 * <a href="use.jdk.as.project.jdk.embedded">Use Embedded JDK (<path to Embedded JDK>)</a>
 * <a href="use.jdk.as.project.jdk">Use JDK 1.8 (<path to JDK 1.8>)</a>
 * <a href="open.project.jdk.location">Change JDK location</a>
 * """
 *
 * The current reason displayed are these:
 *
 *  - "Neither gradleJvm nor project-jdk-name are defined.":
 *    gradle.xml does not exist and project-jdk-name is not defined
 *  - "project-jdk-name is not defined.":
 *    gradle.xml exists and gradleJvm is set to [USE_PROJECT_JDK] but project-jdk-name is not defined
 *  - "Project set to use JAVA_HOME but the path is invalid.":
 *    gradle.xml exists and gradleJvm is set to [USE_JAVA_HOME] but JAVA_HOME is not a valid path
 *  - "SystemProperties.javaHome is not valid.":
 *    gradle.xml exists and gradleJvm is set to [USE_INTERNAL_JAVA] but internal (SystemProperties.javaHome) jdk is not a valid
 *  - "Could not find the required <jdk type name>.":
 *    A jdk name can be found in gradleJvm or project-jdk-name but the JDK table does not have it
 *  - "JDK home path is not defined.":
 *    A jdk name can be found in gradleJvm or project-jdk-name and the JDK table has it, but there is no home path information on it
 *  - "There is no bin/javac in <jdk path>":
 *    A jdk name can be found in gradleJvm or project-jdk-name and the JDK table has it, but there is no bin/javac in that path
 *  - "Required JDK files from <jdk path> are missing":
 *    A jdk name can be found in gradleJvm or project-jdk-name and the JDK table has it, but there are missing files (other than javac)
 */
class GradleJvmNotificationExtension: GradleNotificationExtension() {
  override fun customize(notificationData: NotificationData, project: Project, error: Throwable?) {
    super.customize(notificationData, project, error)
    val expectedPrefix = GradleBundle.message("gradle.jvm.is.invalid")
    if (notificationData.message.startsWith(expectedPrefix)) {
      val ideSdks = IdeSdks.getInstance()
      // Add more information on why it is not valid
      val projectJdkPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, project.basePath!!)
      val errorInfo = if (projectJdkPath == null) {
        getEmptyJdkPathReason(project)
      } else {
        ideSdks.generateInvalidJdkReason(Paths.get(projectJdkPath))
      }
      var modifiedMessage = expectedPrefix
      if (errorInfo != null) {
        modifiedMessage += " $errorInfo"
      }
      val suffixMessage = notificationData.message.removePrefix(expectedPrefix).trim()
      if (suffixMessage.isNotEmpty()) {
        modifiedMessage += "\n$suffixMessage"
      }
      notificationData.message = "$modifiedMessage\n"
      // Suggest use embedded
      var registeredListeners = notificationData.registeredListenerIds
      val embeddedJdkPath = ideSdks.embeddedJdkPath
      if (embeddedJdkPath != null) {
        if (ideSdks.validateJdkPath(embeddedJdkPath) != null) {
          val absolutePath = embeddedJdkPath.toAbsolutePath().toString()
          val listener = UseJdkAsProjectJdkListener(project, absolutePath, ".embedded")
          if (!registeredListeners.contains(listener.id)) {
            notificationData.message = notificationData.message + "<a href=\"${listener.id}\">Use Embedded JDK ($absolutePath)</a>\n"
            notificationData.setListener(listener.id, listener)
            registeredListeners = notificationData.registeredListenerIds
          }
        }
      }
      // Suggest IdeSdks.jdk (if different to embedded)
      val defaultJdk = ideSdks.jdk
      val defaultPath = defaultJdk?.homePath
      if (defaultPath != null) {
        if (ideSdks.validateJdkPath(Paths.get(defaultPath)) != null) {
          if (embeddedJdkPath == null || (!FileUtils.isSameFile(embeddedJdkPath.toFile(), File(defaultPath)))) {
            val listener = UseJdkAsProjectJdkListener(project, defaultPath)
            if (!registeredListeners.contains(listener.id)) {
              notificationData.message = notificationData.message + "<a href=\"${listener.id}\">Use JDK ${defaultJdk.name} ($defaultPath)</a>\n"
              notificationData.setListener(listener.id, listener)
              registeredListeners = notificationData.registeredListenerIds
            }
          }
        }
      }
      // Add change JDK location link
      if ((registeredListeners != null) && (!registeredListeners.contains(OpenProjectJdkLocationListener.ID))) {
        val service = ProjectSettingsService.getInstance(project)
        if (service is AndroidProjectSettingsService) {
          val listener = OpenProjectJdkLocationListener(service)
          notificationData.message = notificationData.message + "<a href=\"${OpenProjectJdkLocationListener.ID}\">Change JDK location</a>\n"
          notificationData.setListener(OpenProjectJdkLocationListener.ID, listener)
        }
      }
    }
  }

  private fun getEmptyJdkPathReason(project: Project): String? {
    val basePath = project.basePath!!
    val settings = GradleSettings.getInstance(project).getLinkedProjectSettings(basePath)
    if (settings == null) {
      // Does not come from settings
      val jdkName = ProjectRootManager.getInstance(project).projectSdkName ?: return "Neither gradleJvm nor project-jdk-name are defined."
      return projectJdkInvalidMessage(jdkName)
    }

    when (val gradleJvm = settings.gradleJvm) {
      null, USE_PROJECT_JDK -> {
        val jdkName = ProjectRootManager.getInstance(project).projectSdkName ?: return "project-jdk-name is not defined."
        return projectJdkInvalidMessage(jdkName)
      }
      USE_JAVA_HOME -> {
        return "Project set to use JAVA_HOME but the path is invalid."
      }
      USE_INTERNAL_JAVA -> {
        return "SystemProperties.javaHome is not valid."
      }
      else -> {
        return projectJdkInvalidMessage(gradleJvm)
      }
    }
  }

  private fun projectJdkInvalidMessage(jdkName: String): String? {
    val javaSdkType = ExternalSystemJdkUtil.getJavaSdkType()
    val existingJdk = ProjectJdkTable.getInstance().findJdk(jdkName, javaSdkType.name)
                      ?: return "Could not find the required ${javaSdkType.name}."
    val jdkPath = existingJdk.homePath ?: return "JDK home path is not defined."
    return IdeSdks.getInstance().generateInvalidJdkReason(Paths.get(jdkPath))
  }
}
