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
@file:JvmName("BuildSystemServiceUtil")

package com.android.tools.idea.project

import com.android.tools.idea.npw.project.AndroidSourceSet
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * Handles generic build system operations such as syncing and building. Implementations of this interface will
 * receive a {@link Project} instance in their constructor.
 */
interface BuildSystemService {

  /**
   * Returns true iff this object is applicable to the {@link Project} it was created on.
   * This method is called immediately after construction. If it returns false, the instance
   * is discarded and no further methods are invoked.
   */
  fun isApplicable(): Boolean

  /** The result of a sync request */
  enum class SyncResult(val successful: Boolean) {
    /** The user cancelled the sync */
    CANCELLED(false),
    /** Sync failed */
    FAILURE(false),
    /** The user has compilation errors or errors in build system files */
    PARTIAL_SUCCESS(true),
    /** The project state was loaded from a cache instead of performing an actual sync */
    SKIPPED(true),
    /** Sync succeeded */
    SUCCESS(true);
  }

  /** The requestor's reason for syncing the project */
  enum class SyncReason {
    /** The project is being loaded */
    PROJECT_LOADED,
    /** The project has been modified */
    PROJECT_MODIFIED,
    /** The user requested the sync directly (by pushing the button) */
    USER_REQUEST;
  }

  /**
   * Triggers synchronizing the IDE model with the build system model of the project. Source generation
   * may be triggered regardless of the value of {@code requireSourceGeneration}, though implementing classes may
   * use this flag for optimization.
   *
   * @param reason the caller's reason for requesting a sync
   * @param requireSourceGeneration a hint to the underlying project system to optionally generate sources after a successful sync
   *
   * @return the future result of the sync request
   */
  fun syncProject(reason: SyncReason, requireSourceGeneration: Boolean = true): ListenableFuture<SyncResult>

  /**
   * Adds a dependency to the given module.
   *
   * TODO: Figure out and document the format for the dependency strings
   */
  fun addDependency(module: Module, dependency: String)

  /**
   * Merge new dependencies into a (potentially existing) build file. Build files are build-system-specific
   * text files describing the steps for building a single android application or library.
   *
   * TODO: The association between a single android library and a single build file is too gradle-specific.
   * TODO: Document the exact format for the supportLibVersionFilter string
   * TODO: Document the format for the dependencies string
   *
   * @param dependencies new dependencies.
   * @param destinationContents original content of the build file.
   * @param supportLibVersionFilter If a support library filter is provided, the support libraries will be
   * limited to match that filter. This is typically set to the compileSdkVersion, such that you don't end
   * up mixing and matching compileSdkVersions and support libraries from different versions, which is not
   * supported.
   *
   * @return new content of the build file
   */
  fun mergeBuildFiles(dependencies: String,
                      destinationContents: String,
                      supportLibVersionFilter: String?): String

  /**
   * TODO: Provide additional context for the purpose of this method
   *
   * @param facet from which we receive {@link SourceProvider}s.
   * @param targetDirectory to filter the relevant {@link SourceProvider}s from the {@code androidFacet}.
   * @return a list of {@link AndroidSourceSet}s created from each of {@code androidFacet}'s {@link SourceProvider}s.
   * In cases where the source provider returns multiple paths, we always take the first match.
   */
  fun getSourceSets(facet: AndroidFacet, targetDirectory: VirtualFile?): List<AndroidSourceSet>
}

val EP_NAME = ExtensionPointName<BuildSystemService>("com.android.project.buildSystemService")

fun getInstance(project: Project): BuildSystemService? {
  return EP_NAME.getExtensions(project).find { it.isApplicable() }
}