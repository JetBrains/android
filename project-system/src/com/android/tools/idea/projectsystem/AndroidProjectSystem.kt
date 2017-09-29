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
@file:JvmName("ProjectSystemUtil")

package com.android.tools.idea.projectsystem

import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import java.nio.file.Path

/**
 * Provides a build-system-agnostic interface to the build system. Instances of this interface
 * only apply to a specific [Project].
 */
interface AndroidProjectSystem {
  /**
   * Uses build-system-specific heuristics to locate the APK file produced by the given project, or null if none. The heuristics try
   * to determine the most likely APK file corresponding to the application the user is working on in the project's current configuration.
   */
  fun getDefaultApkFile(): VirtualFile?

  /**
   * Returns the absolute filesystem path to the aapt executable being used for the given project.
   */
  fun getPathToAapt(): Path

  /**
   * Initiates an incremental build of the entire project. Blocks the caller until the build
   * is completed.
   *
   * TODO: Make this asynchronous and return something like a ListenableFuture.
   */
  fun buildProject()

  /**
   * Returns true if the project allows adding new modules.
   */
  fun allowsFileCreation(): Boolean

  /**
   * Returns an interface for interacting with the given module.
   */
  fun getModuleSystem(module: Module): AndroidModuleSystem

  /**
   * Attempts to upgrade the project to support instant run. If the project already supported
   * instant run, this will report failure without modifying the project.
   * <p>
   * Returns true iff the upgrade was successful. Callers must sync the
   * project by calling [syncProject] after calling this method if it returns true.
   */
  fun upgradeProjectToSupportInstantRun(): Boolean

  /** The result of a sync request */
  enum class SyncResult(val isSuccessful: Boolean) {
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

  /** Listener which provides callbacks for the beginning and the end of syncs for an associated project */
  interface SyncResultListener {
    fun syncEnded(result: SyncResult)
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
}

/** Endpoint for broadcasting changes in global sync status */
@JvmField val PROJECT_SYSTEM_SYNC_TOPIC = Topic<AndroidProjectSystem.SyncResultListener>("Project sync",
    AndroidProjectSystem.SyncResultListener::class.java)

/**
 * Returns the instance of {@link AndroidProjectSystem} that applies to the given {@link Project}.
 */
fun Project.getProjectSystem(): AndroidProjectSystem {
  return getComponent(ProjectSystemComponent::class.java).projectSystem
}

/**
 * Returns the instance of {@link AndroidModuleSystem} that applies to the given {@link Module}.
 */
fun Module.getModuleSystem(): AndroidModuleSystem {
  return project.getProjectSystem().getModuleSystem(this)
}