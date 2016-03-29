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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.issues.module;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class RootNode extends AbstractPsNode {
  private List<SimpleNode> myChildren = Collections.emptyList();

  RootNode() {
    setAutoExpandNode(true);
  }

  void createChildren(@NotNull List<PsIssue> newIssues) {
    if (!newIssues.isEmpty()) {
      Map<PsIssue.Type, List<PsIssue>> issuesByType = Maps.newHashMap();
      for (PsIssue issue : newIssues) {
        PsIssue.Type type = issue.getType();
        List<PsIssue> issues = issuesByType.get(type);
        if (issues == null) {
          issues = Lists.newArrayList();
          issuesByType.put(type, issues);
        }
        issues.add(issue);
      }

      List<PsIssue.Type> types = Lists.newArrayList(issuesByType.keySet());
      Collections.sort(types, new Comparator<PsIssue.Type>() {
        @Override
        public int compare(PsIssue.Type t1, PsIssue.Type t2) {
          return t1.getPriority() - t2.getPriority();
        }
      });

      List<SimpleNode> children = Lists.newArrayList();

      for (PsIssue.Type type : types) {
        List<PsIssue> issues = issuesByType.get(type);
        assert issues != null;
        children.add(new IssueTypeNode(type, issues));
      }

      myChildren = children;

      return;
    }
    myChildren = Collections.emptyList();
  }

  @Override
  public SimpleNode[] getChildren() {
    return !myChildren.isEmpty() ? myChildren.toArray(new SimpleNode[myChildren.size()]) : NO_CHILDREN;
  }
}
