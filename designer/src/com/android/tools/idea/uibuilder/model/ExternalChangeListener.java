/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.annotations.NonNull;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.annotations.NotNull;

/** Listener for editing events which causes the model to re-render */
public class ExternalChangeListener extends PsiTreeChangeAdapter {
  private final Project myProject;
  private final NlModel myModel;

  public ExternalChangeListener(@NonNull NlModel model) {
    myModel = model;
    myProject = model.getFile().getProject();
  }

  public void activate() {
    PsiManager manager = PsiManager.getInstance(myProject);
    manager.removePsiTreeChangeListener(this); // prevent duplicate registrations
    manager.addPsiTreeChangeListener(this);
  }

  public void deactivate() {
    if (!myProject.isDisposed()) {
      PsiManager.getInstance(myProject).removePsiTreeChangeListener(this);
    }
  }

  protected void updatePsi(PsiTreeChangeEvent event) {
    if (myModel.getFile() == event.getFile()) {
      myModel.requestRender();
    }
  }

  // ---- implements PsiTreeChangeListener ----

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }
}
