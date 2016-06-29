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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.MockupLayer;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.PixelProbe;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Helper class to manipulate XML and Image file for the mockup
 */
public class MockupFileHelper {

  final static String MOCKUP_DIR_NAME = "mockup";
  public static final Set<String> VALID_EXTENSION = new HashSet<>(Arrays.asList("psd", "png", "jpg"));
  public static final Logger LOGGER = Logger.getInstance(MockupFileHelper.class);

  private static final WeakHashMap<String, Image> IMAGE_CACHE = new WeakHashMap<>();

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
      image = PixelProbe.probe(in);
      IMAGE_CACHE.put(path, image);
    }
    catch (IOException e) {
      Logger.getInstance(MockupLayer.class).error(e);
    }
    return image;
  }

  public static FileChooserDescriptor getFileChooserDescriptor() {
    return FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
      .withFileFilter(file -> VALID_EXTENSION.contains(file.getExtension()))
      .withTitle("Choose Mockup File")
      .withDescription("PSD, JPEG, PNG are accepted");
  }

  public static void writeFileNameToXML(VirtualFile virtualFile, @Nullable NlComponent component) {
    if (component == null) {
      return;
    }
    final NlModel model = component.getModel();
    final Path filePath = getXMLFilePath(model.getProject(), virtualFile.getPath());
    if (filePath != null) {
      final String path = filePath.toString();
      final WriteCommandAction action = new WriteCommandAction(model.getProject(), "Edit Mockup file", model.getFile()) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP, path);
        }
      };
      action.execute();
    }
  }

  public static void writeOpacityToXML(Float opacity, @Nullable NlComponent component) {
    if (component == null) {
      return;
    }
    final NlModel model = component.getModel();
    final WriteCommandAction action = new WriteCommandAction(model.getProject(), "Edit Mockup Opacity", model.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_OPACITY, String.valueOf(opacity));
      }
    };
    action.execute();
  }


  public static void writePositionToXML(@NotNull Mockup mockup) {
    NlComponent component = mockup.getComponent();
    if (component == null) {
      return;
    }
    final NlModel model = component.getModel();
    final WriteCommandAction action = new WriteCommandAction(model.getProject(), "Edit Mockup Position", model.getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        if (mockup.isFullScreen()) {
          component.removeAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_POSITION);
        }
        else {
          final Rectangle bounds = mockup.getBounds();
          final Rectangle crop = mockup.getCropping();
          final String position = String.format(Locale.US, "%d %d %d %d %d %d %d %d",
                                                bounds.x, bounds.y, bounds.width, bounds.height,
                                                crop.x, crop.y, crop.width, crop.height);
          component.setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_POSITION, position);
        }
      }
    };
    action.execute();
  }

  public static boolean isInMockupDir(VirtualFile virtualFile, @Nullable String basePath) {
    if (basePath == null) {
      return false;
    }
    final Path mockupDirectory = getMockupDirectory(basePath);
    return Paths.get(virtualFile.getPath()).startsWith(mockupDirectory);
  }

  /**
   * Construct the file path for the mockup to write in the XML
   *
   * If the path is relative, make it relative to project root dir.
   * If the path is inside the project, we write the relative form
   * otherwise we use the absolute
   *
   * @param project  Current woriking project
   * @param filePath File path used by the user in XML or set by the {@link MockupEditorPopup}
   * @return Relative or absolute path or null if the path couldn't be resolved
   */
  @Nullable
  public static Path getXMLFilePath(Project project, String filePath) {
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

  @NotNull
  private static Path getMockupDirectory(@NotNull String basePath) {
    return Paths.get(basePath, MOCKUP_DIR_NAME).normalize();
  }
}
