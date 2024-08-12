/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

/** Runs a compiler to check its version. */
public interface CompilerVersionChecker {

  static CompilerVersionChecker getInstance() {
    return ApplicationManager.getApplication().getService(CompilerVersionChecker.class);
  }

  /** Indicates failure to check compiler version */
  public static class VersionCheckException extends Exception {

    final IssueKind kind;

    /** Describes the failure mode of the version check. */
    public enum IssueKind {
      MISSING_EXEC_ROOT,
      MISSING_COMPILER,
      GENERIC_FAILURE
    }

    public VersionCheckException(IssueKind kind, String message) {
      super(message);
      this.kind = kind;
    }
  }

  /** Returns the compiler's version string */
  String checkCompilerVersion(File executionRoot, File cppExecutable) throws VersionCheckException;
}
