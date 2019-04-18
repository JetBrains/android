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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SketchDocument {
  private final SketchAssetCollection assets;
  private final short colorSpace;  // TODO what is this?
  private final int currentPageIndex;  // TODO what is this?
  private final SketchForeignStyle[] foreignLayerStyles;
  private final SketchForeignSymbol[] foreignSymbols;
  private final SketchForeignTextStyle[] foreignTextStyles;
  private final SketchSharedStyle[] layerStyles;
  private final SketchSharedSymbol[] layerSymbols;
  private final SketchSharedStyle[] layerTextStyles;

  public SketchDocument(@NotNull SketchAssetCollection assets,
                        short colorSpace,
                        int currentPageIndex,
                        @Nullable SketchForeignStyle[] foreignLayerStyles,
                        @Nullable SketchForeignSymbol[] foreignSymbols,
                        @Nullable SketchForeignTextStyle[] foreignTextStyles,
                        @NotNull SketchSharedStyle[] layerStyles,
                        @NotNull SketchSharedSymbol[] layerSymbols,
                        @NotNull SketchSharedStyle[] layerTextStyles) {
    this.assets = assets;
    this.colorSpace = colorSpace;
    this.currentPageIndex = currentPageIndex;
    this.foreignLayerStyles = foreignLayerStyles;
    this.foreignSymbols = foreignSymbols;
    this.foreignTextStyles = foreignTextStyles;
    this.layerStyles = layerStyles;
    this.layerSymbols = layerSymbols;
    this.layerTextStyles = layerTextStyles;
  }

  @NotNull
  public SketchAssetCollection getAssets() {
    return assets;
  }

  public short getColorSpace() {
    return colorSpace;
  }

  public int getCurrentPageIndex() {
    return currentPageIndex;
  }

  @Nullable
  public SketchForeignStyle[] getForeignLayerStyles() {
    return foreignLayerStyles;
  }

  @Nullable
  public SketchForeignSymbol[] getForeignSymbols() {
    return foreignSymbols;
  }

  @Nullable
  public SketchForeignTextStyle[] getForeignTextStyles() {
    return foreignTextStyles;
  }

  @NotNull
  public SketchSharedStyle[] getLayerStyles() {
    return layerStyles;
  }

  @NotNull
  public SketchSharedSymbol[] getLayerSymbols() {
    return layerSymbols;
  }

  @NotNull
  public SketchSharedStyle[] getLayerTextStyles() {
    return layerTextStyles;
  }
}
