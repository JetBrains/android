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
package com.android.tools.idea.uibuilder.error;

import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;

/**
 * Model to centralize every issue that should be used in the Layout Editor
 */
public class IssueModel {

  @Nullable private RenderErrorModel myRenderErrorModel;
  @Nullable private LintAnnotationsModel myLintAnnotationsModel;
  @GuardedBy("itself") private final List<NlIssue> myIssues = new ArrayList<>();
  private final List<IssueModelListener> myListeners = new ArrayList<>(1);
  private int myWarningCount;
  private int myErrorCount;

  @GuardedBy("myIssues")
  public void setRenderErrorModel(@NotNull RenderErrorModel renderErrorModel) {
    myRenderErrorModel = renderErrorModel;
    updateErrorsList();
  }

  @GuardedBy("myIssues")
  private void updateErrorsList() {
    myIssues.clear();
    myWarningCount = 0;
    myErrorCount = 0;
    if (myRenderErrorModel != null) {
      for (RenderErrorModel.Issue error : myRenderErrorModel.getIssues()) {
        NlIssue issue = NlIssue.wrapIssue(error);
        myIssues.add(issue);
        updateIssuesCounts(issue);
      }
    }
    if (myLintAnnotationsModel != null) {
      for (LintAnnotationsModel.IssueData error : myLintAnnotationsModel.getIssues()) {
        NlIssue issue = NlIssue.wrapIssue(error);
        myIssues.add(issue);
        updateIssuesCounts(issue);
      }
    }

    for (IssueModelListener myListener : myListeners) {
      myListener.errorModelChanged();
    }
  }

  private void updateIssuesCounts(@NotNull NlIssue issue) {
    if (issue.getSeverity().equals(HighlightSeverity.WARNING)) {
      myWarningCount++;
    }
    else if (issue.getSeverity().equals(HighlightSeverity.ERROR)) {
      myErrorCount++;
    }
  }

  @GuardedBy("myIssues")
  public void setLintAnnotationsModel(@NotNull LintAnnotationsModel lintAnnotationsModel) {
    myLintAnnotationsModel = lintAnnotationsModel;
    updateErrorsList();
  }

  @GuardedBy("myIssues")
  @NotNull
  public List<NlIssue> getNlErrors() {
    return Collections.unmodifiableList(myIssues);
  }

  public boolean hasRenderError() {
    return myRenderErrorModel != null && !myRenderErrorModel.getIssues().isEmpty();
  }

  @GuardedBy("myIssues")
  public int getIssueCount() {
    return myIssues.size();
  }

  public void addErrorModelListener(@NotNull IssueModelListener listener) {
    myListeners.add(listener);
  }

  public void removeErrorModelListener(@NotNull IssueModelListener listener) {
    myListeners.remove(listener);
  }

  public int getWarningCount() {
    return myWarningCount;
  }

  public int getErrorCount() {
    return myErrorCount;
  }

  @GuardedBy("myIssues")
  public boolean hasIssues() {
    return !myIssues.isEmpty();
  }

  @GuardedBy("myIssues")
  @Nullable
  public NlIssue findIssue(@NotNull NlComponent component) {
    for (NlIssue issue : myIssues) {
      if (component.equals(issue.getSource())) {
        return issue;
      }
    }
    return null;
  }

  public interface IssueModelListener {
    void errorModelChanged();
  }
}
