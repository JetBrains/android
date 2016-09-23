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

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.backgroundremove.RemoveBackgroundPanel;
import com.android.tools.idea.uibuilder.mockup.editor.creators.forms.ExtractBackgroundForm;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 *
 */
public class ExtractIconCreator extends WidgetCreator {


  private final Rectangle mySelection;
  private RemoveBackgroundPanel myRemoveBackgroundPanel;
  private ExtractBackgroundForm myExtractBackgroundForm;

  /**
   * Create a new  View
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link DesignSurface}.
   */
  protected ExtractIconCreator(@NotNull Mockup mockup,
                               @NotNull NlModel model,
                               @NotNull ScreenView screenView,
                               @NotNull Rectangle selection) {
    super(mockup, model, screenView);
    mySelection = selection;
    myRemoveBackgroundPanel = new RemoveBackgroundPanel();
    myExtractBackgroundForm = new ExtractBackgroundForm(myRemoveBackgroundPanel);
  }

  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
  }

  @NotNull
  @Override
  public String getAndroidViewTag() {
    return "";
  }

  @Override
  public boolean hasOptionsComponent() {
    return true;
  }

  @Nullable
  @Override
  public JComponent getOptionsComponent(@NotNull DoneCallback doneCallback) {
    Mockup mockup = getMockup();
    BufferedImage image = mockup.getImage();
    if (image == null) {
      return null;
    }

    myExtractBackgroundForm.setOKListener(actionEvent -> {
      BufferedImage extractedImage = myRemoveBackgroundPanel.getImage();
      if (extractedImage == null) {
        doneCallback.done(DoneCallback.CANCEL);
        return;
      }

      ResourcesUtil.createDrawable(myExtractBackgroundForm.getDrawableName(), "png",
                                   doneCallback, getModel(), extractedImage, this);
    });

    Rectangle realCropping = mockup.getRealCropping();
    BufferedImage subimage = image.getSubimage(realCropping.x + mySelection.x,
                                               realCropping.y + mySelection.y,
                                               mySelection.width,
                                               mySelection.height);
    myRemoveBackgroundPanel.setImage(subimage);
    myRemoveBackgroundPanel.setPreferredSize(new Dimension(300, 300));
    return myExtractBackgroundForm.getComponent();
  }
}
