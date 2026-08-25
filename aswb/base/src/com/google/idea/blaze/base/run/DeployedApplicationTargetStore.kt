/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run

import com.google.idea.blaze.common.Label
import com.intellij.openapi.project.Project

/** Service for mapping deployed application IDs to their Bazel target labels. */
interface DeployedApplicationTargetStore {
  /** Associates the given [applicationId] with the deployed [target]. */
  fun trackTargetForApplication(applicationId: String, target: Label)

  /** Returns the target label associated with the given [applicationId], or null if none is tracked. */
  fun getTargetForApplication(applicationId: String): Label?

  /** Returns all tracked application IDs for the project. */
  fun getAllApplicationIds(): Set<String>

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DeployedApplicationTargetStore = project.getService(DeployedApplicationTargetStore::class.java)
  }
}
