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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Layer build to be on top of the BluePrint ScreenView displaying
 * an optional mockup of the layout to be build
 *
 * @See MockupModel
 **/
public class MockupLayer extends Layer {

  private final ScreenView myScreenView;
  private Dimension myScreenViewSize = new Dimension();
  @Nullable private NlModel myNlModel;
  private List<Mockup> myMockups;

  public MockupLayer(ScreenView screenView) {
    assert screenView != null;
    myScreenView = screenView;
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);
    setNlModel(myScreenView.getModel());
    myMockups = Mockup.createAll(myNlModel);
  }

  public void setNlModel(@Nullable NlModel nlModel) {
    if (nlModel != null && nlModel != myNlModel) {
      nlModel.addListener(new ModelListener() {
        @Override
        public void modelDerivedDataChanged(@NotNull NlModel model) {
          myMockups = Mockup.createAll(myNlModel);
        }

        @Override
        public void modelRendered(@NotNull NlModel model) {
        }

        @Override
        public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
          // Do nothing
        }
      });
    }
    myNlModel = nlModel;
  }

  @Override
  public void paint(@NotNull Graphics2D g) {
    if (!((NlDesignSurface)myScreenView.getSurface()).isMockupVisible() || myMockups.isEmpty()) {
      return;
    }
    final Composite composite = g.getComposite();
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);

    for (int i = 0; i < myMockups.size(); i++) {
      final Mockup mockup = myMockups.get(i);
      paintMockup(g, mockup);
    }
    g.setComposite(composite);
  }

  private void paintMockup(@NotNull Graphics2D g, Mockup mockup) {
    final BufferedImage image = mockup.getImage();
    if (image != null) {
      final Rectangle dest = mockup.getScreenBounds(myScreenView);
      final Rectangle src = mockup.getComputedCropping();

      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, mockup.getAlpha()));
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image,
                  dest.x, dest.y, dest.x + dest.width, dest.y + dest.height,
                  src.x, src.y, src.x + src.width, src.y + src.height,
                  null);
    }
  }
}