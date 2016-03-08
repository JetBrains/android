/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.xml.util.XmlStringUtil.escapeString;

public class PsdIssues {
  @NotNull private final List<PsdIssue> myIssues = Lists.newArrayList();

  public void addIssue(@NotNull PsdIssue issue) {
    myIssues.add(issue);
  }

  @NotNull
  public List<PsdIssue> getIssues() {
    return ImmutableList.copyOf(myIssues);
  }

  public boolean isEmpty() {
    return myIssues.isEmpty();
  }

  @NotNull
  public String getTooltipText() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html><body>");
    int issueCount = myIssues.size();
    int problems = 0;
    for (int i = 0; i < issueCount; i++) {
      PsdIssue issue = myIssues.get(i);
      buffer.append(escapeString(issue.getText())).append("<br>");
      problems++;

      if (i > 9 && issueCount > 12) {
        buffer.append(issueCount - problems).append(" more problems...<br>");
        break;
      }
    }
    buffer.append("</body></html>");
    return buffer.toString();
  }
}
