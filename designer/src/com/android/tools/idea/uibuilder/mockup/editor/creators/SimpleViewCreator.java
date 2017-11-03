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

import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupCoordinate;
import com.android.tools.idea.uibuilder.mockup.colorextractor.ColorExtractor;
import com.android.tools.idea.uibuilder.mockup.colorextractor.DBSCANColorExtractor;
import com.android.tools.idea.uibuilder.mockup.colorextractor.ExtractedColor;
import com.android.tools.idea.uibuilder.mockup.editor.creators.forms.ViewAndColorForm;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

import static com.android.SdkConstants.VIEW;

/**
 * Create a simple {@value com.android.SdkConstants#VIEW} tag with the size, mockup, and tools position attributes
 */
public class SimpleViewCreator extends WidgetCreator {

  @MockupCoordinate private final Rectangle mySelectionBounds;
  @AndroidCoordinate Rectangle myAndroidBounds = new Rectangle();

  /**
   * Create a simple {@value com.android.SdkConstants#VIEW} tag
   * with the size, mockup, and tools position attributes
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link NlDesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates.
   * @param selection  The selection made in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  public SimpleViewCreator(@NotNull Mockup mockup, @NotNull NlModel model, @NotNull ScreenView screenView, @NotNull Rectangle selection) {
    super(mockup, model, screenView);
    mySelectionBounds = selection;

    Rectangle cropping = getMockup().getComputedCropping();
    final NlComponent component = getMockup().getComponent();
    final float xScale = NlComponentHelperKt.getW(component) / (float)cropping.width;
    final float yScale = NlComponentHelperKt.getH(component) / (float)cropping.height;
    myAndroidBounds.setBounds(Math.round(xScale * mySelectionBounds.x),
                              Math.round(yScale * mySelectionBounds.y),
                              Math.round(xScale * mySelectionBounds.width),
                              Math.round(yScale * mySelectionBounds.height));
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String getAndroidViewTag() {
    return VIEW;
  }

  /**
   * Find the color in the provided image and return them in the provided callback.
   * The callback is called in a separate Thread
   *
   * @param image    The image to extract the color from
   * @param callback The callback to get the result of the color extraction
   */
  protected void extractColor(@NotNull BufferedImage image, @NotNull ColorExtractor.ColorExtractorCallback callback) {
    final Rectangle realCropping = getMockup().getComputedCropping();
    final Rectangle selectionBounds = getSelectionBounds();
    final BufferedImage subimage = image.getSubimage(realCropping.x + selectionBounds.x,
                                                     realCropping.y + selectionBounds.y,
                                                     selectionBounds.width,
                                                     selectionBounds.height);

    ColorExtractor colorExtractor = new DBSCANColorExtractor(subimage, DBSCANColorExtractor.DEFAULT_EPS,
                                                             DBSCANColorExtractor.getMinClusterSize(subimage));
    colorExtractor.run(callback);
  }

  protected void extractColor(final ViewAndColorForm viewAndColorForm, BufferedImage image) {
    extractColor(image, new ColorExtractor.ColorExtractorCallback() {
      @Override
      public void result(Collection<ExtractedColor> rgbColors) {
        viewAndColorForm.addColors(rgbColors);
      }

      @Override
      public void progress(int progress) {
        viewAndColorForm.setProgress(progress);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
    addLayoutEditorPositionAttribute(transaction, myAndroidBounds);
    addSizeAttributes(transaction, myAndroidBounds);
    addMockupAttributes(transaction, mySelectionBounds);
  }

  /**
   * {@inheritDoc}
   */
  @AndroidCoordinate
  public Rectangle getAndroidBounds() {
    return myAndroidBounds;
  }

  /**
   * {@inheritDoc}
   */
  @MockupCoordinate
  public Rectangle getSelectionBounds() {
    return mySelectionBounds;
  }
}
