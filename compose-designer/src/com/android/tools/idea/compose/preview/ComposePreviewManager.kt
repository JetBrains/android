/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

/**
 * Interface that provides access to the Compose Preview logic.
 */
interface ComposePreviewManager {
  /**
   * Status of the preview.
   *
   * @param hasRuntimeErrors true if the project has any runtime errors that prevent the preview being up to date.
   *  For example missing classes.
   * @param hasSyntaxErrors true if the preview is displaying content of a file that has syntax errors.
   * @param isOutOfDate true if the preview needs a refresh to be up to date.
   * @param isRefreshing true if the view is currently refreshing.
   */
  data class Status(val hasRuntimeErrors: Boolean, val hasSyntaxErrors: Boolean, val isOutOfDate: Boolean, val isRefreshing: Boolean) {
    /**
     * True if the preview has errors that will need a refresh
     */
    val hasErrors = hasRuntimeErrors || hasSyntaxErrors
  }

  fun status(): Status

  /**
   * Requests a refresh of the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  fun refresh()

  /**
   * When true, a build will automatically be triggered when the user makes a source code change.
   */
  var isAutoBuildEnabled: Boolean

  /**
   * List of available groups in this preview. The editor can contain multiple groups and only will be displayed at a given time.
   */
  val availableGroups: Collection<String>

  /**
   * Group name from [availableGroups] currently selected or null if we do not want to do group filtering.
   */
  var groupNameFilter: String?

  /**
   * Enables (and indicates) the interactive mode of the preview.
   */
  var isInteractive: Boolean
}