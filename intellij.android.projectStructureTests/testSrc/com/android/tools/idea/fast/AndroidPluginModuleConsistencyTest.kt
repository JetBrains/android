// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.fast

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.jdom.Element
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JDomSerializationUtil
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class AndroidPluginModuleConsistencyTest : AndroidPluginProjectConsistencyTestCase() {
  /**
   * The following Android modules exist only in IJ Ultimate and IJ Community projects
   * and should not be referenced in Android plugin projects.
   */
  private val intelliJOnlyAndroidModules = listOf(
    // The IntelliJ project consistency test module that exists only in IJ monorepo
    "intellij.android.projectStructureTests",
    // Remote dev module available only in ultimate
    "intellij.android.backend.split"
  )

  @Test
  fun `modules from 'Android plugin' repository are added to 'Ultimate'`() {
    val androidProject = androidProject
    val androidProjectHomePath = androidHomePath
    val ultimateProject = ultimateProject
    val ultimateProjectHomePath = ultimateHomePath

    val androidModules = androidProject.modules.toHashSet()
    val androidModulesNames = androidModules
      .mapTo(HashSet()) { it.name }

    val ultimateAndroidModules = ultimateProject
      .modules
      .filter { it.isAndroidModule }
      .associateBy { it.name }

    val missingAndroidModulesInUltimate = androidModules
      .filter { it.name !in ultimateAndroidModules.keys }

    val (missingAndroidModulesInAndroidPlugin, obsoleteAndroidModulesInUltimate) = ultimateAndroidModules
      .values
      .filter { it.isAndroidModule && it.name !in androidModulesNames && it.name !in intelliJOnlyAndroidModules }
      .partition { it.exists() }

    if (missingAndroidModulesInAndroidPlugin.isNotEmpty()) {
      failWithFileComparisonError(androidProject, androidProjectHomePath, missingAndroidModulesInAndroidPlugin, emptyList())
    }

    if ((missingAndroidModulesInUltimate + obsoleteAndroidModulesInUltimate).isNotEmpty()) {
      failWithFileComparisonError(ultimateProject,
                                  ultimateProjectHomePath,
                                  missingAndroidModulesInUltimate,
                                  obsoleteAndroidModulesInUltimate)
    }
  }

  @Test
  fun `modules from 'Android plugin' repository are added to 'Community'`() {
    val androidProject = androidProject
    val androidProjectHomePath = androidHomePath
    val communityProject = communityProject
    val communityProjectHomePath = communityHomePath

    val androidModules = androidProject.modules.toHashSet()
    val androidModulesNames = androidModules
      .mapTo(HashSet()) { it.name }

    val communityAndroidModules = communityProject
      .modules
      .filter { it.isAndroidModule }
      .associateBy { it.name }

    val missingAndroidModulesInCommunity = androidModules
      .filter { it.name !in communityAndroidModules.keys }

    val (missingAndroidModulesInAndroidPlugin, obsoleteAndroidModulesInCommunity) = communityAndroidModules
      .values
      .filter { it.isAndroidModule && it.name !in androidModulesNames && it.name !in intelliJOnlyAndroidModules }
      .partition { it.exists() }

    if (missingAndroidModulesInAndroidPlugin.isNotEmpty()) {
      failWithFileComparisonError(androidProject, androidProjectHomePath, missingAndroidModulesInAndroidPlugin, emptyList())
    }

    if ((missingAndroidModulesInCommunity + obsoleteAndroidModulesInCommunity).isNotEmpty()) {
      failWithFileComparisonError(communityProject,
                                  communityProjectHomePath,
                                  missingAndroidModulesInCommunity,
                                  obsoleteAndroidModulesInCommunity)
    }
  }

  private fun failWithFileComparisonError(
    project: JpsProject,
    projectPath: Path,
    missingAndroidModulesInProject: List<JpsModule>,
    obsoleteAndroidModulesInProject: List<JpsModule>,
  ) {
    val modulesXmlPath = projectPath.resolve(".idea/modules.xml")
    val projectName = project.name
      .let { projectName -> if (projectName.startsWith("Android")) "Android" else projectName }
      .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    val message = constructErrorMessage(projectName = projectName,
                                        moduleXmlFilePath = modulesXmlPath,
                                        missingAndroidModulesInProject = missingAndroidModulesInProject,
                                        obsoleteAndroidModulesInProject = obsoleteAndroidModulesInProject)

    val expectedModulesXmlContent = generateExpectedModulesXmlContent(projectPath,
                                                                      modulesXmlPath,
                                                                      missingModules = missingAndroidModulesInProject,
                                                                      obsoleteModules = obsoleteAndroidModulesInProject)
    val actualModulesXmlContent = Files.readString(modulesXmlPath)
    val actualFilePath = modulesXmlPath.toAbsolutePath().toString()

    throw FileComparisonFailedError(
      message = message,
      expected = expectedModulesXmlContent,
      actual = actualModulesXmlContent,
      expectedFilePath = null,
      actualFilePath = actualFilePath
    )
  }

  private fun constructErrorMessage(
    projectName: String,
    moduleXmlFilePath: Path,
    missingAndroidModulesInProject: List<JpsModule>,
    obsoleteAndroidModulesInProject: List<JpsModule>,
  ): String {
    val missingModuleNamesString = missingAndroidModulesInProject.joinToString(", ", prefix = "[", postfix = "]") { it.name }
    val obsoleteModuleNamesString = obsoleteAndroidModulesInProject.joinToString(", ", prefix = "[", postfix = "]") { it.name }

    return buildString {
      if (missingAndroidModulesInProject.isNotEmpty()) {
        appendLine("The following Android modules are missing in '$projectName' project:")
        appendLine(missingModuleNamesString)
        appendLine()
        appendLine("Please register the modules in the 'module.xml' file: $moduleXmlFilePath")
        appendLine()
      }

      if (obsoleteAndroidModulesInProject.isNotEmpty()) {
        appendLine(
          "The following Android modules are registered in '$projectName' project, but they are no longer registered in the `Android` plugin:")
        appendLine(obsoleteModuleNamesString)
        appendLine()
        appendLine("Please remove the modules from the 'module.xml' file: $moduleXmlFilePath")
      }
    }
  }

  private val JpsModule.isAndroidModule: Boolean
    get() = this.name.startsWith("intellij.android")

  private fun generateExpectedModulesXmlContent(
    projectHomePath: Path,
    moduleXmlFilePath: Path,
    missingModules: List<JpsModule> = emptyList(),
    obsoleteModules: List<JpsModule> = emptyList(),
  ): String {
    val element = buildNsUnawareJdom(moduleXmlFilePath)
    val modulesElement = JDomSerializationUtil.findComponent(element, "ProjectModuleManager")!!.getChild("modules")!!
    val modulesElements = modulesElement.children.toList()
    modulesElements.forEach { it.detach() }
    val missingElements = missingModules.map {
      val relativeImlPath = "${FileUtil.getRelativePath(File(projectHomePath.absolutePathString()), it.baseDirectory)}/${it.name}.iml"
      Element("module")
        .setAttribute("fileurl", "file://\$PROJECT_DIR\$/$relativeImlPath")
        .setAttribute("filepath", "\$PROJECT_DIR\$/$relativeImlPath")
    }
    val nonObsoleteModules = modulesElements.filterNonObsoleteModules(obsoleteModules)
    val expectedModulesElements = (nonObsoleteModules + missingElements).sortedBy {
      it.getAttributeValue("filepath")!!.substringAfterLast("/").removeSuffix(".iml")
    }
    expectedModulesElements.forEach {
      modulesElement.addContent(it)
    }
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n${JDOMUtil.write(element).trimEnd('\n')}"
  }

  private fun List<Element>.filterNonObsoleteModules(obsoleteModules: List<JpsModule>): List<Element> = this.filter { element ->
    obsoleteModules.none { module -> element.getAttributeValue("filepath").contains(module.name) }
  }

  private fun JpsModule.exists(): Boolean = Paths.get("${this.baseDirectory}/${this.name}.iml").exists()

  private val JpsModule.baseDirectory: File
    get() = JpsModelSerializationDataService.getModuleExtension(this)!!.baseDirectory
}