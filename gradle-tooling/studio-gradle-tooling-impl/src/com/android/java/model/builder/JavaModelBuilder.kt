/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.java.model.builder

import com.android.java.model.JavaLibrary
import com.android.java.model.JavaProject
import com.android.java.model.SourceSet
import com.android.java.model.impl.JavaLibraryImpl
import com.android.java.model.impl.JavaProjectImpl
import com.android.java.model.impl.LibraryVersionImpl
import com.android.java.model.impl.SourceSetImpl
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.SelfResolvingDependency
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.specs.Specs
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.util.VersionNumber
import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.HashMap

/**
 * Builder for the custom Java library model.
 */
class JavaModelBuilder : ToolingModelBuilder {

  /**
   * a map that goes from build name ([BuildIdentifier.getName] to the root dir of the
   * build.
   */
  private var buildMapping: Map<String, String> = hashMapOf()

  override fun canBuild(modelName: String): Boolean {
    return modelName == JavaProject::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any? {
    if (!project.plugins.hasPlugin(JavaPlugin::class.java)) {
      return null
    }

    if (buildMapping.isEmpty() && isCompositeBuildSupported(project)) {
      buildMapping = computeBuildMapping(project.gradle)
    }

    val javaPlugin = project.convention.findPlugin(JavaPluginConvention::class.java)

    val sourceSets = ArrayList<SourceSet>()
    for (sourceSet in javaPlugin!!.sourceSets) {
      sourceSets.add(createSourceSets(project, sourceSet, buildMapping))
    }

    return JavaProjectImpl(
      project.name, sourceSets, javaPlugin.sourceCompatibility.toString())
  }

  companion object {

    private const val UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - "
    private const val LOCAL_JAR_DISPLAY_NAME = "local jar - "
    private const val CURRENT_BUILD_NAME = "__current_build__"

    private fun computeBuildMapping(gradle: Gradle): Map<String, String> {
      val buildMapping = HashMap<String, String>()

      // Get the root dir for current build.
      // This is necessary to handle the case when dependency comes from the same build with consumer,
      // i.e. when BuildIdentifier.isCurrentBuild returns true. In that case, BuildIdentifier.getName
      // returns ":" instead of the actual build name.
      val currentBuildPath = gradle.rootProject.projectDir.absolutePath
      buildMapping[CURRENT_BUILD_NAME] = currentBuildPath

      var rootGradleProject: Gradle? = gradle
      // first, ensure we are starting from the root Gradle object.

      while (rootGradleProject!!.parent != null) {
        rootGradleProject = rootGradleProject.parent
      }

      // get the root dir for the top project if different from current project.
      if (rootGradleProject !== gradle) {
        buildMapping[rootGradleProject.rootProject.name] = rootGradleProject.rootProject.projectDir.absolutePath
      }

      for (includedBuild in rootGradleProject.includedBuilds) {
        val includedBuildPath = includedBuild.projectDir.absolutePath
        // current build has been added with key CURRENT_BUIlD_NAME, avoid redundant entry.
        if (includedBuildPath != currentBuildPath) {
          buildMapping[includedBuild.name] = includedBuildPath
        }
      }

      return buildMapping
    }

    private fun createSourceSets(
      project: Project,
      sourceSet: org.gradle.api.tasks.SourceSet,
      buildMapping: Map<String, String>): SourceSet {
      val compileConfigurationName: String = if (isGradleAtLeast(project.gradle.gradleVersion, "2.12")) {
        sourceSet.compileClasspathConfigurationName
      }
      else {
        sourceSet.compileConfigurationName
      }
      val runtimeConfigurationName: String = if (isGradleAtLeast(project.gradle.gradleVersion, "3.4")) {
        sourceSet.runtimeClasspathConfigurationName
      }
      else {
        sourceSet.runtimeConfigurationName
      }
      return SourceSetImpl(
        sourceSet.name,
        sourceSet.allJava.srcDirs,
        sourceSet.resources.srcDirs,
        getClassesDirs(sourceSet, project),
        sourceSet.output.resourcesDir,
        getLibrariesForConfiguration(project, compileConfigurationName, buildMapping),
        getLibrariesForConfiguration(project, runtimeConfigurationName, buildMapping))
    }

    private fun getClassesDirs(sourceSet: org.gradle.api.tasks.SourceSet, project: Project): Collection<File> {
      // SourceSetOutput::getClassesDir was removed from Gradle 5.0.
      // SourceSetOutput::getClassesDirs was added since Gradle 4.0. For pre-4.0 Gradle, use Reflection to call getClassesDir.
      return if (isGradleAtLeast(project.gradle.gradleVersion, "4.0"))
        sourceSet.output.classesDirs.files
      else listOf(Class.forName("org.gradle.api.tasks.SourceSetOutput")
                    .getDeclaredMethod("getClassesDir")
                    .invoke(sourceSet.output) as File)
    }

    fun isGradleAtLeast(gradleVersion: String, expectedVersion: String): Boolean {
      val currentVersion = VersionNumber.parse(gradleVersion)
      val givenVersion = VersionNumber.parse(expectedVersion)
      return currentVersion >= givenVersion
    }

    private fun getLibrariesForConfiguration(
      project: Project, configurationName: String, buildMapping: Map<String, String>): Collection<JavaLibrary> {
      val configuration = project.configurations.getAt(configurationName)
      val javaLibraries = ArrayList<JavaLibrary>()

      // Since this plugin is always called from IDE, it should not break on unresolved dependencies.
      val lenientConfiguration = configuration.resolvedConfiguration.lenientConfiguration
      lenientConfiguration.getArtifacts(Specs.satisfyAll()).mapTo(javaLibraries) { artifact ->
        val projectPath = getProjectPath(project, artifact)
        val buildId = if (projectPath == null) null else getBuildId(project, artifact, buildMapping)
        val versionId = artifact.moduleVersion.id
        val libraryVersion = LibraryVersionImpl(versionId.group, versionId.name, versionId.version)
        JavaLibraryImpl(projectPath, buildId, artifact.name.intern(), artifact.file, libraryVersion)
      }

      // Add unresolved dependencies, mark by adding prefix UNRESOLVED_DEPENDENCY_PREFIX to name.
      // This follows idea plugin.
      lenientConfiguration.unresolvedModuleDependencies.mapTo(javaLibraries) { unresolvedDependency ->
        val selector = unresolvedDependency.selector
        val unresolvedName = UNRESOLVED_DEPENDENCY_PREFIX + selector.toString().replace(":".toRegex(), " ")
        val libraryVersion = LibraryVersionImpl(selector.group, selector.name, selector.version ?: "unknown")
        JavaLibraryImpl(null, null, unresolvedName.intern(), File(unresolvedName), libraryVersion)
      }

      // Collect jars from local directory
      for (dependency in configuration.allDependencies) {
        if (dependency is SelfResolvingDependency && dependency !is ProjectDependency) {
          for (file in dependency.resolve()) {
            val localJarName = LOCAL_JAR_DISPLAY_NAME + file.name
            javaLibraries.add(JavaLibraryImpl(null, null, localJarName.intern(), file, null))
          }
        }
      }
      return javaLibraries
    }

    /** Returns project path if artifact is a module dependency, returns null otherwise.  */
    private fun getProjectPath(project: Project, artifact: ResolvedArtifact): String? {
      if (isGradleAtLeast(project.gradle.gradleVersion, "2.6")) {
        val id = artifact.id.componentIdentifier
        if (id is ProjectComponentIdentifier) {
          return id.projectPath.intern()
        }
      }
      else {
        return project.rootProject.allprojects.firstOrNull { contains(it.buildDir, artifact.file) }?.path?.intern()
      }
      return null
    }

    /** Returns `true` if current Gradle supports composite build.  */
    private fun isCompositeBuildSupported(project: Project): Boolean {
      return isGradleAtLeast(project.gradle.gradleVersion, "3.1")
    }

    /**
     * Returns build id if artifact is a module dependency and Gradle is at least 3.1, returns null
     * otherwise.
     */
    private fun getBuildId(
      project: Project, artifact: ResolvedArtifact, buildMapping: Map<String, String>): String {
      if (isCompositeBuildSupported(project)) {
        val id = artifact.id.componentIdentifier
        if (id is ProjectComponentIdentifier) {
          val identifier = id.build
          return buildMapping[if (identifier.isCurrentBuild) CURRENT_BUILD_NAME else identifier.name]!!
        }
      }
      // Gradle doesn't support composite build, then build id must be current project root directory.
      return project.projectDir.absolutePath
    }

    /** Returns true if file is inside of directory or any of its sub-directories.  */
    private fun contains(directory: File, file: File): Boolean {
      try {
        var canonicalFile: File? = file.canonicalFile.parentFile
        val canonicalDirectory = directory.canonicalFile
        while (canonicalFile != null) {
          if (canonicalFile == canonicalDirectory) {
            return true
          }
          canonicalFile = canonicalFile.parentFile
        }
      }
      catch (ex: IOException) {
        return false
      }
      return false
    }
  }
}
