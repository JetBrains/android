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
package com.android.tools.idea.profilers.stacktrace;

import com.android.tools.idea.actions.PsiClassNavigation;
import com.android.tools.profilers.common.CodeLocation;
import com.android.tools.profilers.common.CodeNavigator;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link CodeNavigator} with logic to jump to code inside of an IntelliJ code editor.
 */
public final class IntellijCodeNavigator extends CodeNavigator {
  private final Project myProject;

  public IntellijCodeNavigator(@NotNull Project project) {
    myProject = project;
  }

  @Override
  protected void handleNavigate(@NotNull CodeLocation location) {
    Navigatable[] navigatables;
    if (location.getLineNumber() > 0) {
      navigatables = PsiClassNavigation.getNavigationForClass(myProject, location.getClassName(), location.getLineNumber());
    }
    else {
      navigatables = PsiClassNavigation.getNavigationForClass(myProject, location.getClassName());
    }
    if (navigatables == null) {
      return;
    }
    for (Navigatable navigatable : navigatables) {
      // If multiple navigatables, intentionally navigate to all of them, as this will have the
      // effect of opening up editors for each one.
      if (navigatable.canNavigate()) {
        navigatable.navigate(false);
      }
    }
  }
}
