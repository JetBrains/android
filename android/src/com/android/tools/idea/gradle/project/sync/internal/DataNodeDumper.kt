/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("DataNodeDumper")

package com.android.tools.idea.gradle.project.sync.internal

import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel
import com.android.tools.idea.gradle.project.model.NdkVariant
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.sanitizeFileName
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

fun <T : Any> DataNode<T>.dump(): String = buildString {

  fun Any.dumpData() = when (this) {
    is ModuleData -> "\n" + """
      id = $id
      owner = $owner
      moduleTypeId = $moduleTypeId
      externalName = $externalName
      moduleFileDirectoryPath = $moduleFileDirectoryPath
      externalConfigPath = $linkedExternalProjectPath""".replaceIndent("    ")
    is GradleModuleModel -> "\n" + """
      moduleName = $moduleName
      taskNames = ${taskNames.take(3)}...
      gradlePath = $gradlePath
      rootFolderPath = $rootFolderPath
      gradlePlugins = $gradlePlugins
      buildFilePath = $buildFilePath
      gradleVersion = $gradleVersion
      agpVersion = $agpVersion
      isKaptEnabled = $isKaptEnabled""".replaceIndent("    ")
    is ExternalProject -> "\n" + """
      externalSystemId = $externalSystemId
      id = $id
      name = $name
      qName = $qName
      description = $description
      group = $group
      version = $version
      getChildProjects = $childProjects
      projectDir = $projectDir
      buildDir = $buildDir
      buildFile = $buildFile
      tasks = ${tasks.entries.take(3)}...
      sourceSets = $sourceSets
      artifacts = ${artifacts.take(3)}...
      artifactsByConfiguration = ${artifactsByConfiguration.entries.take(3)}...
      """.replaceIndent("    ")
    is JavaModuleModel -> "\n" + """
      isBuildable = ${isBuildable}
      languageLevel = ${javaLanguageLevel}
      buildFolderPath = ${buildFolderPath}
      contentRoots = ${contentRoots}
      javaModuleDependencies = ${javaModuleDependencies}
      jarLibraryDependencies = ${jarLibraryDependencies}
      artifactsByConfiguration = ${artifactsByConfiguration}
      configurations = ${configurations}
      """.replaceIndent("    ")
    is AndroidModuleModel -> format()
    is NdkModuleModel -> format()
    else -> toString()
  }

  fun DataNode<T>.dumpNode(prefix: String = "") {
    val v: T = data
    appendln("$key(${v.javaClass.typeName}) ==> ${v.dumpData()}".replaceIndent(prefix))

    children
      .filterNot { it.data is com.intellij.openapi.externalSystem.model.task.TaskData }
      .forEach { it.safeAs<DataNode<T>>()?.dumpNode("$prefix    ") }
  }

  this@dump.dumpNode()
}

fun NdkModuleModel.format(): String = "\n" + """
    moduleName = ${moduleName}
    rootDirPath = ${rootDirPath}
    ndkModel = ${ndkModel.format()}
    features = ${features.format()}
  """

fun <T> Collection<T>.format(format: T.() -> String): String = """[
${this.joinToString(separator = "\n") { "      " + (it?.format() ?: "") }.prependIndent("    ")}
    ]
  """

fun NdkVariant.format(): String = """{
        name = ${name}
        sourceFolders = ${sourceFolders}
        artifacts = ${artifacts.format("          ")}
    }
  """

@Suppress("DEPRECATION")
fun AndroidModuleModel.format(): String = "\n" + """
    androidProject = ${androidProject.format()}
    selectedMainCompileLevel2Dependencies = ${selectedMainCompileLevel2Dependencies.format()}
    selectedAndroidTestCompileDependencies = ${selectedAndroidTestCompileDependencies?.format()}
    features = ${features.format()}
    modelVersion = $modelVersion
    mainArtifact = ${mainArtifact.format()}
    defaultSourceProvider = ${defaultSourceProvider.format()}
    activeSourceProviders = ${activeSourceProviders.format()}
    unitTestSourceProviders = ${unitTestSourceProviders.format()}
    androidTestSourceProviders = ${androidTestSourceProviders.format()}
    allSourceProviders = ${allSourceProviders.format()}
    applicationId = $applicationId
    allApplicationIds = $allApplicationIds
    isDebuggable = $isDebuggable
    minSdkVersion = $minSdkVersion
    runtimeMinSdkVersion = $runtimeMinSdkVersion
    targetSdkVersion = $targetSdkVersion
    versionCode = $versionCode
    projectSystemId = $projectSystemId
    buildTypes = ${buildTypes.format()}
    productFlavors = ${productFlavors.format()}
    moduleName = $moduleName
    rootDirPath = $rootDirPath
    selectedVariant = ${selectedVariant.format()}
    buildTypeNames = ${buildTypeNames.format()}
    productFlavorNames = ${productFlavorNames.format()}
    variantNames = ${variantNames.format()}
    javaLanguageLevel = $javaLanguageLevel
    overridesManifestPackage = ${overridesManifestPackage()}
    artifactForAndroidTest = ${artifactForAndroidTest?.format()}
    testExecutionStrategy = $testExecutionStrategy
    classJarProvider = $classJarProvider
    namespacing = $namespacing
    desugaring = $desugaring
    resValues = $resValues
    """.replaceIndent("    ")

private fun Any.format(prefix: String = "      "): String {
  val text = this.toString()
  return buildString {
    try {
      var prefix = prefix
      text.forEachIndexed { index, c ->
        val n = text.getOrElse(index + 1) { '#' }
        val p = text.getOrElse(index - 1) { '#' }
        when {
          c == '{' && n != '}' -> {
            appendln("{")
            prefix += "  "
            append(prefix)
          }
          c == '}' && p != '{' -> {
            appendln()
            prefix = prefix.substring(2)
            append(prefix)
            append("}")
          }
          c == '[' && n != ']' -> {
            appendln("[")
            prefix += "  "
            append(prefix)
          }
          c == ']' && p != '[' -> {
            appendln()
            prefix = prefix.substring(2)
            append(prefix)
            append("]")
          }
          c == ' ' && p == ',' -> {
            appendln()
            append(prefix)
          }
          c == '\n' -> {
            appendln()
            append(prefix)
          }
          else -> append(c)
        }
      }
    } catch (t: Throwable) {
      appendln("**********")
      appendln(t)
    }
  }
}

class DumpProjectDataAction : DumbAwareAction("Dump Project Data Nodes") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val dataManager = ProjectDataManager.getInstance()
    val projectPath = Projects.getBaseDirPath(project).path
    val data = dataManager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, projectPath) ?: return
    val dump = data.externalProjectStructure?.dump() ?: return
    val outputFile = File(File(projectPath), sanitizeFileName(project.name) + ".project_data_nodes_dump")
    outputFile.writeText(dump)
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, VfsUtil.findFileByIoFile(outputFile, true)!!), true)
    VfsUtil.markDirtyAndRefresh(true, false, false, outputFile)
    println("Dumped to: file://$outputFile")
  }
}
