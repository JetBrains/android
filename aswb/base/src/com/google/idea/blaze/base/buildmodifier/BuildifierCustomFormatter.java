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
package com.google.idea.blaze.base.buildmodifier;

import static com.google.idea.blaze.base.buildmodifier.BuildifierFormattingService.useNewBuildifierFormattingService;

import com.google.idea.blaze.base.formatter.CustomFormatter;
import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import javax.annotation.Nullable;

/** Uses buildifier to format BUILD/bzl files. */
final class BuildifierCustomFormatter implements CustomFormatter {

  @Override
  public boolean appliesToFile(Project project, PsiFile file) {
    return !useNewBuildifierFormattingService.isEnabled() && file instanceof BuildFile;
  }

  @Nullable
  @Override
  public Replacements getReplacements(
      Project project, FileContentsProvider fileContents, Collection<TextRange> ranges) {
    if (!(fileContents.file instanceof BuildFile)) {
      return null;
    }
    BlazeFileType type = ((BuildFile) fileContents.file).getBlazeFileType();
    return BuildFileFormatter.getReplacements(type, fileContents, ranges);
  }

  @Override
  public String progressMessage() {
    return "Running buildifier";
  }
}
