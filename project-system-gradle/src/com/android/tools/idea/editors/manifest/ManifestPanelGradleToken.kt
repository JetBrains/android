/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editors.manifest

import com.android.SdkConstants
import com.android.ide.common.blame.SourceFilePosition
import com.android.ide.common.blame.SourcePosition
import com.android.ide.common.repository.GradleCoordinate
import com.android.manifmerger.Actions
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason.Companion.PROJECT_MODIFIED
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import java.io.File

class ManifestPanelGradleToken : ManifestPanelToken<GradleProjectSystem>, GradleToken {
  /**
   * We don't have an exact position for values coming from the
   * Gradle model. This file is used as a marker pointing to the
   * Gradle model.
   */
  companion object {
    private val GRADLE_MODEL_MARKER_FILE = File(SdkConstants.FN_BUILD_GRADLE)
  }

  /**
   * Computes a library name intended for display purposes; names may not be unique
   * (and separator is always ":"). It will only show the artifact id, if that id contains slashes, otherwise
   * it will include the last component of the group id (unless identical to the artifact id).
   * <p>
   * E.g.
   * com.android.support.test.espresso:espresso-core:3.0.1@aar -> espresso-core:3.0.1
   * android.arch.lifecycle:extensions:1.0.0-beta1@aar -> lifecycle:extensions:1.0.0-beta1
   * com.google.guava:guava:11.0.2@jar -> guava:11.0.2
   */
  override fun getExternalAndroidLibraryDisplayName(library: ExternalAndroidLibrary): String {
    val artifactAddress = library.address
    val coordinates = GradleCoordinate.parseCoordinateString(artifactAddress)
    if (coordinates != null) {
      var name = coordinates.artifactId

      // For something like android.arch.lifecycle:runtime, instead of just showing "runtime",
      // we show "lifecycle:runtime"
      if (!name.contains("-")) {
        val groupId = coordinates.groupId
        val index = groupId.lastIndexOf('.') // okay if it doesn't exist
        val groupSuffix = groupId.substring(index + 1)
        if (groupSuffix != name) { // e.g. for com.google.guava:guava we'd end up with "guava:guava"
          name = "$groupSuffix:$name"
        }
      }
      val version = coordinates.lowerBoundVersion
      if (version != null && "unspecified" != version.toString()) {
        name += ":$version"
      }
      return name
    }
    return StringUtil.trimLeading(artifactAddress, ':')
  }

  override fun recordLocationReference(record: Actions.Record, files: MutableSet<File>): Boolean {
    // Injected values correspond to the Gradle model; we don't have
    // an accurate file location so just use a marker file.
    if (record.actionType == Actions.ActionType.INJECTED) {
      files.add(GRADLE_MODEL_MARKER_FILE)
      return true
    }
    return false
  }

  override fun handleReferencedFiles(
    referenced: Set<File>,
    sortedFiles: MutableList<ManifestFileWithMetadata>,
    sortedOtherFiles: MutableList<ManifestFileWithMetadata>,
    metadataForFileCreator: (SourceFilePosition) -> ManifestFileWithMetadata
  ) {
    // Build.gradle - injected
    if (referenced.contains(GRADLE_MODEL_MARKER_FILE)) {
      sortedFiles.add(metadataForFileCreator(SourceFilePosition(GRADLE_MODEL_MARKER_FILE, SourcePosition.UNKNOWN)))
    }
  }

  override fun getMetadataForRecord(
    record: Actions.Record,
    metadataForFileCreator: (SourceFilePosition) -> ManifestFileWithMetadata
  ) : ManifestFileWithMetadata? = when (record.actionType) {
    Actions.ActionType.INJECTED -> metadataForFileCreator(SourceFilePosition(GRADLE_MODEL_MARKER_FILE, SourcePosition.UNKNOWN))
    else -> null
  }

  override fun createMetadataForFile(file: File?, module: Module): ManifestFileWithMetadata? {
    if (file == null) return null
    if (file.absolutePath == GRADLE_MODEL_MARKER_FILE.absolutePath) {
      val gradleBuildFile = GradleProjectSystemUtil.getGradleBuildFile(module)
      return if (gradleBuildFile != null) {
        val ioFile = VfsUtilCore.virtualToIoFile(gradleBuildFile)
        InjectedBuildDotGradleFile(ioFile)
      }
      else {
        InjectedBuildDotGradleFile(null)
      }
    }
    return null
  }

  override fun generateMinSdkSettingRunnable(module: Module, minSdk: Int): Runnable? =
    Runnable {
      val linkAction = Runnable linkAction@{
        val project = module.project
        val pbm = ProjectBuildModel.get(project)
        val gbm: GradleBuildModel = pbm.getModuleBuildModel(module) ?: return@linkAction
        gbm.android().defaultConfig().minSdkVersion().setValue(minSdk)
        ApplicationManager.getApplication().invokeAndWait {
          WriteCommandAction
            .runWriteCommandAction(project, "Update build file minSdkVersion", null,
                                   { pbm.applyChanges() },
                                   gbm.psiFile)
        }
        // We must make sure that the files have been updated before we sync, we block above but not here.
        val syncRunnable = Runnable { project.getSyncManager().syncProject(PROJECT_MODIFIED) }
        if (ApplicationManager.getApplication().isUnitTestMode) {
          syncRunnable.run()
        }
        else {
          ApplicationManager.getApplication().invokeLater(syncRunnable)
        }
      }
      if (ApplicationManager.getApplication().isUnitTestMode) {
        linkAction.run()
      }
      else {
        ApplicationManager.getApplication().executeOnPooledThread(linkAction)
      }
    }
}

data class InjectedBuildDotGradleFile(override val file: File?) : InjectedFile() {
  override val isProjectFile = true;
}