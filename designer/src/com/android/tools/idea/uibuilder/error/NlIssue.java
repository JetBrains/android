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

import com.android.tools.idea.rendering.HtmlBuilderHelper;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.utils.HtmlBuilder;
import com.android.utils.Pair;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.PsiEditorUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;
import java.util.List;
import java.util.stream.Stream;

import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static java.util.Arrays.stream;

/**
 * Represent an Error that can be displayed in the {@link IssuePanel}.
 *
 * <p>
 * This class also provide wrapper classes for {@link RenderErrorModel.Issue} and {@link LintAnnotationsModel.IssueData}
 * so both kind of issue can be used the same way.
 */
public abstract class NlIssue {

  public static final String EXECUTE_FIX = "Execute Fix: ";

  /**
   * @return A short summary of the error description
   */
  @NotNull
  public abstract String getSummary();

  /**
   * @return The description of the error. It can contains some HTML tag
   */
  @NotNull
  public abstract String getDescription();

  @NotNull
  public abstract HighlightSeverity getSeverity();

  /**
   * @return An indication of the origin of the error like the Component causing the error
   */
  @Nullable
  public abstract NlComponent getSource();

  /**
   * @return The priority between 1 and 10.
   */
  public abstract String getCategory();

  /**
   * Allows the {@link NlIssue} to return an HyperlinkListener to handle embedded links
   */
  @Nullable
  public HyperlinkListener getHyperlinkListener() {
    return null;
  }

  /**
   * @return a Steam of pair containing the description of the fix as the first element
   * and a {@link Runnable} to execute the fix
   */
  @NotNull
  public Stream<Pair<String, Runnable>> getFixes() {
    return Stream.empty();
  }

  /**
   * Create a new {@link NlIssue} wrapping a {@link RenderErrorModel.Issue}
   *
   * @param renderIssue The issue to wrap
   * @return the newly created {@link NlIssue}
   */
  @NotNull
  public static NlIssue wrapIssue(@NotNull RenderErrorModel.Issue renderIssue) {
    return new NlRenderIssueWrapper(renderIssue);
  }

  /**
   * Create a new {@link NlIssue} wrapping a {@link LintAnnotationsModel.IssueData}
   *
   * @param lintIssue The issue to wrap
   * @return the newly created {@link NlIssue}
   */
  @NotNull
  public static NlIssue wrapIssue(@NotNull LintAnnotationsModel.IssueData lintIssue) {
    return new NlLintIssueWrapper(lintIssue);
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (!(object instanceof NlIssue)) return false;
    NlIssue nlIssue = (NlIssue)object;

    return nlIssue.getSeverity().equals(getSeverity())
           && nlIssue.getSummary().equals(getSummary())
           && nlIssue.getDescription().equals(getDescription())
           && nlIssue.getCategory().equals(getCategory())
           && nlIssue.getSource() == getSource();
  }

  @Override
  public int hashCode() {
    int result = 13;
    result += 17 * getSeverity().hashCode();
    result += 19 * getSummary().hashCode();
    result += 23 * getDescription().hashCode();
    result += 29 * getCategory().hashCode();
    NlComponent source = getSource();
    if (source != null) {
      result += 31 * source.hashCode();
    }
    return result;
  }

  /**
   * Wrapper class to use a {@link RenderErrorModel.Issue} as an {@link NlIssue}
   */
  private static class NlRenderIssueWrapper extends NlIssue {

    private final RenderErrorModel.Issue myIssue;

    private NlRenderIssueWrapper(@NotNull RenderErrorModel.Issue issue) {
      myIssue = issue;
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

  private static class NlLintIssueWrapper extends NlIssue {

    private final LintAnnotationsModel.IssueData myIssue;

    private String myDescription;

    private NlLintIssueWrapper(@NotNull LintAnnotationsModel.IssueData issue) {
      myIssue = issue;
    }

    private String createFullDescription() {
      Issue issue = myIssue.issue;
      String headerFontColor = HtmlBuilderHelper.getHeaderFontColor();
      HtmlBuilder builder = new HtmlBuilder();

      builder.addHtml(TextFormat.RAW.convertTo(myIssue.message, HTML));
      builder.newline().newline();
      builder.addHtml(issue.getExplanation(HTML));
      builder.newline();
      List<String> moreInfo = issue.getMoreInfo();
      int count = moreInfo.size();
      if (count > 1) {
        builder.addHeading("More Info: ", headerFontColor);
        builder.beginList();
      }
      for (String uri : moreInfo) {
        if (count > 1) {
          builder.listItem();
        }
        builder.addLink(uri, uri);
      }
      if (count > 1) {
        builder.endList();
      }
      builder.newline();

      return builder.getHtml();
    }

    @NotNull
    @Override
    public String getSummary() {
      return myIssue.issue.getBriefDescription(TextFormat.RAW);
    }

    @NotNull
    @Override
    public String getDescription() {
      if (myDescription == null) {
        myDescription = createFullDescription();
      }
      return myDescription;
    }

    @NotNull
    @Override
    public HighlightSeverity getSeverity() {
      return myIssue.level.getSeverity();
    }

    @NotNull
    @Override
    public NlComponent getSource() {
      return myIssue.component;
    }

    @Override
    public String getCategory() {
      return myIssue.issue.getCategory().getFullName();
    }

    @NotNull
    @Override
    public Stream<Pair<String, Runnable>> getFixes() {
      AndroidLintInspectionBase inspection = myIssue.inspection;
      AndroidLintQuickFix[] quickFixes = inspection.getQuickFixes(myIssue.startElement, myIssue.endElement,
                                                                  myIssue.message, myIssue.quickfixData);
      IntentionAction[] intentions = inspection.getIntentions(myIssue.startElement, myIssue.endElement);
      return Stream.concat(
        stream(quickFixes).map(this::createQuickFixPair),
        stream(intentions).map(this::createQuickFixPair));
    }

    @NotNull
    private Pair<String, Runnable> createQuickFixPair(@NotNull AndroidLintQuickFix fix) {
      return Pair.of(fix.getName(), createQuickFixRunnable(fix));
    }

    @NotNull
    private Pair<String, Runnable> createQuickFixPair(@NotNull IntentionAction fix) {
      return Pair.of(fix.getText(), createQuickFixRunnable(fix));
    }

    @NotNull
    private Runnable createQuickFixRunnable(@NotNull AndroidLintQuickFix fix) {
      return () -> {
        NlModel model = myIssue.component.getModel();
        Editor editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(myIssue.startElement);
        if (editor != null) {
          Project project = model.getProject();
          CommandProcessor.getInstance().executeCommand(
            project,
            () -> fix.apply(myIssue.startElement, myIssue.endElement, AndroidQuickfixContexts.BatchContext.getInstance()),
            EXECUTE_FIX + fix.getName(),
            null);
        }
      };
    }

    @NotNull
    private Runnable createQuickFixRunnable(@NotNull IntentionAction fix) {
      return () -> {
        NlModel model = myIssue.component.getModel();
        Editor editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(myIssue.startElement);
        if (editor != null) {
          Project project = model.getProject();
          CommandProcessor.getInstance().executeCommand(
            project,
            () -> fix.invoke(project, editor, model.getFile()),
            EXECUTE_FIX + fix.getFamilyName(),
            null);
        }
      };
    }
  }
}
