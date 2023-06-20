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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ModalityUiUtil;
import icons.StudioIcons;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Model to centralize every issue that should be used in the Layout Editor
 */
public class IssueModel implements Disposable {
  private static final int MAX_ISSUE_NUMBER_LIMIT = 200;

  @NotNull private final Project myProject;
  /**
   * Maximum number of issues allowed by this model. This allows to limit how many issues will be handled
   * by this model.
   */
  private final int myIssueNumberLimit;
  @NotNull private ImmutableList<Issue> myIssues = ImmutableList.of();
  private final ListenerCollection<IssueModelListener> myListeners;
  protected int myWarningCount;
  protected int myErrorCount;
  @VisibleForTesting
  public final Runnable myUpdateCallback = () -> updateErrorsList();

  private final Set<IssueProvider> myIssueProviders = new HashSet<>();

  @NotNull
  private final AtomicBoolean myIsActivated = new AtomicBoolean(true);

  /**
   * IssueModel constructor.
   * @param listenerExecutor {@link Executor} to run the listeners execution.
   * @param issueNumberLimit maximum number of issues to be handled by this model. If the number of issues exceeds this number, it will be
   *                         truncated to <code>issueNumberLimit</code> and a new {@link TooManyIssuesIssue} added.
   */
  private IssueModel(@NotNull Disposable parentDisposable, @NotNull Project project, @NotNull Executor listenerExecutor, int issueNumberLimit) {
    Disposer.register(parentDisposable, this);
    myProject = project;
    myListeners = ListenerCollection.createWithExecutor(listenerExecutor);
    myIssueNumberLimit = issueNumberLimit;
  }

  public IssueModel(@NotNull Disposable parentDisposable, @NotNull Project project) {
    this(parentDisposable, project, command -> ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), command), MAX_ISSUE_NUMBER_LIMIT);
  }

  @TestOnly
  public static IssueModel createForTesting(@NotNull Disposable parentDisposable, @NotNull Project project, int issueNumberLimit) {
    return new IssueModel(parentDisposable, project, MoreExecutors.directExecutor(), issueNumberLimit);
  }

  @TestOnly
  public static IssueModel createForTesting(@NotNull Disposable parentDisposable, @NotNull Project project) {
    return new IssueModel(parentDisposable, project, MoreExecutors.directExecutor(), MAX_ISSUE_NUMBER_LIMIT);
  }

  @Nullable
  public Issue getHighestSeverityIssue(@NotNull NlComponent component) {
    IssueSource componentSource = IssueSource.fromNlComponent(component);
    Issue[] filtered = myIssues.stream()
      .filter((it) -> componentSource.equals(it.getSource()))
      .toArray(size -> new Issue[size]);

    if (filtered.length == 0) {
      return null;
    }

    Issue max = filtered[0];
    for (int i = 1; i < filtered.length; i++) {
      Issue it = filtered[i];
      if (max.getSeverity().compareTo(it.getSeverity()) < 0) {
        max = it;
      }
    }

    return max;
  }

  @Override
  public void dispose() {
    myIssueProviders.clear();
    myListeners.clear();
  }

  /**
   * Get the icon for the severity level.
   *
   * @return The icon for the severity level of the issue.
   */
  @Nullable
  public static Icon getIssueIcon(@NotNull HighlightSeverity severity, boolean selected) {
    boolean isError = severity == HighlightSeverity.ERROR;
    if (selected) {
      return isError ? StudioIcons.Common.ERROR_INLINE_SELECTED : StudioIcons.Common.WARNING_INLINE_SELECTED;
    }
    return isError ? StudioIcons.Common.ERROR_INLINE : StudioIcons.Common.WARNING_INLINE;
  }

  public void updateErrorsList() {
    myWarningCount = 0;
    myErrorCount = 0;
    ImmutableList.Builder<Issue> issueListBuilder = ImmutableList.builder();

    ImmutableSet<IssueProvider> providers;
    synchronized (myIssueProviders) {
      providers = ImmutableSet.copyOf(myIssueProviders);
    }
    for (IssueProvider provider : providers) {
      provider.collectIssues(issueListBuilder);
    }

    ImmutableList<Issue> newIssueList = issueListBuilder.build();
    if (newIssueList.size() > myIssueNumberLimit) {
      newIssueList = ImmutableList.<Issue>builder()
        .addAll(newIssueList.subList(0, myIssueNumberLimit))
        .add(new TooManyIssuesIssue(newIssueList.size() - myIssueNumberLimit))
        .build();
    }
    newIssueList.forEach(issue -> updateIssuesCounts(issue));
    myIssues = newIssueList;
    // Run listeners on the UI thread
    myListeners.forEach(IssueModelListener::errorModelChanged);
    if (myIsActivated.get()) {
      myProject.getMessageBus().syncPublisher(IssueProviderListener.TOPIC).issueUpdated(this, newIssueList);
    }
  }

  private void updateIssuesCounts(@NotNull Issue issue) {
    if (issue.getSeverity().equals(HighlightSeverity.WARNING)) {
      myWarningCount++;
    }
    else if (issue.getSeverity().equals(HighlightSeverity.ERROR)) {
      myErrorCount++;
    }
  }

  /**
   * Add issue provider
   * @param update true if issueModel should be updated to display newest information. False otherwise.
   */
  public void addIssueProvider(@NotNull IssueProvider issueProvider, boolean update) {
    synchronized (myIssueProviders) {
      myIssueProviders.add(issueProvider);
    }
    issueProvider.addListener(myUpdateCallback);
    if (update) {
      updateErrorsList();
    }
  }

  /** Add issue provider, and update the error list. */
  public void addIssueProvider(@NotNull IssueProvider issueProvider) {
    addIssueProvider(issueProvider, true);
  }

  public void removeIssueProvider(@NotNull IssueProvider issueProvider) {
    synchronized (myIssueProviders) {
      myIssueProviders.remove(issueProvider);
    }
    issueProvider.removeListener(myUpdateCallback);
    updateErrorsList();
  }

  public void removeAllIssueProviders() {
    synchronized (myIssueProviders) {
      myIssueProviders.forEach(provider -> provider.removeListener(myUpdateCallback));
      myIssueProviders.clear();
    }
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

  public void activate() {
    myIsActivated.set(true);
    myProject.getMessageBus().syncPublisher(IssueProviderListener.TOPIC).issueUpdated(this, myIssues);
  }

  public void deactivate() {
    myIsActivated.set(false);
    myProject.getMessageBus().syncPublisher(IssueProviderListener.TOPIC).issueUpdated(this, Collections.emptyList());
  }

  public interface IssueModelListener {
    void errorModelChanged();
  }
}
