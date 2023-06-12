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
package com.android.tools.idea.editors.fast

import com.android.tools.idea.editors.build.ProjectBuildStatusManagerForTests
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.res.ResourceNotificationManager
import com.google.common.collect.ImmutableSet
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Returns a set with all the filenames contained in the path.
 */
fun Path.toFileNameSet(): Set<String> {
  val generatedFilesSet = mutableSetOf<String>()
  @Suppress("BlockingMethodInNonBlockingContext")
  Files.walkFileTree(this, object : SimpleFileVisitor<Path>() {
    override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
      file?.let { generatedFilesSet.add(it.fileName.toString()) }
      @Suppress("BlockingMethodInNonBlockingContext")
      return super.visitFile(file, attrs)
    }
  })
  return generatedFilesSet
}

internal fun ProjectBuildStatusManagerForTests.simulateProjectSystemBuild(buildMode: ProjectSystemBuildManager.BuildMode = ProjectSystemBuildManager.BuildMode.COMPILE,
                                                                         buildStatus: ProjectSystemBuildManager.BuildStatus) {
  getBuildListenerForTest().buildStarted(buildMode)
  getBuildListenerForTest().buildCompleted(
    ProjectSystemBuildManager.BuildResult(buildMode, buildStatus, 1L))
}


internal fun ProjectBuildStatusManagerForTests.simulateResourcesChange() {
  getResourcesListenerForTest().resourcesChanged(ImmutableSet.of(ResourceNotificationManager.Reason.RESOURCE_EDIT))
}