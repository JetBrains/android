/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.rendering.BuildTargetReference
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.AlreadyDisposedException

internal class BazelBuildTargetReference internal constructor(module: Module, val file: VirtualFile) : BuildTargetReference {
  fun getFileWorkspaceRelativePath() = WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, listOf(file)).single()

  override val module = module
    get() = if (field.isDisposed) throw AlreadyDisposedException("Already disposed: $field") else field

  override val moduleIfNotDisposed = if (module.isDisposed) null else module
}
