/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.UnknownCompilerKind;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

final class BlazeCompilerSettings {

  @Nullable private final File cCompiler;
  @Nullable private final File cppCompiler;
  private final ImmutableList<String> cCompilerSwitches;
  private final ImmutableList<String> cppCompilerSwitches;
  private final String compilerVersion;

  BlazeCompilerSettings(
      Project project,
      @Nullable File cCompiler,
      @Nullable File cppCompiler,
      ImmutableList<String> cFlags,
      ImmutableList<String> cppFlags,
      String compilerVersion) {
    this.cCompiler = cCompiler;
    this.cppCompiler = cppCompiler;
    this.cCompilerSwitches = ImmutableList.copyOf(getCompilerSwitches(project, cFlags));
    this.cppCompilerSwitches = ImmutableList.copyOf(getCompilerSwitches(project, cppFlags));
    this.compilerVersion = compilerVersion;
  }

  OCCompilerKind getCompiler(OCLanguageKind languageKind) {
    if (languageKind == CLanguageKind.C || languageKind == CLanguageKind.CPP) {
      return ClangCompilerKind.INSTANCE;
    }
    return UnknownCompilerKind.INSTANCE;
  }

  File getCompilerExecutable(OCLanguageKind lang) {
    if (lang == CLanguageKind.C) {
      return cCompiler;
    } else if (lang == CLanguageKind.CPP) {
      return cppCompiler;
    }
    // We don't support objective c/c++.
    return null;
  }

  ImmutableList<String> getCompilerSwitches(OCLanguageKind lang, @Nullable VirtualFile sourceFile) {
    if (lang == CLanguageKind.C) {
      return cCompilerSwitches;
    }
    if (lang == CLanguageKind.CPP) {
      return cppCompilerSwitches;
    }
    return ImmutableList.of();
  }

  private static List<String> getCompilerSwitches(Project project, List<String> allCompilerFlags) {
    return BlazeCompilerFlagsProcessor.process(project, allCompilerFlags);
  }

  String getCompilerVersion() {
    return compilerVersion;
  }
}
