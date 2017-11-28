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
package com.android.tools.idea.uibuilder.mockup;

import com.android.SdkConstants;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.MockupLayer;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.PixelProbe;
import com.android.tools.pixelprobe.decoder.Decoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Helper class to manipulate XML and Image file for the mockup
 */
public class MockupFileHelper {

  public static final Set<String> VALID_EXTENSION = new HashSet<>(Arrays.asList("psd", "png", "jpg"));
  public static final Logger LOGGER = Logger.getInstance(MockupFileHelper.class);

  private static final Map<String, Image> IMAGE_CACHE = ContainerUtil.createWeakMap();

  @Nullable
  public static Image openImageFile(String path) {
    Image image = null;
    final File file = new File(path);
    if (!file.exists()) {
      return null;
    }

    if (IMAGE_CACHE.containsKey(path)) {
      return IMAGE_CACHE.get(path);
    }

    try (FileInputStream in = new FileInputStream(file)) {
      image = PixelProbe.probe(in, Decoder.Options.LAYER_METADATA_ONLY);
      IMAGE_CACHE.put(path, image);
    }
    catch (IOException e) {
      Logger.getInstance(MockupLayer.class).error(e);
    }
    return image;
  }

  @NotNull
  public static FileChooserDescriptor getFileChooserDescriptor() {
    return FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withFileFilter(file -> VALID_EXTENSION.contains(file.getExtension()))
      .withTitle("Choose Mockup File")
      .withDescription("PSD, JPEG, PNG are accepted");
  }

  /**
   * Write the attribute {@link SdkConstants#ATTR_MOCKUP_CROP} and its value using the provided mockup
   * @param mockup The mockup to retrieve the position string from
   * @see #getPositionString(Mockup)
   */
  public static void writePositionToXML(@NotNull Mockup mockup) {
    NlComponent component = mockup.getComponent();
    if (component == null) {
      return;
    }

    NlWriteCommandAction.run(component, "Edit Mockup Crop", () -> {
      if (mockup.isFullScreen()) {
        component.removeAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_CROP);
      }
      else {
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_CROP, getPositionString(mockup));
      }
    });
  }

  /**
   * Return the string representing the crop and bounds of the provided mockup.
   *
   * @param mockup The mockup to retrieve the position from
   * @return A string representing the bounds and crop of the mockup.
   */
  public static String getPositionString(@NotNull Mockup mockup) {
    final Rectangle bounds = mockup.getBounds();
    final Rectangle crop = mockup.getCropping();
    final String cropping;
    if (bounds.equals(new Rectangle(0, 0, -1, -1))) {
      cropping = String.format(Locale.US, "%d %d %d %d",
                               crop.x, crop.y, crop.width, crop.height);
    }
    else {
      cropping = String.format(Locale.US, "%d %d %d %d %d %d %d %d",
                               crop.x, crop.y, crop.width, crop.height,
                               bounds.x, bounds.y, bounds.width, bounds.height);
    }
    return cropping;
  }

  /**
   * Construct the file path for the mockup to write in the XML
   *
   * If the path is relative, make it relative to project root dir.
   * If the path is inside the project, we write the relative form
   * otherwise we use the absolute
   *
   * @param project  Current project
   * @param filePath File path to modify
   * @return Relative or absolute path or null if the path couldn't be resolved
   */
  @Nullable
  public static Path getXMLFilePath(@NotNull Project project, @NotNull String filePath) {
    final String basePath = project.getBasePath();
    if (basePath == null) {
      return null;
    }
    Path path = null;
    final Path projectDirectory = Paths.get(basePath).normalize();
    try {
      path = getFullFilePath(project, filePath);
      // If the path is inside the project, we write the relative form
      // otherwise we use the absolute
      if (path != null && path.startsWith(projectDirectory)) {
        return projectDirectory.relativize(path).normalize();
      }
    }
    catch (Exception e) {
      LOGGER.error(String.format("Incorrect File Path : %s", filePath));
    }
    return path;
  }

  /**
   * Returns the absolute file path for the provided path. If the path is relative, it will be resolved
   * relatively to the provided project base directory
   * @param project The project used to resolve the path
   * @param filePath The path to return as absolute
   * @return The absolute version of the file path
   */
  @Nullable
  public static Path getFullFilePath(Project project, String filePath) {
    final String basePath = project.getBasePath();
    if (filePath == null || filePath.isEmpty() || basePath == null) {
      return null;
    }
    final Path projectDirectory = Paths.get(basePath).normalize();
    Path path;
    try {
      path = Paths.get(filePath);

      // If the path is relative, make it relative to project root dir
      if (!path.isAbsolute()) {
        path = projectDirectory.resolve(path).normalize();
      }
      return path.normalize();
    }
    catch (Exception e) {
      LOGGER.error(String.format("Incorrect File Path : %s", filePath));
    }
    return null;
  }
}
