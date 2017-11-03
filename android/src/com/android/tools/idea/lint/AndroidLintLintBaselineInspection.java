/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.LintBaseline;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.TextFormat;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class AndroidLintLintBaselineInspection extends AndroidLintInspectionBase {
  public AndroidLintLintBaselineInspection() {
    super(AndroidBundle.message("android.lint.inspections.lint.baseline"), IssueRegistry.BASELINE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @org.jetbrains.annotations.Nullable LintFix fixData) {
    if (LintBaseline.isFilteredMessage(message, TextFormat.RAW)) {
      return new AndroidLintQuickFix[]{
        new DefaultLintQuickFix("Temporarily turn off the baseline and re-run the analysis") {
          @Override
          public void apply(@NotNull PsiElement startElement,
                            @NotNull PsiElement endElement,
                            @NotNull AndroidQuickfixContexts.Context context) {
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ourSkipBaselineNextRun = true;
            rerun();
          }
        }
      };
    }

    if (LintBaseline.isFixedMessage(message, TextFormat.RAW)) {
      return new AndroidLintQuickFix[]{
        new DefaultLintQuickFix("Update baseline file to remove fixed issues") {
          @Override
          public void apply(@NotNull PsiElement startElement,
                            @NotNull PsiElement endElement,
                            @NotNull AndroidQuickfixContexts.Context context) {
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            ourUpdateBaselineNextRun = true;
            rerun();
          }
        }
      };
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }

  private static void rerun() {
    // Attempt to re-run tne analysis. This isn't a service we can access programmatically unless we
    // have the inspections view itself. We could attempt to hold on to the GlobalInspectionContextImpl
    // after each lint run, but that object points to a huge amount of state and I don't want to risk
    // leaking it.
    //
    // Therefore, instead we fish around the JComponent hierarchy and look for the window itself;
    // from there we can invoke it. There's one wrinkle: we need to persist the analysis scope. Luckily
    // we only need to do this very temporarily (from the rerun action to the AndroidLintGlobalInspectionContext
    // processes it.)
    ApplicationManager.getApplication().assertIsDispatchThread();
    ApplicationManager.getApplication().runWriteAction(() -> {
      for (Frame frame : Frame.getFrames()) {
        InspectionResultsView view = findInspectionView(frame);
        if (view != null && view.isRerunAvailable() && !view.isDisposed()) {
          ourRerunScope = view.getScope();
          view.rerun();
        }
      }
    });
  }

  /**
   * If true, and baselines are used, skip using the baseline for the next analysis run only.
   * This lets the user temporarily see all the issues without having to delete or move the baseline out of the way.
   */
  public static boolean ourSkipBaselineNextRun;
  /**
   * If true, and baselines are used, update the baseline for the next analysis run. This will cause it
   * to add any newly discovered issues, but more commonly to remove fixed issues.
   */
  public static boolean ourUpdateBaselineNextRun;

  /** Normally null, only set briefly during re-run initialization */
  @Nullable public static AnalysisScope ourRerunScope;

  public static void clearNextRunState() {
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourSkipBaselineNextRun = false;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourUpdateBaselineNextRun = false;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourRerunScope = null;
  }

  @Nullable
  private static InspectionResultsView findInspectionView(@NonNull Container container) {
    for (int i = 0, n = container.getComponentCount(); i < n; i++) {
      Component component = container.getComponent(i);
      if (component instanceof InspectionResultsView) {
        return ((InspectionResultsView)component);
      } else if (component instanceof Container) {
        InspectionResultsView view = findInspectionView((Container)component);
        if (view != null) {
          return view;
        }
      }
    }
    return null;
  }
}