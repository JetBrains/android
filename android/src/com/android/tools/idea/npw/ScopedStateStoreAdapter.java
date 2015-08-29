/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.assetstudiolib.GraphicGenerator;
import com.android.tools.idea.npw.AssetStudioAssetGenerator;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;

import static com.android.tools.idea.npw.IconStep.*;

/**
 * Adapter for asset studio asset generator to use data from {@link ScopedStateStore}.
 */
public class ScopedStateStoreAdapter implements AssetStudioAssetGenerator.AssetStudioContext {
  private final ScopedStateStore myState;

  public ScopedStateStoreAdapter(ScopedStateStore state) {
    myState = state;
  }

  @Override
  public int getPadding() {
    Integer value = myState.get(ATTR_PADDING);
    assert value != null;
    return value;
  }

  @Override
  public void setPadding(int padding) {
    myState.put(ATTR_PADDING, padding);
  }

  @Override
  @Nullable
  public AssetStudioAssetGenerator.SourceType getSourceType() {
    return myState.get(ATTR_SOURCE_TYPE);
  }

  @Override
  public void setSourceType(AssetStudioAssetGenerator.SourceType sourceType) {
    myState.put(ATTR_SOURCE_TYPE, sourceType);
  }

  @Nullable
  @Override
  public AssetStudioAssetGenerator.AssetType getAssetType() {
    return myState.get(ATTR_ASSET_TYPE);
  }

  @Override
  public boolean isTrim() {
    Boolean trim = myState.get(ATTR_TRIM);
    assert trim != null;
    return trim;
  }

  @Override
  public void setTrim(boolean trim) {
    myState.put(ATTR_TRIM, trim);
  }

  @Override
  public boolean isDogear() {
    Boolean dogEar = myState.get(ATTR_DOGEAR);
    assert dogEar != null;
    return dogEar;
  }

  @Override
  public void setDogear(boolean dogEar) {
    myState.put(ATTR_DOGEAR, dogEar);
  }

  @Nullable
  @Override
  public String getImagePath() {
    return myState.get(ATTR_IMAGE_PATH);
  }

  @Nullable
  @Override
  public String getText() {
    return myState.get(ATTR_TEXT);
  }

  @Override
  public void setText(String text) {
    myState.put(ATTR_TEXT, text);
  }

  @Nullable
  @Override
  public String getClipartName() {
    return myState.get(ATTR_CLIPART_NAME);
  }

  @Override
  public void setClipartName(String name) {
    myState.put(ATTR_CLIPART_NAME, name);
  }

  @Nullable
  @Override
  public URL getVectorLibIconPath() {
    return myState.get(ATTR_VECTOR_LIB_ICON_PATH);
  }

  @Override
  public Color getForegroundColor() {
    Color color = myState.get(ATTR_FOREGROUND_COLOR);
    assert color != null;
    return color;
  }

  @Override
  public void setForegroundColor(Color fg) {
    myState.put(ATTR_FOREGROUND_COLOR, fg);
  }

  @Nullable
  @Override
  public String getFont() {
    return myState.get(ATTR_FONT);
  }

  @Override
  public void setFont(String fontName) {
    myState.put(ATTR_FONT, fontName);
  }

  @Override
  public int getFontSize() {
    Integer fontSize = myState.get(ATTR_FONT_SIZE);
    assert fontSize != null;
    return fontSize;
  }

  @Override
  public void setFontSize(int fontSize) {
    myState.put(ATTR_FONT_SIZE, fontSize);
  }

  @Override
  public AssetStudioAssetGenerator.Scaling getScaling() {
    AssetStudioAssetGenerator.Scaling scaling = myState.get(ATTR_SCALING);
    assert scaling != null;
    return scaling;
  }

  @Override
  public void setScaling(AssetStudioAssetGenerator.Scaling scaling) {
    myState.put(ATTR_SCALING, scaling);
  }

  @Nullable
  @Override
  public String getAssetName() {
    return myState.get(ATTR_ASSET_NAME);
  }

  @Override
  public GraphicGenerator.Shape getShape() {
    GraphicGenerator.Shape shape = myState.get(ATTR_SHAPE);
    assert shape != null;
    return shape;
  }

  @Override
  public void setShape(GraphicGenerator.Shape shape) {
    myState.put(ATTR_SHAPE, shape);
  }

  @Override
  public Color getBackgroundColor() {
    Color color = myState.get(ATTR_BACKGROUND_COLOR);
    assert color != null;
    return color;
  }

  @Override
  public void setBackgroundColor(Color bg) {
    myState.put(ATTR_BACKGROUND_COLOR, bg);
  }

  @Nullable
  @Override
  public String getAssetTheme() {
    return myState.get(ATTR_ASSET_THEME);
  }

  @Override
  public void setErrorLog(String log) {
    myState.put(ATTR_ERROR_LOG, log);
  }

  @Override
  public String getVectorWidth()  {
    return myState.get(ATTR_VECTOR_DRAWBLE_WIDTH);
  }

  @Override
  public String getVectorHeight()  {
    return myState.get(ATTR_VECTOR_DRAWBLE_HEIGHT);
  }

  @Override
  public void setOriginalWidth(int width) {
    myState.put(ATTR_ORIGINAL_WIDTH, width);
  }

  @Override
  public void setOriginalHeight(int height) {
    myState.put(ATTR_ORIGINAL_HEIGHT, height);
  }

  @Override
  public int getVectorOpacity()  {
    return myState.get(ATTR_VECTOR_DRAWBLE_OPACTITY);
  }

  @Override
  public boolean getVectorAutoMirrored()  {
    return myState.get(ATTR_VECTOR_DRAWBLE_AUTO_MIRRORED);
  }

  @Override
  public void setValidPreview(boolean valid)  {
    myState.put(ATTR_VALID_PREVIEW, valid);
  }
}
