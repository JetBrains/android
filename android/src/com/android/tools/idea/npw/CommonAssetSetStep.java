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
package com.android.tools.idea.npw;

import com.android.builder.model.SourceProvider;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.ui.ImageComponent;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Iterators;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.android.tools.idea.npw.AssetStudioAssetGenerator.*;

/**
 * This is the common asset set step for both PNG files and SVG files.
 */
abstract public class CommonAssetSetStep extends TemplateWizardStep implements Disposable {
  public static final String ATTR_OUTPUT_FOLDER = "outputFolder";
  private static final Logger LOG = Logger.getInstance(CommonAssetSetStep.class);
  protected SourceProvider mySourceProvider = null;

  protected AssetStudioAssetGenerator myAssetGenerator;

  protected AssetType mySelectedAssetType;
  protected boolean myInitialized;
  protected final MergingUpdateQueue myUpdateQueue;
  protected final Map<String, Map<String, BufferedImage>> myImageMap = new ConcurrentHashMap<>();

  @SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI
  public CommonAssetSetStep(TemplateWizardState state,
                            @Nullable Project project,
                            @Nullable Module module,
                            @Nullable Icon sidePanelIcon,
                            UpdateListener updateListener,
                            @Nullable VirtualFile invocationTarget) {
    super(state, project, module, sidePanelIcon, updateListener);
    myAssetGenerator = new AssetStudioAssetGenerator(state);
    if (invocationTarget != null && module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null) {
        mySourceProvider = Iterators.getNext(IdeaSourceProvider.getSourceProvidersForFile(facet, invocationTarget, null).iterator(), null);
      }
    }

    myUpdateQueue = new MergingUpdateQueue("asset.studio", 200, true, null, this, null, false);
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }

    String assetName = myTemplateState.getString(ATTR_ASSET_NAME);
    if (drawableExists(assetName)) {
      setErrorHtml(String.format("A drawable resource named %s already exists and will be overwritten.", assetName));
    }
    requestPreviewUpdate();
    return isValid();
  }

  /**
   * (Re)schedule the background task which updates the preview images.
   */
  private void requestPreviewUpdate() {
    myUpdateQueue.cancelAllUpdates();
    myUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        try {
          myAssetGenerator.generateImages(myImageMap, true, true);
          SwingUtilities.invokeLater(CommonAssetSetStep.this::updatePreviewImages);
        }
        catch (final ImageGeneratorException e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              setErrorHtml(e.getMessage());
            }
          });
        }
      }
    });
  }

  abstract protected void updatePreviewImages();

  protected static void setIconOrClear(@NotNull ImageComponent component, @Nullable BufferedImage image) {
    if (image == null) {
      component.setIcon(null);
    } else {
      component.setIcon(new ImageIcon(image));
    }
  }

  @Override
  public void updateStep() {
    super.updateStep();

    if (!myInitialized) {
      myInitialized = true;
      initialize();
    }
  }

  abstract protected void initialize();

  /**
   * Generate a resource name.
   */
  @NotNull
  abstract protected String computeResourceName();

  /**
   * Must be run inside a write action. Creates the asset files on disk.
   */
  public void createAssets(@Nullable Module module) {
    File targetResDir = (File)myTemplateState.get(ATTR_OUTPUT_FOLDER);
    if (targetResDir == null) {
      if (myTemplateState.hasAttr(TemplateMetadata.ATTR_RES_DIR)) {
        assert module != null;
        File moduleDir = new File(module.getModuleFilePath()).getParentFile();
        targetResDir = new File(moduleDir, myTemplateState.getString(TemplateMetadata.ATTR_RES_DIR));
      } else {
        return;
      }
    }

    generateAssetFiles(targetResDir);

    VirtualFile resDir = LocalFileSystem.getInstance().findFileByIoFile(targetResDir);
    if (resDir != null) {
      // Refresh the res directory so that the new files show up in the IDE.
      resDir.refresh(true, true);
    } else {
      // If we can't find the res directory, refresh the project.
      if (myProject != null) {
        myProject.getBaseDir().refresh(true, true);
      }
    }
  }

  abstract protected void generateAssetFiles(File targetResDir);

  protected boolean drawableExists(String resourceName) {
    if (mySourceProvider != null) {
      return Parameter.existsResourceFile(mySourceProvider, myModule, ResourceFolderType.DRAWABLE, ResourceType.DRAWABLE, resourceName);
    }
    return Parameter.existsResourceFile(myModule, ResourceType.DRAWABLE, resourceName);
  }

  @Override
  public void dispose()  {
    myUpdateQueue.cancelAllUpdates();
  }
}
