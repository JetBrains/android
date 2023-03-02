package com.android.ide.gradle.model.composites

import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.util.GradleVersion
import java.io.File

class BuildMapModelBuilder : ToolingModelBuilder {
  override fun canBuild(modelName: String): Boolean {
    return modelName == BuildMap::class.java.name
  }

  override fun buildAll(modelName: String, project: Project): Any {
    return when (modelName) {
      BuildMap::class.java.name -> buildBuildMap(project)
      else -> error("Does not support model $modelName")
    }
  }

  private fun buildBuildMap(project: Project): BuildMap = BuildMapImpl(getBuildMap(project))

  private fun getBuildMap(project: Project): Map<String, File> {
    if (GradleVersion.version(project.gradle.gradleVersion) < GradleVersion.version("3.1")) return emptyMap()
    return project.gradle.includedBuilds.associate { it.name to it.projectDir }
  }
}
