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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.HashSet;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.List;
import java.util.Set;

public class NlPaletteModel {
  public static final String ANDROID_PALETTE = "android-palette";
  public static final String PALETTE_VERSION = "v1";
  public static final String METADATA = "palette.xml";

  private final Project myProject;
  private final Set<String> myLibrariesUsed;

  private Palette myPalette;

  public static NlPaletteModel get(@NonNull Project project) {
    return project.getComponent(NlPaletteModel.class);
  }

  /**
   * Use the {@link #get} method for getting a {@link NlPaletteModel} instance.<br>
   * This constructor is meant to be used by Intellij's plugin injector. It is not meant for normal use of this class.
   */
  public NlPaletteModel(@NonNull Project project) {
    myProject = project;
    myLibrariesUsed = new HashSet<String>();
  }

  public Palette getPalette() {
    if (myPalette == null) {
      loadPalette();
    }
    return myPalette;
  }

  public Set<String> getLibrariesUsed() {
    return myLibrariesUsed;
  }

  private void loadPalette() {
    try {
      File file = getPaletteFile(METADATA);
      Reader reader = new InputStreamReader(new FileInputStream(file));
      try {
        loadPalette(reader);
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    catch (JAXBException e) {
      throw new RuntimeException(e);
    }
  }

  @VisibleForTesting
  void loadPalette(@NonNull Reader reader) throws IOException, JAXBException {
    ViewHandlerManager manager = ViewHandlerManager.get(myProject);
    myLibrariesUsed.clear();
    myPalette = Palette.parse(reader, manager);
    findLibrariesUsed(myPalette.getItems());
  }

  private static File getPaletteFile(@NonNull String metadata) throws IOException {
    String path = FileUtil.toCanonicalPath(PathManager.getSystemPath());
    // @formatter:off
    //noinspection StringBufferReplaceableByString
    File paletteFile = new File(new StringBuilder()
                                    .append(path).append(File.separator)
                                    .append(ANDROID_PALETTE).append(File.separator)
                                    .append(PALETTE_VERSION).append(File.separator)
                                    .append(METADATA).toString());
    // @formatter:on
    if (!paletteFile.exists()) {
      copyPredefinedPalette(paletteFile, metadata);
    }
    return paletteFile;
  }

  private static void copyPredefinedPalette(@NonNull File paletteFile, @NonNull String metadata) throws IOException {
    InputStream stream = NlPaletteModel.class.getResourceAsStream(metadata);
    File folder = paletteFile.getParentFile();
    if (!folder.isDirectory() && !folder.mkdirs()) {
      throw new IOException("Could not create directory: " + folder);
    }
    FileOutputStream output = new FileOutputStream(paletteFile);
    try {
      FileUtil.copy(stream, output);
    }
    finally {
      stream.close();
      output.close();
    }
  }

  private void findLibrariesUsed(@NonNull List<Palette.BaseItem> items) {
    for (Palette.BaseItem item : items) {
      if (item instanceof Palette.Group) {
        Palette.Group group = (Palette.Group) item;
        findLibrariesUsed(group.getItems());
      }
      else if (item instanceof Palette.Item) {
        Palette.Item paletteItem = (Palette.Item) item;
        if (paletteItem.getGradleCoordinate() != null) {
          myLibrariesUsed.add(paletteItem.getGradleCoordinate());
        }
      }
    }
  }
}
