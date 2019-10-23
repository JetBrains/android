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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Library that contains global information that is relevant when processing parts of a Sketch file.
 */
public class SketchLibrary {
  private SymbolsLibrary mySymbolsLibrary = new SymbolsLibrary();
  private StylesLibrary myStylesLibrary = new StylesLibrary();

  public void addStyles(@NotNull ImmutableList<SketchStyle> styles) {
    myStylesLibrary.addStyles(styles);
  }

  public void addSymbols(@NotNull ImmutableList<SketchSymbolMaster> symbolMasters) {
    mySymbolsLibrary.addSymbols(symbolMasters);
  }

  @Nullable
  public SketchStyle getStyle(@NotNull String objectId) {
    return myStylesLibrary.getStyle(objectId);
  }

  @Nullable
  public SketchSymbolMaster getSymbol(@NotNull String objectId) {
    return mySymbolsLibrary.getSymbol(objectId);
  }

  public boolean hasStyles() {
    return !myStylesLibrary.isEmpty();
  }

  public boolean hasSymbols() {
    return !mySymbolsLibrary.isEmpty();
  }
}
