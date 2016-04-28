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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import icons.AndroidIcons;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LintAnnotationsModel {
  /** A map from a component to a list of issues for that component. */
  private ListMultimap<NlComponent, IssueData> myIssues;

  @NotNull
  public Collection<NlComponent> getComponentsWithIssues() {
    return myIssues == null ? Collections.<NlComponent>emptyList() : myIssues.keySet();
  }

  @Nullable
  public Icon getIssueIcon(@NotNull NlComponent component) {
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }

    IssueData max = findHighestSeverityIssue(issueData);
    return HighlightDisplayLevel.ERROR.equals(max.level) ? AndroidIcons.ErrorBadge : AndroidIcons.WarningBadge;
  }

  public String getIssueMessage(@NotNull NlComponent component) {
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }

    IssueData max = findHighestSeverityIssue(issueData);
    return max.message + "<br><br>\n" + max.inspection.getStaticDescription();
  }

  private static IssueData findHighestSeverityIssue(List<IssueData> issueData) {
    return Collections.max(issueData, new Comparator<IssueData>() {
      @Override
      public int compare(IssueData o1, IssueData o2) {
        return o1.level.getSeverity().compareTo(o2.level.getSeverity());
      }
    });
  }

  public void addIssue(@NotNull NlComponent component,
                       @NotNull String message,
                       @NotNull AndroidLintInspectionBase inspection,
                       @NotNull HighlightDisplayLevel level) {
    if (myIssues == null) {
      myIssues = ArrayListMultimap.create();
    }

    myIssues.put(component, new IssueData(inspection, message, level));
  }

  private static class IssueData {
    @NotNull public final AndroidLintInspectionBase inspection;
    @NotNull public final HighlightDisplayLevel level;
    @NotNull private final String message;

    private IssueData(@NotNull AndroidLintInspectionBase inspection, @NotNull String message, @NotNull HighlightDisplayLevel level) {
      this.inspection = inspection;
      this.message = message;
      this.level = level;
    }
  }
}
