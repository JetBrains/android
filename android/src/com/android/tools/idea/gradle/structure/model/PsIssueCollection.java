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
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeAndTextComparator;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidLibraryDependency;
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyPath;
import com.android.tools.idea.gradle.structure.navigation.PsModulePath;
import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.util.containers.ConcurrentMultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.stream.Collectors;

public class PsIssueCollection {
  @NotNull private final ConcurrentMultiMap<PsNavigationPath, PsIssue> myIssues = new ConcurrentMultiMap<>();
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

    if (model instanceof PsAndroidLibraryDependency) {
      PsAndroidLibraryDependency dependency = (PsAndroidLibraryDependency)model;
      path = new PsLibraryDependencyPath(myContext, dependency);
    }

    if (path != null) {
      return findIssues(path, comparator);
    }

    return Collections.emptyList();
  }

  @NotNull
  public List<PsIssue> findIssues(@NotNull PsNavigationPath path, @Nullable Comparator<PsIssue> comparator) {
    Set<PsIssue> issues = Sets.newHashSet(myIssues.get(path));
    List<PsIssue> issueList = issues.stream().collect(Collectors.toList());
    if (comparator != null && issueList.size() > 1) {
      Collections.sort(issueList, comparator);
    }
    return issueList;
  }

  @NotNull
  public List<PsIssue> getValues() {
    Set<PsIssue> issues = Sets.newHashSet(myIssues.values());
    return issues.stream().collect(Collectors.toList());
  }

  @NotNull
  public List<PsIssue> getValues(@NotNull Class<? extends PsNavigationPath> pathType) {
    Set<PsIssue> issues = Sets.newHashSet();
    myIssues.keySet().stream().filter(pathType::isInstance).forEachOrdered(path -> issues.addAll(myIssues.get(path)));
    return issues.stream().collect(Collectors.toList());
  }

  public boolean isEmpty() {
    return myIssues.isEmpty();
  }

  @Nullable
  public static String getTooltipText(@NotNull List<PsIssue> issues) {
    if (issues.isEmpty()) {
      return null;
    }

    List<PsIssue> sorted = Lists.newArrayList(issues);
    Collections.sort(sorted, IssuesByTypeAndTextComparator.INSTANCE);

    // Removed duplicated lines.
    Set<String> lines = Sets.newLinkedHashSet();
    for (PsIssue issue : sorted) {
      String line = issue.getText();
      String path = issue.getPath().getPlainText();
      if (!path.isEmpty()) {
        line = path + ": " + line;
      }
      lines.add(line);
    }

    StringBuilder buffer = new StringBuilder();
    buffer.append("<html><body>");
    int issueCount = lines.size();
    int problems = 0;

    int count = 0;
    for (String line : lines) {
      buffer.append(line).append("<br>");
      problems++;

      if (count++ > 9 && issueCount > 12) {
        buffer.append(issueCount - problems).append(" more problems...<br>");
        break;
      }
    }
    buffer.append("</body></html>");
    return buffer.toString();
  }

  public void remove(@NotNull PsIssueType type) {
    for (PsNavigationPath path : myIssues.keySet()) {
      List<PsIssue> toRemove = Collections.emptyList();
      Collection<PsIssue> issues = myIssues.getModifiable(path);
      if (!issues.isEmpty()) {
        toRemove = issues.stream().filter(issue -> issue.getType().equals(type)).collect(Collectors.toList());
      }
      if (!toRemove.isEmpty()) {
        issues.removeAll(toRemove);
      }
    }
  }

  @TestOnly
  void clear() {
    myIssues.clear();
  }
}
