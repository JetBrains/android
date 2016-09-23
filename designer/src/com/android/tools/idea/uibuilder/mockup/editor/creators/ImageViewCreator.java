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
import static com.android.tools.idea.uibuilder.mockup.editor.creators.ResourcesUtil.createDrawable;
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
    imageCreatorForm.addSetSourceListener(e -> {
      myNewDrawableName = imageCreatorForm.getDrawableName();
      createDrawable(myNewDrawableName, DRAWABLE_TYPE, doneCallback, getMockup(), getModel(),
                     getSelectionBounds(), this);
    });
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
}
