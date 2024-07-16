/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.attribution;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

public class BuildAttributionOutputLinkFilter implements Filter, DumbAware {

  public static final String LINK_TEXT = "Build Analyzer";
  public static final String INSIGHTS_AVAILABLE_LINE = "Build Analyzer results available";

  @Override
  public Result applyFilter(@NotNull String line, int entireLength) {
    int lineStart = entireLength - line.length();
    if (line.contains(INSIGHTS_AVAILABLE_LINE)) {
      int index = line.indexOf(LINK_TEXT);
      if (index != -1) {
        return new Result(Collections.singletonList(createLink(lineStart + index, lineStart + index + LINK_TEXT.length())));
      }
    }
    return null;
  }

  @NotNull
  private static ResultItem createLink(int start, int end) {
    return new ResultItem(start, end, getHyperLinkInfo());
  }

  @NotNull
  private static HyperlinkInfo getHyperLinkInfo() {
    return new HyperlinkInfo() {
      @Override
      public void navigate(@NotNull Project project) {
        project.getService(BuildAttributionManager.class).openResultsTab();
      }
    };
  }
}
