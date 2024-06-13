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

import com.android.tools.compile.fast.CompilationResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import java.nio.file.Path

/**
 * Interface to implement by specific implementations that can talk to compiler daemons.
 */
interface CompilerDaemonClient : Disposable {
  /**
   * Returns if this daemon is running. If not, no compileRequests will be handled.
   */
  val isRunning: Boolean

  /**
   * Sends the given compilation requests and returns a [CompilationResult] indicating the result.
   *
   * @param files Set of files to be compiled on this request.
   * @param module [Module] to use as classpath for this compilation requests.
   * @param outputDirectory [Path] existing path to store the result of the compilation.
   * @param indicator [ProgressIndicator] that the request can use to inform the user about the progress of the request.
   */
  suspend fun compileRequest(files: Collection<PsiFile>, module: Module, outputDirectory: Path, indicator: ProgressIndicator): CompilationResult
}