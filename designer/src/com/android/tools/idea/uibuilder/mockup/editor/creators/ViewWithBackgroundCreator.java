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
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Create a new simple View and show a popup to set the background of the view
 */
public class ViewWithBackgroundCreator extends SimpleViewCreator {
  protected ColorResourceHolder myColor;

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
  public ViewWithBackgroundCreator(@NotNull Mockup mockup,
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
    ViewAndColorForm viewAndColorForm =
      new ViewAndColorForm("Extract Background color", createColorSelectedListener(doneCallback));
    final BufferedImage image = getMockup().getImage();
    if (image == null) {
      return null;
    }
    extractColor(viewAndColorForm, image);
    return viewAndColorForm.getComponent();
  }

  /**
   * Create a listener that will call the provided done callback after setting the selected color
   *
   * @param doneCallback The callback provided by {@link WidgetCreator}
   * @return The {@link ViewAndColorForm.ColorSelectedListener} that will be called by the {@link ViewAndColorForm}
   */
  @NotNull
  protected ViewAndColorForm.ColorSelectedListener createColorSelectedListener(@NotNull DoneCallback doneCallback) {
    return colorHolder -> {
      myColor = colorHolder;
      if (myColor != null) {
        if (myColor.name != null && !myColor.name.isEmpty() && myColor.value != null) {
          createColorResource(myColor.name, myColor.value, getModel());
        }
        doneCallback.done(DoneCallback.FINISH);
      }
      else {
        doneCallback.done(DoneCallback.CANCEL);
      }
    };
  }

  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
    super.addAttributes(transaction);
    if (myColor != null && myColor.value != null) {
      if (myColor.name != null && !myColor.name.isEmpty()) {
        transaction
          .setAttribute(null, SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_BACKGROUND,
                        SdkConstants.COLOR_RESOURCE_PREFIX + myColor.name);
      }
      else {
        transaction
          .setAttribute(null, SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_BACKGROUND,
                        String.format("#%06X", myColor.value.getRGB()));
      }
    }
  }
}
