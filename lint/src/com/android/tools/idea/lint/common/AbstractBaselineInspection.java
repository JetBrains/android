/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.application.ApplicationManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;

public class AbstractBaselineInspection extends AndroidLintInspectionBase {
  public AbstractBaselineInspection(@NotNull String displayName, @NotNull Issue issue) {
    super(displayName, issue);
  }

  protected static void rerun() {

    // Attempt to re-run tne analysis. This isn't a service we can access programmatically unless we
    // have the inspections view itself. We could attempt to hold on to the GlobalInspectionContextImpl
    // after each lint run, but that object points to a huge amount of state and I don't want to risk
    // leaking it.
    //
    // Therefore, instead we fish around the JComponent hierarchy and look for the window itself;
    // from there we can invoke it. There's one wrinkle: we need to persist the analysis scope. Luckily
    // we only need to do this very temporarily (from the rerun action to the LintGlobalInspectionContext
    // processes it.)
    ThreadingAssertions.assertEventDispatchThread();
    ApplicationManager.getApplication().invokeLater(
      () -> {
        ApplicationManager.getApplication().assertIsDispatchThread();
        ApplicationManager.getApplication().runWriteAction(() -> {
          for (Frame frame : Frame.getFrames()) {
            InspectionResultsView view = findInspectionView(frame);
            if (view != null && view.isRerunAvailable() && !view.isDisposed()) {
              view.rerun();
            }
          }
        });
      }
    );
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

  public static void clearNextRunState() {
    ourSkipBaselineNextRun = false;
    ourUpdateBaselineNextRun = false;
  }

  @Nullable
  private static InspectionResultsView findInspectionView(@NonNull Container container) {
    for (int i = 0, n = container.getComponentCount(); i < n; i++) {
      Component component = container.getComponent(i);
      if (component instanceof InspectionResultsView) {
        return ((InspectionResultsView)component);
      }
      else if (component instanceof Container) {
        InspectionResultsView view = findInspectionView((Container)component);
        if (view != null) {
          return view;
        }
      }
    }
    return null;
  }
}
