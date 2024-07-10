// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.fast

import com.intellij.openapi.application.PathManager
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.jps.model.JpsProject
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

abstract class AndroidPluginProjectConsistencyTestCase {
  /**
   * IJ Ultimate project home directory.
   */
  protected val ultimateHomePath: Path = Paths.get(PathManager.getHomePath())

  /**
   * IJ Community project home directory.
   */
  protected val communityHomePath: Path = Paths.get(PlatformTestUtil.getCommunityPath())

  /**
   * Android project home directory.
   */
  protected val androidHomePath: Path = communityHomePath.resolve("android")

  /**
   * Project instance of the `IJ Community` project.
   */
  protected val communityProject: JpsProject
    get() = loadIntelliJProject(communityHomePath)

  /**
   * Project instance of the `IJ Ultimate` project.
   */
  protected val ultimateProject: JpsProject
    get() = loadIntelliJProject(ultimateHomePath)

  /**
   * Project instance of the `Android plugin` project.
   */
  protected val androidProject: JpsProject
    get() = loadIntelliJProject(androidHomePath)

  private fun loadIntelliJProject(projectHome: Path): JpsProject {
    return IntelliJProjectConfiguration.loadIntelliJProject(projectHome.absolutePathString())
  }
}