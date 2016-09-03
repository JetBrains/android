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
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.mockup.editor.creators.forms.ImageCreatorForm;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;
import static org.jetbrains.android.util.AndroidUtils.createChildDirectoryIfNotExist;

/**
 * Create an ImageView and displays option to export the selection as a drawable
 */
public class ImageViewCreator extends SimpleViewCreator {

  public static final Logger LOGGER = Logger.getInstance(ImageViewCreator.class);
  public static final String DRAWABLE_TYPE = "png";

  @Nullable private String myNewDrawableName;

  /**
   * Create a simple {@value SdkConstants#VIEW} tag
   * with the size, mockup, and tools position attributes
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link DesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates.
   * @param selection  The selection made in the {@link MockupEditor}
   */
  public ImageViewCreator(@NotNull Mockup mockup,
                          @NotNull NlModel model,
                          @NotNull ScreenView screenView, @NotNull Rectangle selection) {
    super(mockup, model, screenView, selection);
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
    ImageCreatorForm imageCreatorForm = new ImageCreatorForm();
    imageCreatorForm.addSetSourceListener(e -> createDrawable(imageCreatorForm.getDrawableName(), doneCallback));
    imageCreatorForm.getDoNotSetSourceButton().addActionListener(e -> doneCallback.done(DoneCallback.CANCEL));
    return imageCreatorForm.getComponent();
  }

  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
    super.addAttributes(transaction);
    if (myNewDrawableName != null) {
      transaction.setAttribute(null, ANDROID_NS_NAME_PREFIX + ATTR_SRC, DRAWABLE_PREFIX + myNewDrawableName);
    }
  }

  /**
   * Create a drawable into the drawable folder with the given name. The image created will be a png file.
   *
   * @param drawableName The name of the image to create (without the extension)
   * @param doneCallback The callback to call once the image is created
   */
  private void createDrawable(@NotNull String drawableName, @NotNull DoneCallback doneCallback) {
    AndroidFacet facet = getModel().getFacet();
    try {
      Rectangle selectionBounds = getSelectionBounds();
      BufferedImage image = getMockup().getImage();
      if (image == null) {
        return;
      }

      // Extract selection from original image
      final Rectangle realCropping = getMockup().getRealCropping();
      BufferedImage subImage =
        image.getSubimage(selectionBounds.x + realCropping.x, selectionBounds.y + realCropping.y, selectionBounds.width,
                          selectionBounds.height);

      // Transform the new image into a byte array
      byte[] imageInByte = imageToByteArray(subImage);

      // Create a new file in the res/drawable folder
      List<VirtualFile> drawableSubDirs = AndroidResourceUtil.getResourceSubdirs(
        ResourceFolderType.DRAWABLE,
        VfsUtilCore.toVirtualFileArray(facet.getModuleResources(true).getResourceDirs()));

      Project project = getModel().getProject();
      // Check if the drawable folder already exist, create it otherwise
      if (!drawableSubDirs.isEmpty()) {
        createDrawableFile(drawableName, imageInByte, project, drawableSubDirs.get(0), doneCallback);
      }
      else {
        createDrawableAndFolder(drawableName, facet, imageInByte, project, doneCallback);
      }
    }
    catch (IOException e) {
      LOGGER.error("Could not export selection to drawable");
    }
  }

  /**
   * Create a byte array from a BufferedImage
   *
   * @param subImage the image to convert
   * @return the byte array representing the image
   * @throws IOException
   */
  private static byte[] imageToByteArray(BufferedImage subImage) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(subImage, DRAWABLE_TYPE, baos);
    baos.flush();
    byte[] imageInByte = baos.toByteArray();
    baos.close();
    return imageInByte;
  }

  /**
   * Create the drawable folder into the main resource directory and
   * then create the new image file represented by imageInByte
   *
   * @param drawableName The name of the drawable to create
   * @param facet        the current facet of the model
   * @param imageInByte  the byte representation of the image to create
   * @param project      the current project
   * @param doneCallback The callback to call one the image is created
   */
  private void createDrawableAndFolder(@NotNull String drawableName,
                                       @NotNull AndroidFacet facet,
                                       @NotNull byte[] imageInByte,
                                       @NotNull Project project,
                                       @NotNull DoneCallback doneCallback) {
    Collection<VirtualFile> resDirectories = facet.getMainIdeaSourceProvider().getResDirectories();
    Iterator<VirtualFile> iterator = resDirectories.iterator();
    if (iterator.hasNext()) {
      CommandProcessor.getInstance().executeCommand(
        project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            VirtualFile drawableDir = createChildDirectoryIfNotExist(project, iterator.next(), FD_RES_DRAWABLE);
            createDrawableFile(drawableName, imageInByte, project, drawableDir, doneCallback);
          }
          catch (IOException e) {
            LOGGER.error(e);
          }
        }),
        "Export selection to drawable",
        null
      );
    }
  }

  /**
   * Create the image file in the drawable directory
   *
   * @param drawableName The name of the drawable to create
   * @param imageInByte  the byte representation of the image to create
   * @param project      the current project
   * @param doneCallback The callback to call one the image is created
   */
  private void createDrawableFile(@NotNull String drawableName,
                                  @NotNull byte[] imageInByte,
                                  @NotNull Project project,
                                  @NotNull VirtualFile drawableDir,
                                  @NotNull DoneCallback doneCallback) {
    CommandProcessor.getInstance().executeCommand(
      project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          myNewDrawableName = drawableName;
          final VirtualFile folder = drawableDir.createChildData(this, drawableName + "." + DRAWABLE_TYPE);
          folder.setBinaryContent(imageInByte);
          doneCallback.done(DoneCallback.FINISH);
        }
        catch (IOException e) {
          LOGGER.error(e);
          doneCallback.done(DoneCallback.CANCEL);
        }
      }),
      "Export selection to drawable",
      null
    );
  }
}
