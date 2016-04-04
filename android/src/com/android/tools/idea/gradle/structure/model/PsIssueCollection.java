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

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyPath;
import com.android.tools.idea.gradle.structure.navigation.PsModulePath;
import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.util.containers.ConcurrentMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.intellij.xml.util.XmlStringUtil.escapeString;

public class PsIssueCollection {
  @NotNull private final ConcurrentMultiMap<PsNavigationPath, PsIssue> myIssues = new ConcurrentMultiMap<PsNavigationPath, PsIssue>();
  @NotNull private final PsContext myContext;

  public PsIssueCollection(@NotNull PsContext context) {
    myContext = context;
  }

  public void add(@NotNull PsIssue issue) {
    myIssues.putValue(issue.getPath(), issue);
    PsNavigationPath extraPath = issue.getExtraPath();
    if (extraPath != null) {
      myIssues.putValue(extraPath, issue);
    }
  }

  @NotNull
  public List<PsIssue> findIssues(@NotNull PsModel model, @Nullable Comparator<PsIssue> comparator) {
    PsNavigationPath path = null;
    if (model instanceof PsModule) {
      PsModule module = (PsModule)model;
      path = new PsModulePath(module);
    }
    if (model instanceof PsLibraryDependency) {
      PsLibraryDependency dependency = (PsLibraryDependency)model;
      path = new PsLibraryDependencyPath(myContext, dependency);
    }
    if (path != null) {
      return findIssues(path, comparator);
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<PsIssue> findIssues(@NotNull PsNavigationPath path, @Nullable Comparator<PsIssue> comparator) {
    List<PsIssue> issues = Lists.newArrayList(myIssues.get(path));
    if (comparator != null && issues.size() > 1) {
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

  @Nullable
  public static String getTooltipText(@NotNull List<PsIssue> issues) {
    if (issues.isEmpty()) {
      return null;
    }

    // Removed duplicated lines.
    Set<String> lines = Sets.newLinkedHashSet();
    for (PsIssue issue : issues) {
      lines.add(escapeString(issue.getText()));
    }

    StringBuilder buffer = new StringBuilder();
    buffer.append("<html><body>");
    int issueCount = lines.size();
    int problems = 0;

    int count = 0;
    for (String line : lines) {
      buffer.append(escapeString(line)).append("<br>");
      problems++;

      if (count++ > 9 && issueCount > 12) {
        buffer.append(issueCount - problems).append(" more problems...<br>");
        break;
      }
    }
    buffer.append("</body></html>");
    return buffer.toString();
  }
}
