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
package com.android.tools.idea.uibuilder.lint;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.lint.checks.RtlDetector;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.psi.PsiElement;
import icons.AndroidIcons;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LintAnnotationsModel {
  /** A map from a component to a list of issues for that component. */
  private ListMultimap<NlComponent, IssueData> myIssues;
  private List<IssueData> myIssueList;

  @NotNull
  public Collection<NlComponent> getComponentsWithIssues() {
    return myIssues == null ? Collections.<NlComponent>emptyList() : myIssues.keySet();
  }

  @Nullable
  public Icon getIssueIcon(@NotNull NlComponent component) {
    if (myIssues == null) {
      return null;
    }
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }

    IssueData max = findHighestSeverityIssue(issueData);
    return HighlightDisplayLevel.ERROR.equals(max.level) ? AndroidIcons.ErrorBadge : AndroidIcons.WarningBadge;
  }

  public String getIssueMessage(@NotNull NlComponent component) {
    if (myIssues == null) {
      return null;
    }
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }

    IssueData max = findHighestSeverityIssue(issueData);
    return max.message + "<br><br>\n" + max.inspection.getStaticDescription();
  }

  private static IssueData findHighestSeverityIssue(List<IssueData> issueData) {
    return Collections.max(issueData, (o1, o2) -> o1.level.getSeverity().compareTo(o2.level.getSeverity()));
  }

  public void addIssue(@NotNull NlComponent component,
                       @NotNull Issue issue,
                       @NotNull String message,
                       @NotNull AndroidLintInspectionBase inspection,
                       @NotNull HighlightDisplayLevel level,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement) {
    // Constraint layout doesn't handle RTL issues yet; don't highlight these
    if (issue == RtlDetector.COMPAT) {
      return;
    }
    if (myIssues == null) {
      myIssues = ArrayListMultimap.create();
      myIssueList = Lists.newArrayList();
    }

    IssueData data = new IssueData(component, inspection, issue, message, level, startElement, endElement);
    myIssues.put(component, data);
    myIssueList.add(data); // TODO: Derive from myIssues map when needed?
  }

  public int getIssueCount() {
    return myIssueList == null ? 0 : myIssueList.size();
  }

  @NotNull
  public List<IssueData> getIssues() {
    return myIssueList != null ? myIssueList : Collections.emptyList();
  }

  static class IssueData {
    @NotNull public final AndroidLintInspectionBase inspection;
    @NotNull public final HighlightDisplayLevel level;
    @NotNull public final String message;
    @NotNull public final Issue issue;
    @NotNull public final PsiElement endElement;
    @NotNull public final PsiElement startElement;
    @NotNull public final NlComponent component;

    private IssueData(@NotNull NlComponent component,
                      @NotNull AndroidLintInspectionBase inspection,
                      @NotNull Issue issue,
                      @NotNull String message,
                      @NotNull HighlightDisplayLevel level,
                      @NotNull PsiElement startElement,
                      @NotNull PsiElement endElement) {
      this.component = component;
      this.inspection = inspection;
      this.issue = issue;
      this.message = message;
      this.level = level;
      this.startElement = startElement;
      this.endElement = endElement;
    }
  }
}
