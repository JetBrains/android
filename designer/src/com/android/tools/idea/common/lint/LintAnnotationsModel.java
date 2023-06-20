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

import com.android.tools.idea.common.error.IssueSource;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.lint.common.AndroidLintInspectionBase;
import com.android.tools.idea.lint.common.SuppressLintQuickFix;
import com.android.tools.lint.checks.RtlDetector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintAnnotationsModel {
  /**
   * A map from a component to a list of issues for that component.
   */
  private ListMultimap<NlComponent, IssueData> myIssues = ImmutableListMultimap.of();
  private ListMultimap<AttributeKey, IssueData> myAttributeIssues = ImmutableListMultimap.of();
  private List<IssueData> myIssueList = Collections.emptyList();

  /**
   * If the provided component has an issue, return the message associated with the highest
   * severity issue for the component.
   *
   * @param component                The component to get the issue from
   * @return The issue message or null if the component has no issue.
   */
  @Nullable
  public String getIssueMessage(@NotNull NlComponent component) {
    if (myIssues == null) {
      return null;
    }
    List<IssueData> issueData = myIssues.get(component);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }
    return findHighestSeverityIssue(issueData).message;
  }

  /**
   * Get the issue level for a given attribute of a component.
   *
   * @param component the component that may have an issue
   * @param namespace the namespace of the attribute
   * @param attributeName the attribute name
   *
   * @return the highest level among the issues fround for this attribute
   */
  @Nullable
  public IssueData findIssue(@NotNull NlComponent component, @NotNull String namespace, @NotNull String attributeName) {
    if (myAttributeIssues == null) {
      return null;
    }
    AttributeKey attributeKey = new AttributeKey(component, namespace, attributeName);
    List<IssueData> issueData = myAttributeIssues.get(attributeKey);
    if (issueData == null || issueData.isEmpty()) {
      return null;
    }
    return findHighestSeverityIssue(issueData);
  }

  private static IssueData findHighestSeverityIssue(List<IssueData> issueData) {
    return Collections.max(issueData);
  }

  public void addIssue(@NotNull NlComponent component,
                       @Nullable AttributeKey attribute,
                       @NotNull Issue issue,
                       @NotNull String message,
                       @NotNull AndroidLintInspectionBase inspection,
                       @NotNull HighlightDisplayLevel level,
                       @NotNull SmartPsiElementPointer<PsiElement> startElement,
                       @NotNull SmartPsiElementPointer<PsiElement> endElement,
                       @Nullable LintFix quickfixData) {
    // Constraint layout doesn't handle RTL issues yet; don't highlight these
    if (issue == RtlDetector.COMPAT) {
      return;
    }
    if (myIssues.isEmpty()) {
      myIssues = ArrayListMultimap.create();
      myAttributeIssues = ArrayListMultimap.create();
      myIssueList = new ArrayList<>();
    }

    IssueData data = new IssueData(component, attribute, inspection, issue, message, level, startElement, endElement, quickfixData);
    myIssues.put(component, data);
    myIssueList.add(data); // TODO: Derive from myIssues map when needed?
    if (attribute != null) {
      myAttributeIssues.put(attribute, data);
    }
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
    @NotNull public final SmartPsiElementPointer<PsiElement> endElementPointer;
    @NotNull public final SmartPsiElementPointer<PsiElement> startElementPointer;
    @NotNull public final NlComponent component;
    @NotNull public final IssueSource issueSource;
    @Nullable public final AttributeKey attribute;
    @Nullable public final LintFix quickfixData;

    private IssueData(@NotNull NlComponent component,
                      @Nullable AttributeKey attribute,
                      @NotNull AndroidLintInspectionBase inspection,
                      @NotNull Issue issue,
                      @NotNull String message,
                      @NotNull HighlightDisplayLevel level,
                      @NotNull SmartPsiElementPointer<PsiElement> startElementPointer,
                      @NotNull SmartPsiElementPointer<PsiElement> endElementPointer,
                      @Nullable LintFix quickfixData) {
      this.component = component;
      this.issueSource = IssueSource.fromNlComponent(component);
      this.attribute = attribute;
      this.inspection = inspection;
      this.issue = issue;
      this.message = message;
      this.level = level;
      this.startElementPointer = startElementPointer;
      this.endElementPointer = endElementPointer;
      this.quickfixData = quickfixData;
    }

    @Nullable
    public SuppressLintQuickFix getSuppressLintQuickFix() {
      HighlightDisplayKey key = HighlightDisplayKey.find(inspection.getShortName());
      return key == null ? null : new SuppressLintQuickFix(key.getID(), startElementPointer.getElement());
    }

    /**
     * Compare the issue by comparing the following properties, sorted by Highest priority first
     * <ul>
     * <li> {@link HighlightDisplayLevel#getSeverity()}
     * <li> {@link Issue#getPriority()}
     * <li> {@link Issue#getDefaultSeverity()}
     * </ul>
     */
    @Override
    public int compareTo(@NotNull IssueData o) {
      return ComparisonChain.start()
        .compare(this.level.getSeverity(), o.level.getSeverity())
        .compare(this.issue.getPriority(), o.issue.getPriority())
        .compare(this.issue.getDefaultSeverity(), o.issue.getDefaultSeverity())
        .result();
    }
  }
}
