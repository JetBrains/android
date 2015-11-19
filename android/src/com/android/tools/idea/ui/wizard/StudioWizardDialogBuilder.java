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
package com.android.tools.idea.ui.wizard;

import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Convenience class for building a {@link ModelWizard} styled for Android Studio.
 */
public final class StudioWizardDialogBuilder {
  private static final Dimension MINIMUM_SIZE = JBUI.size(1080, 650);

  @NotNull ModelWizard myWizard;
  @NotNull String myTitle;
  @Nullable Component myParent;
  @Nullable Project myProject;
  @NotNull DialogWrapper.IdeModalityType myModalityType = DialogWrapper.IdeModalityType.IDE;

  public StudioWizardDialogBuilder(@NotNull ModelWizard wizard, @NotNull String title) {
    myWizard = wizard;
    myTitle = title;
  }

  /**
   * Build a wizard with a parent component it should always show in front of. If you use this
   * constructor, any calls to {@link #setProject(Project)} and
   * {@link #setModalityType(DialogWrapper.IdeModalityType)} will be ignored.
   */
  public StudioWizardDialogBuilder(@NotNull ModelWizard wizard, @NotNull String title, @NotNull Component parent) {
    this(wizard, title);
    myParent = parent;
  }

  public StudioWizardDialogBuilder setProject(@Nullable Project project) {
    if (project != null) {
      myProject = project;
    }
    return this;
  }

  public StudioWizardDialogBuilder setModalityType(@Nullable DialogWrapper.IdeModalityType modalityType) {
    if (modalityType != null) {
      myModalityType = modalityType;
    }
    return this;
  }

  public ModelWizardDialog build() {
    StudioWizardLayout customLayout = new StudioWizardLayout();
    ModelWizardDialog dialog;
    if (myParent != null) {
      dialog = new ModelWizardDialog(myWizard, myTitle, myParent, customLayout);
    }
    else {
      dialog = new ModelWizardDialog(myWizard, myTitle, customLayout, myProject, myModalityType);
    }

    dialog.setSize(MINIMUM_SIZE.width, MINIMUM_SIZE.height);
    return dialog;
  }
}
