/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public class SketchForeignSymbol {
  @SerializedName("libraryID")
  private final String libraryId;
  private final String sourceLibraryName;
  private final SketchSymbolMaster originalMaster;
  private final SketchSymbolMaster symbolMaster;

  public SketchForeignSymbol(@NotNull String libraryId,
                             @NotNull String sourceLibraryName,
                             @NotNull SketchSymbolMaster originalMaster,
                             @NotNull SketchSymbolMaster symbolMaster) {
    this.libraryId = libraryId;
    this.sourceLibraryName = sourceLibraryName;
    this.originalMaster = originalMaster;
    this.symbolMaster = symbolMaster;
  }

  @NotNull
  public String getLibraryId() {
    return libraryId;
  }

  @NotNull
  public String getSourceLibraryName() {
    return sourceLibraryName;
  }

  @NotNull
  public SketchSymbolMaster getOriginalMaster() {
    return originalMaster;
  }

  @NotNull
  public SketchSymbolMaster getSymbolMaster() {
    return symbolMaster;
  }
}
