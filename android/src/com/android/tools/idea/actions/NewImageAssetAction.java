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
import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsModel;
import com.android.tools.idea.npw.assetstudio.wizard.NewImageAssetStep;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Action to invoke the Image Asset Studio. This will allow the user to generate icons using source
 * assets. See also {@link BaseAsset}.
 */
public class NewImageAssetAction extends AndroidAssetStudioAction {

  private static final Dimension WIZARD_SIZE = new Dimension(800, 750);

  public NewImageAssetAction() {
    super("Image Asset", "Open Asset Studio to create an image asset");
  }

  // TODO: Remove unused "targetFile" param when NewVectorAsset wizard is migrated.
  // (The old wizard currently uses this for buggy "does this asset already exist?" logic which is
  // done in a more robust manner with the new wizard.)
  @Override
  protected void showWizardAndCreateAsset(@NotNull AndroidFacet facet, @Nullable VirtualFile targetFile) {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    wizardBuilder.addStep(new NewImageAssetStep(new GenerateIconsModel(facet)));

    // TODO: Move some of this logic up to AndroidAssetStudioAction when NewVectorAsset wizard is
    // migrated.
    StudioWizardDialogBuilder dialogBuilder = new StudioWizardDialogBuilder(wizardBuilder.build(), "Generate Icons");
    dialogBuilder.setProject(facet.getModule().getProject()).setMinimumSize(WIZARD_SIZE);

    dialogBuilder.build().show();
  }
}
