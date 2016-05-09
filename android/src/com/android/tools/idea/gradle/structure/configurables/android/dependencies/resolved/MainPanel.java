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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

class MainPanel extends AbstractMainPanel {
  @NotNull private final ResolvedDependenciesPanel myDependenciesPanel;

  MainPanel(@NotNull PsAndroidModule module, @NotNull PsContext context) {
    super(context);

    myDependenciesPanel = new ResolvedDependenciesPanel(module, context);
    add(myDependenciesPanel, BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myDependenciesPanel);
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    return null;
  }

  @Override
  public void queryPlace(@NotNull Place place) {

  }
}
