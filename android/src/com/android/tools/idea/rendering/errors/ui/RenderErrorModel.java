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
package com.android.tools.idea.rendering.errors.ui;

import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.HtmlBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class RenderErrorModel {

  /**
   * {@link RenderErrorModel} with one issue that is displayed while the project is still building. This avoids displaying an error
   * panel full of errors.
   */
  public static final RenderErrorModel STILL_BUILDING_ERROR_MODEL = new RenderErrorModel(ImmutableList.of(
    Issue.builder()
      .setSeverity(HighlightSeverity.INFORMATION)
      .setSummary("The project is still building")
      .setHtmlContent(new HtmlBuilder()
                        .add("The project is still building and the current preview might be inaccurate.")
                        .newline()
                        .add("The preview will automatically refresh once the build finishes."))
      .build()
  ));
  private ImmutableList<Issue> myIssues = ImmutableList.of();

  public RenderErrorModel(@NotNull Collection<Issue> issues) {
    myIssues = ImmutableList.copyOf(issues);
  }

  @NotNull
  public ImmutableList<Issue> getIssues() {
    return myIssues;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RenderErrorModel that = (RenderErrorModel)o;
    return Objects.equals(myIssues, that.myIssues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myIssues);
  }

  /**
   * Holder for issues contained in {@link RenderErrorModel}. An issue has the following fields:
   * <ul>
   * <li><b>Severity</b>: Indicates the severity of the issue.
   * <li><b>Summary</b>: A brief description of the issue.
   * <li><b>HTML content</b>: A detailed description of the issue that can contain HTML, including links.
   * <li><b>Link handler</b>: An {@link HyperlinkListener} to handle any links on the HTML content. It can be null.</li>
   * </ui>
   */
  public static class Issue implements Comparable<Issue> {
    private HighlightSeverity mySeverity = HighlightSeverity.INFORMATION;
    private String mySummary;
    private String myHtmlContent;
    private HyperlinkListener myHyperlinkListener;
    // myHtmlContent with HTML tags stripped. Used for comparisons
    private String myCachedPlainContent;

    private Issue() {
    }

    @NotNull
    public static Builder builder() {
      return new Builder();
    }

    @NotNull
    public HighlightSeverity getSeverity() {
      return mySeverity;
    }

    @NotNull
    public String getSummary() {
      return StringUtil.notNullize(mySummary);
    }

    @NotNull
    public String getHtmlContent() {
      return StringUtil.notNullize(myHtmlContent);
    }

    /**
     * Returns the HTML content with the HTML tags stripped.
     */
    @NotNull
    private String getPlainContent() {
      if (myCachedPlainContent == null) {
        myCachedPlainContent = TextFormat.HTML.toText(myHtmlContent);
      }

      return myCachedPlainContent;
    }

    @Nullable
    public HyperlinkListener getHyperlinkListener() {
      return myHyperlinkListener;
    }

    @Override
    public int compareTo(@NotNull Issue o) {
      return ComparisonChain.start()
        .compare(o.getSeverity(), this.getSeverity()) // ERRORS at the top
        .compare(mySummary, o.mySummary)
        .result();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Issue issue = (Issue)o;
      return Objects.equals(mySeverity, issue.mySeverity) &&
             Objects.equals(mySummary, issue.mySummary) &&
             Objects.equals(getPlainContent(), issue.getPlainContent());
    }

    @Override
    public int hashCode() {
      return Objects.hash(mySeverity, mySummary, myHtmlContent);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
        .add("severity", mySeverity)
        .add("summary", mySummary)
        .add("htmlContent", myHtmlContent)
        .add("hasHyperlinkListener", myHyperlinkListener != null)
        .toString();
    }

    public static class Builder {
      private final Issue myIssue = new Issue();

      @NotNull
      private Builder setHtmlContent(@NotNull String htmlContent) {
        myIssue.myHtmlContent = htmlContent;
        myIssue.myCachedPlainContent = null;

        return this;
      }

      @NotNull
      public Builder setHtmlContent(@NotNull HtmlBuilder htmlBuilder) {
        return setHtmlContent(htmlBuilder.getStringBuilder().toString());
      }

      @NotNull
      public Builder setSeverity(@NotNull HighlightSeverity severity) {
        myIssue.mySeverity = severity;

        return this;
      }

      /**
       * Same as {@link #setSeverity(HighlightSeverity)} but allows to increase (or decrease) the relative priority of the issue.
       * This can be used to promote certain issues to the top of the list.
       */
      @NotNull
      public Builder setSeverity(@NotNull HighlightSeverity severity, int priority) {
        myIssue.mySeverity = new HighlightSeverity(severity.myName, severity.myVal + priority);

        return this;
      }

      @NotNull
      public Builder setSummary(@NotNull String summary) {
        myIssue.mySummary = summary;

        return this;
      }

      @NotNull
      public Builder setLinkHandler(@NotNull HyperlinkListener listener) {
        myIssue.myHyperlinkListener = listener;

        return this;
      }

      @NotNull
      public Issue build() {
        if (myIssue.mySummary == null) {
          myIssue.mySummary = "";
        }
        if (myIssue.mySeverity == null) {
          myIssue.mySeverity = HighlightSeverity.INFORMATION;
        }
        if (myIssue.myHtmlContent == null) {
          myIssue.myHtmlContent = "";
        }

        // TODO: We should probably return a copy and not the reference to the final attribute
        return myIssue;
      }
    }
  }
}
