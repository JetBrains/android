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

import com.android.tools.environment.Logger;
import com.android.tools.rendering.HtmlLinkManager;
import com.android.tools.rendering.RenderProblem;
import com.intellij.openapi.module.Module;
import java.lang.ref.WeakReference;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowExceptionFix implements HtmlLinkManager.Action {
  @NotNull private final WeakReference<RenderProblem> myRenderProblemWeakReference;
  @Nullable private final Throwable myThrowable;

  /**
   * Creates a {@link ShowExceptionFix} holding the given {@link Throwable}. Avoid using this constructor
   * if you have a {@link RenderProblem} available.
   */
  public ShowExceptionFix(@NotNull Throwable throwable) {
    myThrowable = throwable;
    myRenderProblemWeakReference = new WeakReference<>(null);
  }

  /**
   * Use this constructor if you have a {@link RenderProblem} available. This constructor will
   * avoid holding a strong link to the {@link RenderProblem} and will avoid consuming extra memory if the problem is disposed.
   */
  public ShowExceptionFix(@NotNull RenderProblem problem) {
    myThrowable = null;
    myRenderProblemWeakReference = new WeakReference<>(problem);
  }

  @Override
  public void actionPerformed(@Nullable Module module) {
    if (module == null) {
      Logger.getInstance(ShowExceptionFix.class).warn("Module has been disposed");
      return;
    }
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
    AndroidUtils.showStackStace(module.getProject(), new Throwable[]{t});
  }
}
