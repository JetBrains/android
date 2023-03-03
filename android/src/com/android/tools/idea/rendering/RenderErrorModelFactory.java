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
package com.android.tools.idea.rendering;

import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that produces a {@link RenderErrorModel} containing the issues found in a {@link RenderResult}.
 */
public class RenderErrorModelFactory {
  private RenderErrorModelFactory() {
  }

  /**
   * Returns a new {@RenderErrorModel} with the {@link RenderErrorModel.Issue}s found in the passed {@link RenderResult}.
   */
  @NotNull
  public static RenderErrorModel createErrorModel(@Nullable EditorDesignSurface surface, @NotNull RenderResult result, @Nullable DataContext dataContext) {
    List<RenderErrorModel.Issue> issues = new ArrayList<>();
    for (RenderErrorContributor.Provider provider : RenderErrorContributor.Provider.EP_NAME.getExtensions()) {
      if (provider.isApplicable(result.getProject())) {
        issues.addAll(provider.getContributor(surface, result, dataContext).reportIssues());
      }
    }
    return new RenderErrorModel(issues);
  }
}
