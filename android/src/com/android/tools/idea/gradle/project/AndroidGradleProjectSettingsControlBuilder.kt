/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.gradle.ui.GradleJdkComboBox
import com.android.tools.idea.gradle.util.GradleJdkComboBoxUtil
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.gradle.service.settings.IdeaGradleProjectSettingsControlBuilder
import org.jetbrains.plugins.gradle.service.settings.JavaGradleProjectSettingsControlBuilder
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.getGradleJvmLookupProvider
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.io.path.absolutePathString

private const val GRADLE_JDK_LABEL_TEXT = "Gradle JDK:"

@Suppress("UnstableApiUsage")
class AndroidGradleProjectSettingsControlBuilder(
  private val myInitialSettings: GradleProjectSettings
) : JavaGradleProjectSettingsControlBuilder(myInitialSettings) {

  init {
    // Drop original JdkComponents so new ones can be generated
    super.dropGradleJdkComponents()
  }

  private var myInitialJdkName: String? = null
  private var dropGradleJdkComponents = false
  private var myGradleJdkLabel: JBLabel? = null
  private var myGradleJdkComboBoxWrapper: JPanel? = null
  private var myGradleJdkComboBox: GradleJdkComboBox? = null

  override fun dropGradleJdkComponents(): IdeaGradleProjectSettingsControlBuilder {
    dropGradleJdkComponents = true
    return this
  }

  override fun addGradleJdkComponents(content: JPanel?, indentLevel: Int): IdeaGradleProjectSettingsControlBuilder {
    if (!dropGradleJdkComponents) {
      val project = ProjectManager.getInstance().defaultProject
      myGradleJdkLabel = JBLabel(GRADLE_JDK_LABEL_TEXT)
      myGradleJdkComboBoxWrapper = JPanel(BorderLayout())
      recreateGradleJdkComboBox(project, ProjectSdksModel())

      myGradleJdkLabel!!.labelFor = myGradleJdkComboBoxWrapper

      content!!.add(myGradleJdkLabel!!, ExternalSystemUiUtil.getLabelConstraints(indentLevel))
      content.add(myGradleJdkComboBoxWrapper!!, ExternalSystemUiUtil.getFillLineConstraints(0))
    }
    return this
  }

  override fun validate(settings: GradleProjectSettings?): Boolean {
    if (myGradleJdkComboBox != null && !ApplicationManager.getApplication().isUnitTestMode) {
      when (val sdkInfo = myGradleJdkComboBox?.getSelectedGradleJvmInfo()) {
        is SdkInfo.Undefined -> throw ConfigurationException("Please, set the Gradle JDK option")
        is SdkInfo.Resolved -> {
          if (!ExternalSystemJdkUtil.isValidJdk(sdkInfo.homePath)) {
            throw ConfigurationException("Gradle JDK option is incorrect:\nPath: ${sdkInfo.homePath}")
          }
        }
        is SdkInfo.Resolving, SdkInfo.Unresolved, null -> {}
      }
    }
    return super.validate(settings)
  }

  override fun apply(settings: GradleProjectSettings?) {
    super.apply(settings)
    if (myGradleJdkComboBox != null) {
      wrapExceptions { myGradleJdkComboBox!!.applyModelChanges() }
      val gradleJvm = myGradleJdkComboBox!!.selectedGradleJvmReference
      settings!!.gradleJvm = if (StringUtil.isEmpty(gradleJvm)) null else gradleJvm
      IdeSdks.getInstance().setUseEnvVariableJdk(JDK_LOCATION_ENV_VARIABLE_NAME == gradleJvm)
    }
  }

  override fun isModified(): Boolean {
    if (myGradleJdkComboBox != null) {
      val gradleJvm = myGradleJdkComboBox!!.selectedGradleJvmReference
      if (!StringUtil.equals(gradleJvm, myInitialJdkName)) {
        return true
      }
      if (myGradleJdkComboBox!!.isModelModified()) {
        return true
      }
    }
    return super.isModified()
  }

  override fun reset(project: Project?, settings: GradleProjectSettings?, isDefaultModuleCreation: Boolean) {
    reset(project, settings, isDefaultModuleCreation, null)
  }

  override fun reset(project: Project?, settings: GradleProjectSettings?, isDefaultModuleCreation: Boolean, wizardContext: WizardContext?) {
    super.reset(project, settings, isDefaultModuleCreation, wizardContext)
    resetGradleJdkComboBox(project, settings, wizardContext)
  }

  override fun update(linkedProjectPath: String?, settings: GradleProjectSettings?, isDefaultModuleCreation: Boolean) {
    super.update(linkedProjectPath, settings, isDefaultModuleCreation)
  }

  override fun resetGradleJdkComboBox(project: Project?,
                                      settings: GradleProjectSettings?,
                                      wizardContext: WizardContext?) {
    if (myGradleJdkComboBox == null)
      return

    val checkedProject = if (project == null || project.isDisposed) ProjectManager.getInstance().defaultProject else project
    val structureConfigurable = ProjectStructureConfigurable.getInstance(checkedProject)
    val sdksModel = structureConfigurable.projectJdksModel

    val projectSdk = wizardContext?.projectJdk
    setupProjectSdksModel(sdksModel, checkedProject, projectSdk)
    recreateGradleJdkComboBox(checkedProject, sdksModel)

    var selectedSdk = settings!!.gradleJvm
    if (IdeSdks.getInstance().isUsingEnvVariableJdk) {
      selectedSdk = JDK_LOCATION_ENV_VARIABLE_NAME
    }
    if (selectedSdk == USE_PROJECT_JDK) {
      val resolvedJdk = myGradleJdkComboBox!!.getProjectSdk()
      if (resolvedJdk != null)
        selectedSdk = resolvedJdk.name
    }
    myInitialJdkName = selectedSdk
    myGradleJdkComboBox!!.selectedGradleJvmReference = selectedSdk
  }

  private fun recreateGradleJdkComboBox(project: Project, sdksModel: ProjectSdksModel) {
    if (myGradleJdkComboBox != null) {
      myGradleJdkComboBoxWrapper!!.remove(myGradleJdkComboBox!!.component)
    }
    // Add Android Studio specific jdks

    val ideSdks = IdeSdks.getInstance()
    val ideInfo = IdeInfo.getInstance()
    if (ideInfo.isAndroidStudio || ideInfo.isGameTools) {
      // Remove any invalid JDK
      ideSdks.removeInvalidJdksFromTable()
      // Add embedded
      ideSdks.embeddedJdkPath?.let {
        val embeddedJdkName = JavaSdk.getInstance().suggestSdkName(null, it.absolutePathString())
        addJdkIfNotPresent(sdksModel, embeddedJdkName, it)
      }
      // ADD JDK_LOCATION_ENV_VARIABLE_NAME
      if (ideSdks.isJdkEnvVariableValid) {
        addJdkIfNotPresent(sdksModel, JDK_LOCATION_ENV_VARIABLE_NAME, ideSdks.jdkPath!!)
      }
    }
    val projectJdk = sdksModel.projectSdk
    sdksModel.projectSdk = null
    val boxModel = GradleJdkComboBoxUtil.createBoxModel(project, sdksModel)
    sdksModel.projectSdk = projectJdk

    // TODO: Remove, used only for debug
    val jdkTable = ProjectJdkTable.getInstance()
    println("Current JDK's:")
    for (jdk in jdkTable.allJdks) {
      println("${jdk.name}: ${jdk.homePath}")
    }
    // TODO: End of remove

    myGradleJdkComboBox = GradleJdkComboBox(
      sdkComboBoxModel = boxModel,
      sdkLookupProvider = getGradleJvmLookupProvider(project, myInitialSettings),
      externalProjectPath = myInitialSettings.externalProjectPath
    )
    // Add JAVA_HOME
    IdeSdks.getJdkFromJavaHome()?.let {
      myGradleJdkComboBox?.addJdkReferenceItem(JAVA_HOME, it)
    }
    myGradleJdkComboBoxWrapper!!.add(myGradleJdkComboBox!!.component, BorderLayout.CENTER)
  }

  private fun addJdkIfNotPresent(sdksModel: ProjectSdksModel, name: String, jdkPath: Path) {
    if (sdksModel.findSdk(name) != null) {
      // Already exists, do not generate a new one
      return
    }
    val jdk = JavaSdk.getInstance().createJdk(name, jdkPath.toAbsolutePath().toString())
    sdksModel.addSdk(jdk)
  }

  private fun setupProjectSdksModel(sdksModel: ProjectSdksModel, project: Project, projectSdk: Sdk?) {
    var resolvedProjectSdk = projectSdk
    sdksModel.reset(project)
    deduplicateSdkNames(sdksModel)
    if (resolvedProjectSdk == null) {
      resolvedProjectSdk = sdksModel.projectSdk
      // Find real sdk
      // see ProjectSdksModel#getProjectSdk for details
      resolvedProjectSdk = sdksModel.findSdk(resolvedProjectSdk)
    }
    if (resolvedProjectSdk != null) {
      // resolves executable JDK
      // e.g: for Android projects
      resolvedProjectSdk = ExternalSystemJdkUtil.resolveDependentJdk(resolvedProjectSdk)
      // Find editable sdk
      // see ProjectSdksModel#getProjectSdk for details
      resolvedProjectSdk = sdksModel.findSdk(resolvedProjectSdk.name)
    }
    sdksModel.projectSdk = resolvedProjectSdk
  }

  private fun deduplicateSdkNames(projectSdksModel: ProjectSdksModel) {
    val processedNames: MutableSet<String> = HashSet()
    val editableSdks: Collection<Sdk> = projectSdksModel.projectSdks.values
    for (sdk in editableSdks) {
      if (processedNames.contains(sdk.name)) {
        val sdkModificator = sdk.sdkModificator
        val name = SdkConfigurationUtil.createUniqueSdkName(sdk.name, editableSdks)
        sdkModificator.name = name
        sdkModificator.commitChanges()
      }
      processedNames.add(sdk.name)
    }
  }

  private fun wrapExceptions(runnable: ThrowableRunnable<Throwable>) {
    try {
      runnable.run()
    }
    catch (ex: Throwable) {
      throw IllegalStateException(ex)
    }
  }
}