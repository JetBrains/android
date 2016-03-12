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
package com.android.tools.idea.actions;

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.wizard.GenerateImageIconsModel;
import com.android.tools.idea.npw.assetstudio.wizard.NewImageAssetStep;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;

/**
 * Action to invoke the Image Asset Studio. This will allow the user to generate icons using source
 * assets. See also {@link BaseAsset}.
 */
public class NewImageAssetAction extends AndroidAssetStudioAction {

  public NewImageAssetAction() {
    super("Image Asset", "Open Asset Studio to create an image asset");
  }

  @NotNull
  @Override
  protected ModelWizard createWizard(@NotNull AndroidFacet facet) {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new NewImageAssetStep(new GenerateImageIconsModel(facet)));

    return wizardBuilder.build();
  }

  @NotNull
  @Override
  protected Dimension getWizardSize() {
    return new Dimension(800, 750);
  }

  @Nullable
  @Override
  protected URL getHelpUrl() {
    return WizardUtils.toUrl("https://developer.android.com/r/studio-ui/image-asset-studio.html");
  }
}
