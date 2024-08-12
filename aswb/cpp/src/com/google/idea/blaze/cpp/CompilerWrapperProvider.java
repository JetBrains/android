/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import javax.annotation.Nullable;

/** Wraps the provided compiler in a script that accepts Clion parameter files. */
public interface CompilerWrapperProvider {
  static CompilerWrapperProvider getInstance() {
    return ApplicationManager.getApplication().getService(CompilerWrapperProvider.class);
  }

  /**
   * Create a wrapper script that transforms the CLion compiler invocation into a safe invocation of
   * the compiler script that blaze uses.
   *
   * <p>CLion passes arguments to the compiler in an arguments file. The c toolchain compiler
   * wrapper script doesn't handle arguments files, so we need to move the compiler arguments from
   * the file to the command line.
   *
   * <p>The first argument provided to this script is the argument file. The second argument is the
   * output file.
   *
   * @param executionRoot the execution root for running the compiler
   * @param blazeCompilerExecutableFile the compiler
   * @return The wrapper script that CLion can call.
   */
  @Nullable
  File createCompilerExecutableWrapper(File executionRoot, File blazeCompilerExecutableFile);
}
