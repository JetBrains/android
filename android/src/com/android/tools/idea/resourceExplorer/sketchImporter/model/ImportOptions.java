/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.model;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intermediary level connecting the (immutable) data read from the Sketch file and the
 * user-defined (variable) options for parsing that data.
 */
public class ImportOptions {
  private static final Logger LOGGER_INSTANCE = Logger.getInstance(ImportOptions.class);
  private static final boolean DEFAULT_IMPORT_ALL = false;

  private boolean myImportAll = DEFAULT_IMPORT_ALL;

  /**
   * Mapping {@code objectId}s to corresponding options.
   */
  private HashMap<String, PageOptions> myPageIdToOptions = new HashMap<>();
  private HashMap<String, IconOptions> myIconIdToOptions = new HashMap<>();

  public ImportOptions(@NotNull SketchFile file) {
    initWithDefaultOptions(file);
  }

  public boolean isImportAll() {
    return myImportAll;
  }

  /**
   * @param importAll {@code false} if the user chooses to only import icons explicitly marked as
   *                  Exportable, {@code true} otherwise.
   */
  public void setImportAll(boolean importAll) {
    myImportAll = importAll;
  }

  /**
   * @param objectId ID of desired page
   * @return options corresponding to page with {@code objectId}
   */
  @Nullable
  public PageOptions getPageOptions(@NotNull String objectId) {
    return myPageIdToOptions.get(objectId);
  }

  /**
   * @param objectId ID of desired icon
   * @return options corresponding to icon with {@code objectId}
   */
  @Nullable
  public IconOptions getIconOptions(@NotNull String objectId) {
    return myIconIdToOptions.get(objectId);
  }

  public boolean putPageOptions(@NotNull String objectId, @NotNull PageOptions options) {
    if (myPageIdToOptions.get(objectId) != null) {
      myPageIdToOptions.put(objectId, options);
      return true;
    }
    else {
      LOGGER_INSTANCE.warn("Page with objectId = " + objectId + " not found!");
      return false;
    }
  }

  public boolean putIconOptions(@NotNull String objectId, @NotNull IconOptions options) {
    if (myIconIdToOptions.get(objectId) != null) {
      myIconIdToOptions.put(objectId, options);
      return true;
    }
    else {
      Logger.getInstance(ImportOptions.class).warn("Artboard with objectId = " + objectId + " not found!");
      return false;
    }
  }

  /**
   * Add options for each page/artboard in the {@code myObjectIdToOptions HashMap} based on the sketch data.
   */
  private void initWithDefaultOptions(@NotNull SketchFile file) {
    for (SketchPage page : file.getPages()) {
      PageOptions pageOptions = new PageOptions(page);
      myPageIdToOptions.put(page.getObjectId(), pageOptions);

      for (SketchLayer layer : page.getLayers()) {
        if (layer instanceof SketchArtboard) {
          IconOptions options = new IconOptions((SketchArtboard)layer);
          myIconIdToOptions.put(layer.getObjectId(), options);
        }
      }
    }
  }
}
