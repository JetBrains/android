/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.formatter;

import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import java.util.Collection;
import javax.annotation.Nullable;

/** Used to delegate formatting to an external tool. */
public interface CustomFormatter {

  ExtensionPointName<CustomFormatter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.CustomFormatter");

  boolean appliesToFile(Project project, PsiFile file);

  /** Displayed to the user in a progress dialog while waiting for the formatting results. */
  String progressMessage();

  /**
   * Given the input file and a list of line ranges, returns the formatting replacements, or null if
   * the formatting failed.
   *
   * <p>Will be called in a background thread, under a progress dialog.
   */
  @Nullable
  Replacements getReplacements(
      Project project, FileContentsProvider fileContents, Collection<TextRange> ranges);

  /** If true, then when formatting VCS changed text will actually format the entire file. */
  default boolean alwaysFormatEntireFile() {
    return false;
  }
}
