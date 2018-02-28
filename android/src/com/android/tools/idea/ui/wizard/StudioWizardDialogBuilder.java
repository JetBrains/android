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
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;

/**
 * Convenience class for building a {@link ModelWizard} styled for Android Studio.
 */
public final class StudioWizardDialogBuilder {

  /**
   * The minimum (and initial) size of a dialog should be no bigger than the user's screen (or,
   * a percentage of the user's screen, to leave a bit of space on the sides). This prevents
   * developers from specifying a size that looks good on their monitor but won't fit on a low
   * resolution screen. Worst case, the UI may end up squished for some users, but the
   * prev/next/cancel buttons will always be visible.
   */
  private static final float SCREEN_PERCENT = 0.8f;

  /**
   * Temporary toggle as we migrate wizards to a new design
   *
   * TODO: remove this once migration to the new design is complete
   */
  public enum UxStyle {
    /**
     * Has a title bar with Android Studio icon on the Left, a main text header in large font for the Step title,
     * a "Android Studio" sub header bellow main header, and an optional Step Icon on the right.
     */
    ORIGINAL,
    /**
     * Similar to ORIGINAL, without the "Android Studio" sub header and a slightly larger Dialog.
     */
    INSTANT_APP,
    /**
     * Has a simpler title bar with only the main text header, in large font, for the Step title.
     */
    DYNAMIC_APP
  }
  @NotNull private UxStyle myUxStyle = UxStyle.ORIGINAL;

  @NotNull ModelWizard myWizard;
  @NotNull String myTitle;
  @Nullable Component myParent;
  @Nullable Project myProject;
  @Nullable URL myHelpUrl;
  @NotNull DialogWrapper.IdeModalityType myModalityType = DialogWrapper.IdeModalityType.IDE;
  @Nullable Dimension myMinimumSize;
  @Nullable Dimension myPreferredSize;
  @NotNull ModelWizardDialog.CancellationPolicy myCancellationPolicy = ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL;

  public StudioWizardDialogBuilder(@NotNull ModelWizard wizard, @NotNull String title) {
    myWizard = wizard;
    myTitle = title;
  }

  /**
   * Convenience construction for wizards that only have a single step in them.
   */
  public StudioWizardDialogBuilder(@NotNull ModelWizardStep step, @NotNull String title) {
    this(new ModelWizard.Builder(step).build(), title);
  }

  /**
   * Build a wizard with a parent component it should always show in front of. If you use this
   * constructor, any calls to {@link #setProject(Project)} and
   * {@link #setModalityType(DialogWrapper.IdeModalityType)} will be ignored.
   */
  public StudioWizardDialogBuilder(@NotNull ModelWizard wizard, @NotNull String title, @Nullable Component parent) {
    this(wizard, title);
    myParent = parent;
  }

  /**
   * Set the target project that this dialog is associated with.
   *
   * If {@code null}, this call will be ignored, although it is allowed as an argument to work well
   * with {@code Nullable} APIs.
   */
  @NotNull
  public StudioWizardDialogBuilder setProject(@Nullable Project project) {
    if (project != null) {
      myProject = project;
    }
    return this;
  }

  /**
   * Override the modality type of this dialog.
   *
   * If {@code null}, this call will be ignored, although it is allowed as an argument to work well
   * with {@code Nullable} APIs.
   */
  @NotNull
  public StudioWizardDialogBuilder setModalityType(@Nullable DialogWrapper.IdeModalityType modalityType) {
    if (modalityType != null) {
      myModalityType = modalityType;
    }
    return this;
  }

  /**
   * Override the minimum size of this dialog.
   *
   * If {@code null}, this call will be ignored, although it is allowed as an argument to work well
   * with {@code Nullable} APIs.
   */
  @NotNull
  public StudioWizardDialogBuilder setMinimumSize(@Nullable Dimension minimumSize) {
    if (minimumSize != null) {
      myMinimumSize = minimumSize;
    }
    return this;
  }

  /**
   * Override the preferred size of this dialog.
   *
   * If {@code null}, this call will be ignored, although it is allowed as an argument to work well
   * with {@code Nullable} APIs.
   */
  @NotNull
  public StudioWizardDialogBuilder setPreferredSize(@Nullable Dimension preferredSize) {
    if (preferredSize != null) {
      myPreferredSize = preferredSize;
    }
    return this;
  }

  /**
   * Set a help link that the dialog's help button should browse to.
   *
   * If {@code null}, this call will be ignored, although it is allowed as an argument to work well
   * with {@code Nullable} APIs.
   */
  @NotNull
  public StudioWizardDialogBuilder setHelpUrl(@Nullable URL helpUrl) {
    if (helpUrl != null) {
      myHelpUrl = helpUrl;
    }
    return this;
  }

  /**
   * Set the dialog cancellation policy to provide more fine-grained user experience
   * by making it clearer when clicking Cancel is likely to incur some actual cancellation action
   *
   * If {@code null}, this call will be ignored, although it is allowed as an argument to work well
   * with {@code Nullable} APIs.
   */
  @NotNull
  public StudioWizardDialogBuilder setCancellationPolicy(@Nullable ModelWizardDialog.CancellationPolicy cancellationPolicy) {
    if (cancellationPolicy != null) {
      myCancellationPolicy = cancellationPolicy;
    }
    return this;
  }

  /**
   * Whether or not to use the new Ux design for this wizard
   *
   * TODO: remove this once migration to the new design is complete
   */
  @NotNull
  public StudioWizardDialogBuilder setUxStyle(UxStyle style) {
    myUxStyle = style;
    return this;
  }

  @NotNull
  public ModelWizardDialog build() {
    ModelWizardDialog.CustomLayout customLayout;
    switch (myUxStyle) {
      case ORIGINAL:
        customLayout = new com.android.tools.idea.ui.wizard.deprecated.StudioWizardLayout();
        break;
      case INSTANT_APP:
        customLayout = new StudioWizardLayout();
        break;
      case DYNAMIC_APP:
        customLayout = new SimpleStudioWizardLayout();
        break;
        default:
          throw new IllegalStateException("Unknown style when attempting to build wizard dialog: " + myUxStyle);
    }

    if (myMinimumSize == null) {
      myMinimumSize = customLayout.getDefaultMinSize();
    }
    if (myPreferredSize == null) {
      myPreferredSize = customLayout.getDefaultPreferredSize();
    }

    ModelWizardDialog dialog;
    if (myParent != null) {
      dialog = new ModelWizardDialog(myWizard, myTitle, myParent, customLayout, myHelpUrl, myCancellationPolicy);
    }
    else {
      dialog = new ModelWizardDialog(myWizard, myTitle, customLayout, myProject, myHelpUrl, myModalityType, myCancellationPolicy);
    }

    dialog.getContentPanel().setMinimumSize(getClampedSize(myMinimumSize));
    dialog.getContentPanel().setPreferredSize(getClampedSize(myPreferredSize));
    return dialog;
  }

  @NotNull
  private static Dimension getClampedSize(Dimension size) {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    return new Dimension(Math.min(size.width, (int)(screenSize.width * SCREEN_PERCENT)),
                         Math.min(size.height, (int)(screenSize.height * SCREEN_PERCENT)));
  }
}
