/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.error;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.GuiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Model to centralize every issue that should be used in the Layout Editor
 */
public class IssueModel {

  private ImmutableList<Issue> myIssues = ImmutableList.of();
  private final ListenerCollection<IssueModelListener> myListeners = ListenerCollection.createWithExecutor(
    command -> GuiUtils.invokeLaterIfNeeded(command, ModalityState.defaultModalityState()));
  protected int myWarningCount;
  protected int myErrorCount;

  private List<IssueProvider> myIssueProviders = new ArrayList<>();

  @VisibleForTesting
  public void updateErrorsList() {
    myWarningCount = 0;
    myErrorCount = 0;
    ImmutableList.Builder<Issue> issueListBuilder = ImmutableList.builder();

    for (IssueProvider provider : myIssueProviders) {
      provider.collectIssues(issueListBuilder);
    }

    myIssues = issueListBuilder.build();
    myIssues.forEach(issue -> updateIssuesCounts(issue));
    // Run listeners on the UI thread
    myListeners.forEach(IssueModelListener::errorModelChanged);
  }

  private void updateIssuesCounts(@NotNull Issue issue) {
    if (issue.getSeverity().equals(HighlightSeverity.WARNING)) {
      myWarningCount++;
    }
    else if (issue.getSeverity().equals(HighlightSeverity.ERROR)) {
      myErrorCount++;
    }
  }

  public void addIssueProvider(@NotNull IssueProvider issueProvider) {
    myIssueProviders.add(issueProvider);
    updateErrorsList();
  }

  public void removeIssueProvider(@NotNull IssueProvider issueProvider) {
    myIssueProviders.remove(issueProvider);
    updateErrorsList();
  }

  @NotNull
  public ImmutableList<Issue> getIssues() {
    return myIssues;
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

  @Nullable
  public Issue findIssue(@NotNull NlComponent component) {
    for (Issue issue : myIssues) {
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
