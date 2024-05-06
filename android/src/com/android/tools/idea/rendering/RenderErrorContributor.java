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
import com.android.tools.rendering.RenderResult;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RenderErrorContributor {
  // These priorities can be used to promote certain issues to the top of the list
  int HIGH_PRIORITY = 100;
  @SuppressWarnings("unused") int MEDIUM_PRIORITY = 10;
  @SuppressWarnings("unused") int LOW_PRIORITY = 10;

  Collection<RenderErrorModel.Issue> reportIssues();

  interface Provider {
    ExtensionPointName<Provider> EP_NAME =
      new ExtensionPointName<>("com.android.rendering.renderErrorContributor");

    boolean isApplicable(Project project);
    RenderErrorContributor getContributor(@Nullable EditorDesignSurface surface, @NotNull RenderResult result);
  }
}
