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
package com.android.tools.idea.uibuilder.error;

import com.android.tools.idea.common.error.IssueSource;
import com.android.tools.idea.common.model.NlModel;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.idea.common.error.Issue;
import com.android.tools.idea.common.error.IssueProvider;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.lang.annotation.HighlightSeverity;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;
import org.jetbrains.annotations.TestOnly;

public class RenderIssueProvider extends IssueProvider {
  @NotNull private final RenderErrorModel myRenderErrorModel;
  @Nullable private final NlModel mySourceNlModel;

  public RenderIssueProvider(@Nullable NlModel sourceNlModel, @NotNull RenderErrorModel renderErrorModel) {
    myRenderErrorModel = renderErrorModel;
    mySourceNlModel = sourceNlModel;
  }

  @Override
  public void collectIssues(@NotNull ImmutableCollection.Builder<Issue> issueListBuilder) {
    myRenderErrorModel.getIssues().forEach(
      issue -> issueListBuilder.add(NlRenderIssueWrapper.wrapIssue(issue, mySourceNlModel)));
  }

  /**
   * Wrapper class to use a {@link RenderErrorModel.Issue} as an {@link Issue}
   */
  @VisibleForTesting
  public static class NlRenderIssueWrapper extends Issue {

    private final RenderErrorModel.Issue myIssue;
    private final IssueSource mySource;

    private NlRenderIssueWrapper(@NotNull RenderErrorModel.Issue issue, @Nullable NlModel sourceModel) {
      myIssue = issue;
      mySource = sourceModel == null ? IssueSource.NONE : IssueSource.fromNlModel(sourceModel);
    }

    /**
     * Create a new {@link Issue} wrapping a {@link RenderErrorModel.Issue}
     *
     * @param renderIssue The issue to wrap.
     * @param sourceModel Optional source {@linl NlModel} that generated these issues.
     */
    @NotNull
    public static Issue wrapIssue(@NotNull RenderErrorModel.Issue renderIssue, @Nullable NlModel sourceModel) {
      return new NlRenderIssueWrapper(renderIssue, sourceModel);
    }

    @NotNull
    @Override
    public String getSummary() {
      return myIssue.getSummary();
    }

    @NotNull
    @Override
    public String getDescription() {
      return myIssue.getHtmlContent();
    }

    @NotNull
    @Override
    public HighlightSeverity getSeverity() {
      return myIssue.getSeverity();
    }

    @NotNull
    @Override
    public IssueSource getSource() {
      return mySource;
    }

    @NotNull
    @Override
    public String getCategory() {
      return "Rendering Issue";
    }

    @Override
    @Nullable
    public HyperlinkListener getHyperlinkListener() {
      return myIssue.getHyperlinkListener();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      NlRenderIssueWrapper wrapper = (NlRenderIssueWrapper)o;
      return Objects.equals(myIssue, wrapper.myIssue) && Objects.equals(mySource, wrapper.mySource);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), myIssue, mySource);
    }
  }

}
