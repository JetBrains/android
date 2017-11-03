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
package com.android.tools.idea.uibuilder.mockup.editor.creators;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.mockup.editor.creators.forms.ViewAndColorForm;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Creator that displays a popup with a Auto completion field to choose the tag name of the created view
 * and extract a background color
 */
public class AutoCompleteViewCreator extends ViewWithBackgroundCreator {
  private String myViewTag = SdkConstants.VIEW;

  /**
   * Create a simple {@value SdkConstants#VIEW} tag
   * with the size, mockup, and tools position attributes
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link NlDesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates.
   * @param selection  The selection made in the {@link MockupEditor}
   */
  public AutoCompleteViewCreator(@NotNull Mockup mockup,
                                 @NotNull NlModel model,
                                 @NotNull ScreenView screenView,
                                 @NotNull Rectangle selection) {
    super(mockup, model, screenView, selection);
  }

  @Override
  public boolean hasOptionsComponent() {
    return true;
  }

  @Nullable
  @Override
  public JComponent getOptionsComponent(@NotNull DoneCallback doneCallback) {
    ViewAndColorForm autoCompleteForm = new ViewAndColorForm(
      "Create a new view", createColorSelectedListener(doneCallback), getModel().getFacet());
    final BufferedImage image = getMockup().getImage();
    if (image == null) {
      return null;
    }
    extractColor(autoCompleteForm, image);
    autoCompleteForm.setAddListener(e -> {
      myViewTag = autoCompleteForm.getTagName();
    });
    return autoCompleteForm.getComponent();
  }

  @NotNull
  @Override
  public String getAndroidViewTag() {
    return myViewTag;
  }
}
