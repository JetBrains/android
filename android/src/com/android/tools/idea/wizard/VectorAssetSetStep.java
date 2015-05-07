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
package com.android.tools.idea.wizard;

import com.android.resources.Density;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.android.tools.idea.wizard.AssetStudioAssetGenerator.*;
import static com.android.tools.idea.wizard.AssetStudioAssetGenerator.ATTR_ASSET_TYPE;
import static com.android.tools.idea.wizard.AssetStudioAssetGenerator.ATTR_IMAGE_PATH;

/**
 * Similar to RasterAssetSetStep, this is particular for vector drawable generation.
 */
public class VectorAssetSetStep extends CommonAssetSetStep {
  private static final Logger LOG = Logger.getInstance(VectorAssetSetStep.class);

  private JPanel myPanel;
  private JLabel myError;
  private JLabel myDescription;

  private ImageComponent myImagePreview;

  private TextFieldWithBrowseButton myImageFile;
  private JLabel myImageFileLabel;
  private JLabel myResourceNameLabel;
  private JTextField myResourceNameField;

  @SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI
  public VectorAssetSetStep(TemplateWizardState state, @Nullable Project project, @Nullable Module module,
                      @Nullable Icon sidePanelIcon, UpdateListener updateListener, @Nullable VirtualFile invocationTarget) {
    super(state, project, module, sidePanelIcon, updateListener, invocationTarget);

    myImageFile.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());

    myTemplateState.put(ATTR_ASSET_TYPE, AssetType.ACTIONBAR.name());
    // TODO: hook up notification type here!
    mySelectedAssetType = AssetType.ACTIONBAR;
    register(ATTR_ASSET_NAME, myResourceNameField);
  }

  @Override
  public void deriveValues() {
    super.deriveValues();
    if (!myTemplateState.myModified.contains(ATTR_ASSET_NAME)) {
      updateDerivedValue(ATTR_ASSET_NAME, myResourceNameField, new Callable<String>() {
        @Override
        public String call() throws Exception {
          return computeResourceName();
        }
      });
    }
  }

  @Override
  protected void updatePreviewImages() {
    if (mySelectedAssetType == null || myImageMap == null) {
      return;
    }

    final BufferedImage previewImage = getImage(myImageMap, Density.ANYDPI.getResourceValue());
    setIconOrClear(myImagePreview, previewImage);
  }

  @Nullable
  private static BufferedImage getImage(@NotNull Map<String, Map<String, BufferedImage>> map, @NotNull String name) {
    final Map<String, BufferedImage> images = map.get(name);
    if (images == null) {
      return null;
    }

    final Collection<BufferedImage> values = images.values();
    return values.isEmpty() ? null : values.iterator().next();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  protected void initialize() {
    register(ATTR_IMAGE_PATH, myImageFile);
  }

  @NotNull
  @Override
  protected String computeResourceName() {
    String resourceName = null;
    if (resourceName == null) {
      resourceName = String.format("ic_vector_name", "name");
    }

    if (drawableExists(resourceName)) {
      // While uniqueness isn't satisfied, increment number and add to end
      int i = 1;
      while (drawableExists(resourceName + Integer.toString(i))) {
        i++;
      }
      resourceName += Integer.toString(i);
    }

    return resourceName;
  }

  @Override
  protected void generateAssetFiles(File targetResDir) {
    myAssetGenerator.outputXmlToRes(targetResDir);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myImageFile;
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myError;
  }
}
