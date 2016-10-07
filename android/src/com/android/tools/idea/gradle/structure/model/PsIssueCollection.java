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
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.PLAIN_TEXT;

public class PsIssueCollection {
  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull private final Multimap<PsPath, PsIssue> myIssues = HashMultimap.create();

  @NotNull private final PsContext myContext;

  public PsIssueCollection(@NotNull PsContext context) {
    myContext = context;
  }

  public void add(@NotNull PsIssue issue) {
    PsPath path = issue.getPath();
    PsPath extraPath = issue.getExtraPath();
    synchronized (myLock) {
      myIssues.put(path, issue);
      if (extraPath != null) {
        myIssues.put(extraPath, issue);
      }
    }
  }

  @NotNull
  public List<PsIssue> findIssues(@NotNull PsModel model, @Nullable Comparator<PsIssue> comparator) {
    PsPath path = null;
    if (model instanceof PsModule) {
      PsModule module = (PsModule)model;
      path = new PsModulePath(module);
    }

    if (model instanceof PsLibraryDependency) {
      PsLibraryDependency dependency = (PsLibraryDependency)model;
      path = new PsLibraryDependencyNavigationPath(myContext, dependency);
    }

    if (path != null) {
      return findIssues(path, comparator);
    }

    return Collections.emptyList();
  }

  @NotNull
  public List<PsIssue> findIssues(@NotNull PsPath path, @Nullable Comparator<PsIssue> comparator) {
    List<PsIssue> issueList;
    synchronized (myLock) {
      issueList = myIssues.get(path).stream().collect(Collectors.toList());
    }
    if (comparator != null && issueList.size() > 1) {
      Collections.sort(issueList, comparator);
    }
    return issueList;
  }

  @NotNull
  public List<PsIssue> getValues() {
    synchronized (myLock) {
      return myIssues.values().stream().collect(Collectors.toList());
    }
  }

  @NotNull
  public List<PsIssue> getValues(@NotNull Class<? extends PsPath> pathType) {
    Set<PsIssue> issues = Sets.newHashSet();
    synchronized (myLock) {
      myIssues.keySet().stream().filter(pathType::isInstance).forEachOrdered(path -> issues.addAll(myIssues.get(path)));
    }
    return issues.stream().collect(Collectors.toList());
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myIssues.isEmpty();
    }
  }

  @Nullable
  public static String getTooltipText(@NotNull List<PsIssue> issues, boolean includePath) {
    if (issues.isEmpty()) {
      return null;
    }

    List<PsIssue> sorted = Lists.newArrayList(issues);
    Collections.sort(sorted, IssuesByTypeAndTextComparator.INSTANCE);

    boolean useBullets = issues.size() > 1;

    // Removed duplicated lines.
    Set<String> lines = Sets.newLinkedHashSet();
    for (PsIssue issue : sorted) {
      String line = issue.getText();
      if (includePath) {
        String path = issue.getPath().toText(PLAIN_TEXT);
        if (!path.isEmpty()) {
          line = path + ": " + line;
        }
      }
      if (useBullets) {
        line = "<li>" + line + "</li>";
      }
      lines.add(line);
    }

    StringBuilder buffer = new StringBuilder();
    buffer.append("<html><body>");
    if (useBullets) {
      buffer.append("<ul>");
    }
    int issueCount = lines.size();
    int problems = 0;

    int count = 0;

    boolean tooManyMessages = false;

    for (String line : lines) {
      buffer.append(line);
      if (!useBullets) {
        buffer.append("<br>");
      }
      problems++;

      if (count++ > 9 && issueCount > 12) {
        if (useBullets) {
          buffer.append("</ul>");
        }
        buffer.append(issueCount - problems).append(" more messages...<br>");
        tooManyMessages = true;
        break;
      }
    }
    if (useBullets && !tooManyMessages) {
      buffer.append("</ul>");
    }
    buffer.append("</body></html>");
    return buffer.toString();
  }

  public void remove(@NotNull PsIssueType type) {
    synchronized (myLock) {
      Set<PsPath> paths = myIssues.keySet();
      List<Pair<PsPath,PsIssue>> toRemove = Lists.newArrayList();
      for (PsPath path : paths) {
        Collection<PsIssue> issues = myIssues.get(path);
        for (PsIssue issue : issues) {
          if (issue.getType().equals(type)) {
            toRemove.add(Pair.create(path, issue));
          }
        }
      }

      for (Pair<PsPath, PsIssue> pair : toRemove) {
        myIssues.remove(pair.getFirst(), pair.getSecond());
      }
    }
  }

  @TestOnly
  void clear() {
    synchronized (myLock) {
      myIssues.clear();
    }
  }
}
