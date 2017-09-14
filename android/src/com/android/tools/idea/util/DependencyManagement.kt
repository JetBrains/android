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
@file:JvmName("DependencyManagementUtil")

package com.android.tools.idea.util

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.projectsystem.DependencyManagementException
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.GoogleMavenArtifactVersion
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Adds the given artifact as a dependency available to the given source file. The version used depends on the artifact: For Android
 * support libraries, this method ensures that if the project uses any support library, then any newly added one will use the same
 * version as the existing ones. Otherwise, it uses the latest known version.
 * @param sourceContext The depender's source context. i.e. A java class, layout xml, etc.
 */
private fun addArtifactWithCorrectVersion(project: Project, sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): Boolean {
  val projectSystem = project.getProjectSystem()
  val version: GoogleMavenArtifactVersion?

  try {
    if (artifactId.isSupportLibrary) {
      version = GoogleMavenArtifactId.values()
          .asSequence()
          .map { id -> projectSystem.getVersionOfDependency(sourceContext, id) }
          .firstOrNull()
    }
    else {
      version = null
    }

    projectSystem.addDependency(sourceContext, artifactId, version)
  }
  catch (e: DependencyManagementException) {
    return false
  }
  return true
}

/**
 * Confirms with the user before adding the list of artifacts with given artifact ids as dependencies.
 * This method shows no confirmation dialog and performs a no-op if the list of artifacts is the empty list.
 * @return list of artifacts that were not successfully added. i.e. If the returned list is empty, then all were added successfully.
 * @see addArtifactWithCorrectVersion
 *
 */
fun Project.addDependencies(sourceContext: VirtualFile, artifactIds: List<GoogleMavenArtifactId>, promptUserBeforeAdding: Boolean): List<GoogleMavenArtifactId> {
  if (artifactIds.isEmpty() || (promptUserBeforeAdding && !userWantsToAdd(this, artifactIds))) {
    return artifactIds
  }

  val artifactsNotAdded = mutableListOf<GoogleMavenArtifactId>()
  for (id in artifactIds) {
    if (!addArtifactWithCorrectVersion(this, sourceContext, id)) {
      artifactsNotAdded.add(id)
    }
  }

  return artifactsNotAdded
}

/**
 * Returns true iff the dependency artifact is available to the source context.
 */
fun Project.hasDependency(sourceContext: VirtualFile, artifactId: GoogleMavenArtifactId): Boolean {
  return (getProjectSystem().getVersionOfDependency(sourceContext, artifactId) != null)
}

private fun userWantsToAdd(project: Project, artifactIds: List<GoogleMavenArtifactId>): Boolean {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return true
  }

  return Messages.OK == Messages.showOkCancelDialog(project, createAddDependencyMessage(artifactIds), "Add Project Dependency", Messages.getErrorIcon())
}

@VisibleForTesting
fun createAddDependencyMessage(artifactIds: List<GoogleMavenArtifactId>): String {
  val libraryNames = artifactIds.joinToString(", ") { it.artifactCoordinate }
  return "This operation requires the ${StringUtil.pluralize("library", artifactIds.size)} $libraryNames. \n\n" +
      "Would you like to add ${StringUtil.pluralize("this", artifactIds.size)} now?"
}