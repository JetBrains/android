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
package com.android.tools.idea.common.lint;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.lint.checks.RtlDetector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
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
  /**
   * A map from a component to a list of issues for that component.
   */
  @Nullable private ListMultimap<NlComponent, IssueData> myIssues;
  private List<IssueData> myIssueList;

  @NotNull
  public Collection<NlComponent> getComponentsWithIssues() {
    return myIssues == null ? Collections.emptyList() : myIssues.keySet();
  }

  @Nullable
  public Icon getIssueIcon(@NotNull NlComponent component) {
    return getIssueIcon(component, true, false);
  }

  /**
   * Get the icon for the severity level of the issue associated with the
   * given component. If the component has no issue, the returned icon will be null
   *
   * @param component The component the get the icon for
   * @param smallSize If true, will return an 8x8 icon, otherwise it will return a 16x16
   *                  (or scaled equivalent for HiDpi screen)
   * @param selected
   * @return The icon for the severity level of the issue.
   */
  @Nullable
  public Icon getIssueIcon(@NotNull NlComponent component, boolean smallSize, boolean selected) {
    if (myIssues == null) {
      return null;
    }
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }

    IssueData max = findHighestSeverityIssue(issueData);
    boolean isError = HighlightDisplayLevel.ERROR.equals(max.level);
    if (smallSize) {
      return isError ? AndroidIcons.Issue.ErrorBadge : AndroidIcons.Issue.WarningBadge;
    }

    if (selected) {
      return isError ? AndroidIcons.Issue.ErrorSelected : AndroidIcons.Issue.WarningSelected;
    }
    return isError ? AndroidIcons.Issue.Error : AndroidIcons.Issue.Warning;
  }

  /**
   * Convenience method for {@link #getIssueMessage(NlComponent, boolean true)}
   */
  @Nullable
  public String getIssueMessage(@NotNull NlComponent component) {
    return getIssueMessage(component, true);
  }

  /**
   * If the provided component has an issue, return the message associated with the highest
   * severity issue for the component.
   *
   * @param component                The component to get the issue from
   * @param includeStaticDescription If true, the returned String will include the {@link AndroidLintInspectionBase#getStaticDescription()}
   *                                 associated with this issue
   * @return The issue message or null if the component has no issue.
   */
  @Nullable
  public String getIssueMessage(@NotNull NlComponent component, boolean includeStaticDescription) {
    if (myIssues == null) {
      return null;
    }
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }

    IssueData max = findHighestSeverityIssue(issueData);
    if (includeStaticDescription) {
      return max.message + "<br><br>\n" + max.inspection.getStaticDescription();
    }
    return max.message;
  }

  private static IssueData findHighestSeverityIssue(List<IssueData> issueData) {
    return Collections.max(issueData);
  }

  public void addIssue(@NotNull NlComponent component,
                       @NotNull Issue issue,
                       @NotNull String message,
                       @NotNull AndroidLintInspectionBase inspection,
                       @NotNull HighlightDisplayLevel level,
                       @NotNull PsiElement startElement,
                       @NotNull PsiElement endElement,
                       @Nullable LintFix quickfixData) {
    // Constraint layout doesn't handle RTL issues yet; don't highlight these
    if (issue == RtlDetector.COMPAT) {
      return;
    }
    if (myIssues == null) {
      myIssues = ArrayListMultimap.create();
      myIssueList = Lists.newArrayList();
    }

    IssueData data = new IssueData(component, inspection, issue, message, level, startElement, endElement, quickfixData);
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

  public static class IssueData implements Comparable<IssueData> {
    @NotNull public final AndroidLintInspectionBase inspection;
    @NotNull public final HighlightDisplayLevel level;
    @NotNull public final String message;
    @NotNull public final Issue issue;
    @NotNull public final PsiElement endElement;
    @NotNull public final PsiElement startElement;
    @NotNull public final NlComponent component;
    @Nullable public final LintFix quickfixData;

    private IssueData(@NotNull NlComponent component,
                      @NotNull AndroidLintInspectionBase inspection,
                      @NotNull Issue issue,
                      @NotNull String message,
                      @NotNull HighlightDisplayLevel level,
                      @NotNull PsiElement startElement,
                      @NotNull PsiElement endElement,
                      @Nullable LintFix quickfixData) {
      this.component = component;
      this.inspection = inspection;
      this.issue = issue;
      this.message = message;
      this.level = level;
      this.startElement = startElement;
      this.endElement = endElement;
      this.quickfixData = quickfixData;
    }

    /**
     * Compare the issue by comparing the following properties, sorted by Highest priority first
     * <ul>
     * <li> {@link HighlightDisplayLevel#getSeverity()}
     * <li> {@link Issue#priority}
     * <li> {@link Issue#severity}
     * </ul>
     */
    @Override
    public int compareTo(@NotNull IssueData o) {
      return ComparisonChain.start()
        .compare(this.level.getSeverity(), o.level.getSeverity())
        .compare(this.issue.getPriority(), o.issue.getPriority())
        .compare(o.issue.getDefaultSeverity(), this.issue.getDefaultSeverity()) // Inverted on purpose
        .result();
    }
  }
}
