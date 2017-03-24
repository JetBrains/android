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
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Model to centralize every issue that should be used in the Layout Editor
 */
public class IssueModel {

  @Nullable private RenderErrorModel myRenderErrorModel;
  @Nullable private LintAnnotationsModel myLintAnnotationsModel;
  private final List<NlIssue> myIssues = new ArrayList<>();
  private final List<IssueModelListener> myListeners = new ArrayList<>(1);
  private int myWarningCount;
  private int myErrorCount;

  public void setRenderErrorModel(@NotNull RenderErrorModel renderErrorModel) {
    myRenderErrorModel = renderErrorModel;
    updateErrorsList();
  }

  private void updateErrorsList() {
    myIssues.clear();
    myWarningCount = 0;
    myErrorCount = 0;
    if (myRenderErrorModel != null) {
      myRenderErrorModel.getIssues().forEach(error -> {
        NlIssue issue = NlIssue.wrapIssue(error);
        myIssues.add(issue);
        updateIssuesCounts(issue);
      });
    }
    if (myLintAnnotationsModel != null) {
      myLintAnnotationsModel.getIssues().forEach(error -> {

        NlIssue issue = NlIssue.wrapIssue(error);
        myIssues.add(issue);
        updateIssuesCounts(issue);
      });
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

  public void setLintAnnotationsModel(@NotNull LintAnnotationsModel lintAnnotationsModel) {
    myLintAnnotationsModel = lintAnnotationsModel;
    updateErrorsList();
  }

  @NotNull
  public List<NlIssue> getNlErrors() {
    return myIssues;
  }

  public boolean hasRenderError() {
    return myRenderErrorModel != null && !myRenderErrorModel.getIssues().isEmpty();
  }

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

  public boolean hasIssues() {
    return !myIssues.isEmpty();
  }

  public interface IssueModelListener {
    void errorModelChanged();
  }
}
