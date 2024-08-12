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

import com.google.idea.blaze.cpp.CompilerVersionChecker.VersionCheckException.IssueKind;
import java.io.File;

/** {@link CompilerVersionChecker} for tests. */
public class MockCompilerVersionChecker implements CompilerVersionChecker {

  private String compilerVersion;
  private boolean injectFault;

  public MockCompilerVersionChecker(String compilerVersion) {
    this.compilerVersion = compilerVersion;
  }

  @Override
  public String checkCompilerVersion(File executionRoot, File cppExecutable)
      throws VersionCheckException {
    if (injectFault) {
      throw new VersionCheckException(IssueKind.GENERIC_FAILURE, "injected fault");
    }
    return compilerVersion;
  }

  public void setCompilerVersion(String compilerVersion) {
    this.compilerVersion = compilerVersion;
  }

  public void setInjectFault(boolean injectFault) {
    this.injectFault = injectFault;
  }
}
