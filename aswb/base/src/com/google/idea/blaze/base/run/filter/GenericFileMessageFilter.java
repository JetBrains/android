/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.filter;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Adds hyperlinks to generic console output of the form 'path:line:column: ...' */
class GenericFileMessageFilter implements Filter {

  private static final Pattern FILE_LINE_COLUMN =
      Pattern.compile("^([^:\\s]+):([0-9]+):([0-9]+): ");
  private final Project project;

  GenericFileMessageFilter(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcher = FILE_LINE_COLUMN.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    String filePath = matcher.group(1);
    if (filePath == null) {
      return null;
    }
    VirtualFile file = FileResolver.resolveToVirtualFile(project, filePath);
    if (file == null) {
      return null;
    }
    int lineNumber = parseNumber(matcher.group(2));
    int columnNumber = parseNumber(matcher.group(3));
    OpenFileHyperlinkInfo hyperlink =
        new CustomOpenFileHyperlinkInfo(project, file, lineNumber - 1, columnNumber - 1);

    int startIx = matcher.start(1);
    int endIx = matcher.end(3);
    int offset = entireLength - line.length();
    return new Result(startIx + offset, endIx + offset, hyperlink);
  }

  /** defaults to -1 if no number can be parsed. */
  private static int parseNumber(@Nullable String string) {
    try {
      return string != null ? Integer.parseInt(string) : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** Provider for traceback filter */
  static class Provider implements ConsoleFilterProvider {
    @Override
    public Filter[] getDefaultFilters(Project project) {
      return Blaze.isBlazeProject(project)
          ? new Filter[] {new GenericFileMessageFilter(project)}
          : new Filter[0];
    }
  }

  /**
   * A trivial wrapper class to allow interrogating file, line, column results in unit tests
   * (without setting up all the project, application services transitively required by
   * OpenFileHyperlinkInfo).
   */
  @VisibleForTesting
  static class CustomOpenFileHyperlinkInfo extends OpenFileHyperlinkInfo {

    final VirtualFile vf;
    final int line;
    final int column;

    private CustomOpenFileHyperlinkInfo(Project project, VirtualFile vf, int line, int column) {
      super(project, vf, line, column);
      this.vf = vf;
      this.line = line;
      this.column = column;
    }
  }
}
