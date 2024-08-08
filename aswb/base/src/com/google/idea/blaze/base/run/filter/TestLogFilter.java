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

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Hyperlinks test logs in the streamed output. */
class TestLogFilter implements Filter {

  private static final Pattern OLD_REGEX =
      Pattern.compile("^\\s*(/[^:\\s]+/testlogs/[^:\\s]+/test\\.log)\\s*$");

  private static final Pattern NEW_REGEX =
      Pattern.compile(".*\\(see (/[^:\\s]+/testlogs/[^:\\s]+/test\\.log)\\)\\s*");

  private final Project project;

  TestLogFilter(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public Result applyFilter(String line, int entireLength) {
    Matcher matcher = OLD_REGEX.matcher(line);
    if (!matcher.matches()) {
      matcher = NEW_REGEX.matcher(line);
    }
    if (!matcher.matches()) {
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
    int offset = entireLength - line.length();
    return new Result(
        matcher.start(1) + offset,
        matcher.end(1) + offset,
        new OpenFileHyperlinkInfo(project, file, /* line= */ 0));
  }

  /** Provider for traceback filter */
  static class Provider implements ConsoleFilterProvider {
    @Override
    public Filter[] getDefaultFilters(Project project) {
      return Blaze.isBlazeProject(project)
          ? new Filter[] {new TestLogFilter(project)}
          : new Filter[0];
    }
  }
}
