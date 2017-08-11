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
import com.android.tools.idea.uibuilder.mockup.backgroundremove.RemoveBackgroundPanel;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.mockup.editor.creators.forms.ExtractBackgroundForm;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.mockup.editor.creators.ResourcesUtil.checkDrawableExist;
import static com.android.tools.idea.uibuilder.mockup.editor.creators.ResourcesUtil.createDrawable;

/**
 * Create an ImageView and displays option to export the selection as a drawable
 */
public class ImageViewCreator extends SimpleViewCreator {

  public static final Logger LOGGER = Logger.getInstance(ImageViewCreator.class);
  public static final String DRAWABLE_TYPE = "png";

  private final Rectangle mySelection;
  private RemoveBackgroundPanel myRemoveBackgroundPanel;
  private ExtractBackgroundForm myExtractBackgroundForm;
  @Nullable private String myDrawableName;


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
  public ImageViewCreator(@NotNull Mockup mockup, @NotNull NlModel model, @NotNull ScreenView screenView, @NotNull Rectangle selection) {
    super(mockup, model, screenView, selection);
    mySelection = selection;
    myRemoveBackgroundPanel = new RemoveBackgroundPanel();
    myExtractBackgroundForm = new ExtractBackgroundForm(myRemoveBackgroundPanel);
  }

  @NotNull
  @Override
  public String getAndroidViewTag() {
    return IMAGE_VIEW;
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
      myDrawableName = myExtractBackgroundForm.getDrawableName();
      if(checkDrawableExist(myDrawableName, DRAWABLE_TYPE, getModel().getFacet())) {
        myExtractBackgroundForm.setErrorText(String.format(
          "A drawable resource named <i>%s</i> already exists and will be overwritten.", myDrawableName));
        return;
      }
      createDrawable(myDrawableName, DRAWABLE_TYPE,
                     doneCallback, getModel(), extractedImage, this);

    });

    Rectangle realCropping = mockup.getComputedCropping();
    BufferedImage subimage = image.getSubimage(realCropping.x + mySelection.x,
                                               realCropping.y + mySelection.y,
                                               mySelection.width,
                                               mySelection.height);
    myRemoveBackgroundPanel.setImage(subimage);
    myRemoveBackgroundPanel.setPreferredSize(new Dimension(300, 300));
    return myExtractBackgroundForm.getComponent();
  }

  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
    super.addAttributes(transaction);
    if (getDrawableName() != null) {
      transaction.setAttribute(null, ANDROID_NS_NAME_PREFIX + ATTR_SRC, DRAWABLE_PREFIX + getDrawableName());
    }
  }

  @Nullable
  public String getDrawableName() {
    return myDrawableName;
  }
}
