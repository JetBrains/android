package com.android.ide.gradle.model.composites

import org.gradle.api.Project
import org.gradle.api.invocation.Gradle
import org.gradle.tooling.provider.model.ToolingModelBuilder
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
    var rootGradle = project.gradle
    while (rootGradle.parent != null) {
      rootGradle = rootGradle.parent!!
    }

    return mutableMapOf<String, File>().also { map ->
      map[":"] = rootGradle.rootProject.projectDir
      getBuildMap(rootGradle, map)
    }
  }

  private fun getBuildMap(gradle: Gradle, map: MutableMap<String, File>) {
    for (build in gradle.includedBuilds) {
      map[build.name] = build.projectDir
    }
  }
}