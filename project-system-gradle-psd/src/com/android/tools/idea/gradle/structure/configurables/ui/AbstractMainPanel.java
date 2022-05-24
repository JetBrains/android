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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.structure.configurables.ui.CrossModuleUiStateComponent;
import com.intellij.openapi.Disposable;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractMainPanel extends JPanel implements Disposable, Place.Navigator, CrossModuleUiStateComponent {
  @NotNull private final PsProject myProject;
  @NotNull private final PsContext myContext;

  private History myHistory;

  protected AbstractMainPanel(@NotNull PsContext context) {
    super(new BorderLayout());
    myProject = context.getProject();
    myContext = context;
  }

  @NotNull
  protected PsProject getProject() {
    return myProject;
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }

  @Override
  public void setHistory(History history) {
    myHistory = history;
  }

  @Nullable
  protected History getHistory() {
    return myHistory;
  }
}
