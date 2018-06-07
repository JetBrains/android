/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsPath;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class DependencyViewIssueRenderer implements IssueRenderer {
  private final boolean myRenderPath;
  private final boolean myRenderDescription;
  @NotNull private PsContext myContext;

  public DependencyViewIssueRenderer(@NotNull PsContext context,
                                     boolean renderPath,
                                     boolean renderDescription) {
    myContext = context;
    myRenderPath = renderPath;
    myRenderDescription = renderDescription;
  }

  @Override
  public void renderIssue(StringBuilder buffer, PsIssue issue) {
    if (myRenderPath) {
      buffer.append(issue.getPath().getHtml(myContext));
      buffer.append(": ");
    }
    buffer.append(issue.getText());
    PsPath quickFixPath = issue.getQuickFixPath();
    if (quickFixPath != null) {
      buffer.append(" ").append(quickFixPath.getHtml(myContext));
    }
    if (myRenderDescription) {
      String description = issue.getDescription();
      if (StringUtil.isNotEmpty(description)) {
        buffer.append("<br/><br/>").append(description);
      }
    }
  }
}