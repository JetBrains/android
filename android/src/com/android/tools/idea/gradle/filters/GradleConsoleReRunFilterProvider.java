/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.filters;

import com.android.tools.idea.gradle.util.GradleProjects;
import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;

import java.util.ArrayList;
import java.util.List;

public class GradleConsoleReRunFilterProvider implements ConsoleFilterProvider {

  // For a failed build with no options, you may see:
  //   "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output."
  // With --debug turned on, you may see:
  //   "<timestamp> [ERROR] [org.gradle.BuildExceptionReporter] Run with --stacktrace option to get the stack trace."
  // With --info turned on, you may see:
  //   "Run with --stacktrace option to get the stack trace. Run with --debug option to get more log output."
  // With --stacktrace turned on, you may see:
  //   "Run with --info or --debug option to get more log output."

  @NotNull
  @Override
  public Filter[] getDefaultFilters(@NotNull Project project) {
    if (!GradleProjects.isBuildWithGradle(project)) {
      return Filter.EMPTY_ARRAY;
    }
    return new Filter[]{ new MyReRunBuildFilter() };
  }

  private static class MyReRunBuildFilter implements Filter {
    private String line;
    private List<ResultItem> links;
    private int lineStart;

    @Override
    public Result applyFilter(String line, int entireLength) {
      if (line == null) {
        return null;
      }
      this.line = line;
      this.lineStart = entireLength - line.length();
      this.links = new ArrayList<>();
      String trimLine = line.trim();
      if (!(trimLine.contains("Run with --")
          && (trimLine.endsWith("option to get the stack trace.")
              || trimLine.endsWith("option to get more log output.")))) {
        return null;
      }
      addLinkIfMatch("Run with --stacktrace", "--stacktrace");
      addLinkIfMatch("Run with --info", "--info");
      addLinkIfMatch("Run with --debug option", "--debug");
      addLinkIfMatch("--debug option", "--debug");
      if (links.isEmpty()) {
        return null;
      }
      return new Result(links);
    }

    private void addLinkIfMatch(@NotNull String text, @NotNull String option) {
      int index = line.indexOf(text);
      if (index != -1) {
        links.add(createLink(lineStart + index, lineStart + index + text.length(), option));
      }
    }

    private @NotNull ResultItem createLink(int start, int end, @NotNull String option) {
      List<String> options = new ArrayList<>();
      options.add(option);
      return new ResultItem(start, end,
          (project) -> GradleBuildInvoker.getInstance(project).rebuildWithTempOptions(options));
    }
  }
}
