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

import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import com.google.common.collect.Lists;
import com.intellij.util.containers.ConcurrentMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.intellij.xml.util.XmlStringUtil.escapeString;

public class PsIssueCollection {
  @NotNull private final ConcurrentMultiMap<PsNavigationPath, PsIssue> myIssues = new ConcurrentMultiMap<PsNavigationPath, PsIssue>();

  public void add(@NotNull PsIssue issue) {
    myIssues.putValue(issue.getPath(), issue);
  }

  @NotNull
  public List<PsIssue> findIssues(@NotNull PsNavigationPath path, @Nullable Comparator<PsIssue> comparator) {
    List<PsIssue> issues = Lists.newArrayList(myIssues.get(path));
    if (comparator != null) {
      Collections.sort(issues, comparator);
    }
    return issues;
  }

  @NotNull
  public List<PsIssue> getIssues() {
    return Lists.newArrayList(myIssues.values());
  }

  public boolean isEmpty() {
    return myIssues.isEmpty();
  }

  @NotNull
  public static String getTooltipText(@NotNull List<PsIssue> issues) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html><body>");
    int issueCount = issues.size();
    int problems = 0;
    for (int i = 0; i < issueCount; i++) {
      PsIssue issue = issues.get(i);
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
