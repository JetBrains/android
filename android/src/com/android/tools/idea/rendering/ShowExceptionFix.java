/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.rendering;

import com.intellij.openapi.project.Project;
import java.lang.ref.WeakReference;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowExceptionFix implements Runnable {
  @Nullable private final Project myProject;
  @NotNull private final WeakReference<RenderProblem> myRenderProblemWeakReference;
  @Nullable private final Throwable myThrowable;

  /**
   * Creates a {@link ShowExceptionFix} holding the given {@link Throwable}. Avoid using this constructor
   * if you have a {@link RenderProblem} available.
   */
  public ShowExceptionFix(@Nullable Project project, @NotNull Throwable throwable) {
    myProject = project;
    myThrowable = throwable;
    myRenderProblemWeakReference = new WeakReference<>(null);
  }

  /**
   * Use this constructor if you have a {@link RenderProblem} available. This constructor will
   * avoid holding a strong link to the {@link RenderProblem} and will avoid consuming extra memory if the problem is disposed.
   */
  public ShowExceptionFix(@Nullable Project project, @NotNull RenderProblem problem) {
    myProject = project;
    myThrowable = null;
    myRenderProblemWeakReference = new WeakReference<>(problem);
  }

  @Override
  public void run() {
    Throwable t = myThrowable;

    if (t == null) {
      RenderProblem problem = myRenderProblemWeakReference.get();
      t = problem != null ? problem.getThrowable() : null;
    }

    if (t == null) {
      return;
    }
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    AndroidUtils.showStackStace(myProject, new Throwable[]{t});
  }
}
