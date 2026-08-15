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
package com.google.idea.blaze.android.run

import com.android.sdklib.devices.Abi
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.ndk.run.NativeDebuggerAppContext
import com.android.tools.ndk.run.NativeDebuggerAppContextProvider
import com.android.tools.ndk.run.SymbolDir
import com.google.idea.blaze.android.projectsystem.BazelToken
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File

/**
 * An implementation of [NativeDebuggerAppContextProvider] for the Blaze project system.
 *
 * Translates [BazelApplicationProjectContext] into [NativeDebuggerAppContext] to provide symbol directories and source directory mappings
 * (/proc/self/cwd -> workspace root) to LLDB.
 */
class BazelNativeDebuggerAppContextProvider : NativeDebuggerAppContextProvider, BazelToken {

  override fun getNativeDebuggerAppContext(applicationProjectContext: ApplicationProjectContext): NativeDebuggerAppContext? {
    val context = applicationProjectContext as? BazelApplicationProjectContext ?: return null
    return object : NativeDebuggerAppContext {
      override fun getProject(): Project = context.project

      override fun getApplicationId(): String = context.applicationId

      override fun getSymDirs(abis: List<Abi>): Collection<SymbolDir> = context.symbolDirs

      override fun getSourceMap(): Map<String, String> {
        val workingDirPath = WorkspaceRoot.fromProject(context.project).directory().path
        return mapOf("/proc/self/cwd" to workingDirPath)
      }

      override fun getExplicitModuleSymbolMap(abi: Abi): Map<File, File> = emptyMap()

      override fun getModulesToVerify(): Collection<Module> = emptyList()
    }
  }
}
