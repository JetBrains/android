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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.error.Issue;
import com.android.tools.idea.common.error.IssueProvider;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.google.common.collect.ImmutableCollection;
import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;

public class RenderIssueProvider implements IssueProvider {
  private final RenderErrorModel myRenderErrorModel;

  public RenderIssueProvider(@NotNull RenderErrorModel renderErrorModel) {
    myRenderErrorModel = renderErrorModel;
  }

  @Override
  public void collectIssues(@NotNull ImmutableCollection.Builder<Issue> issueListBuilder) {
    for (RenderErrorModel.Issue error : myRenderErrorModel.getIssues()) {
      issueListBuilder.add(NlRenderIssueWrapper.wrapIssue(error));
    }
  }

  /**
   * Wrapper class to use a {@link RenderErrorModel.Issue} as an {@link Issue}
   */
  @VisibleForTesting
  public static class NlRenderIssueWrapper extends Issue {

    private final RenderErrorModel.Issue myIssue;

    NlRenderIssueWrapper(@NotNull RenderErrorModel.Issue issue) {
      myIssue = issue;
    }

    /**
     * Create a new {@link Issue} wrapping a {@link RenderErrorModel.Issue}
     *
     * @param renderIssue The issue to wrap
     * @return the newly created {@link Issue}
     */
    @NotNull
    public static Issue wrapIssue(@NotNull RenderErrorModel.Issue renderIssue) {
      return new NlRenderIssueWrapper(renderIssue);
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

    @Nullable
    @Override
    public NlComponent getSource() {
      return null;
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
  }

}
