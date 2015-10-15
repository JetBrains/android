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
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.net.URL;

import static com.android.tools.idea.npw.AssetStudioAssetGenerator.*;

/**
 * Adapter to use asset studio with template wizard context
 */
public final class TemplateWizardContextAdapter implements AssetStudioContext {
  private final TemplateWizardState myWizardState;

  public TemplateWizardContextAdapter(TemplateWizardState wizardState) {
    myWizardState = wizardState;
  }

  @Override
  public int getPadding() {
    return myWizardState.getInt(ATTR_PADDING);
  }

  @Override
  public void setPadding(int padding) {
    myWizardState.put(ATTR_PADDING, padding);
  }

  @Override
  public SourceType getSourceType() {
    return (SourceType)myWizardState.get(ATTR_SOURCE_TYPE);
  }

  @Override
  public void setSourceType(SourceType sourceType) {
    myWizardState.put(ATTR_SOURCE_TYPE, sourceType);
  }

  @Nullable
  @Override
  public AssetType getAssetType() {
    String o = (String)myWizardState.get(ATTR_ASSET_TYPE);
    return StringUtil.isEmpty(o) ? null : AssetType.valueOf(o);
  }

  @Override
  public boolean isTrim() {
    return myWizardState.getBoolean(ATTR_TRIM);
  }

  @Override
  public void setTrim(boolean trim) {
    myWizardState.put(ATTR_TRIM, trim);
  }

  @Override
  public boolean isDogear() {
    return myWizardState.getBoolean(ATTR_DOGEAR);
  }

  @Override
  public void setDogear(boolean dogEar) {
    myWizardState.put(ATTR_DOGEAR, dogEar);
  }

  @Nullable
  @Override
  public String getImagePath() {
    return (String)myWizardState.get(ATTR_IMAGE_PATH);
  }

  @Nullable
  @Override
  public String getText() {
    return (String)myWizardState.get(ATTR_TEXT);
  }

  @Override
  public void setText(String text) {
    myWizardState.put(ATTR_TEXT, text);
  }

  @Nullable
  @Override
  public String getClipartName() {
    return (String)myWizardState.get(ATTR_CLIPART_NAME);
  }

  @Override
  public void setClipartName(String clipartName) {
    myWizardState.put(ATTR_CLIPART_NAME, clipartName);
  }

  @Nullable
  @Override
  public URL getVectorLibIconPath() {
    return (URL) myWizardState.get(ATTR_VECTOR_LIB_ICON_PATH);
  }

  @Override
  public Color getForegroundColor() {
    return getNonNullValue(ATTR_FOREGROUND_COLOR, Color.class);
  }

  @Override
  public void setForegroundColor(Color fg) {
    myWizardState.put(ATTR_FOREGROUND_COLOR, fg);
  }

  @Nullable
  @Override
  public String getFont() {
    return (String)myWizardState.get(ATTR_FONT);
  }

  @Override
  public void setFont(String font) {
    myWizardState.put(ATTR_FONT, font);
  }

  @Override
  public int getFontSize() {
    return myWizardState.getInt(ATTR_FONT_SIZE);
  }

  @Override
  public void setFontSize(int fontSize) {
    myWizardState.put(ATTR_FONT_SIZE, fontSize);
  }

  @Override
  public AssetStudioAssetGenerator.Scaling getScaling() {
    return getNonNullValue(ATTR_SCALING, Scaling.class);
  }

  @Override
  public void setScaling(Scaling scaling) {
    myWizardState.put(ATTR_SCALING, scaling);
  }

  private <T> T getNonNullValue(String attribute, Class<T> type) {
    Object scaling = myWizardState.get(attribute);
    assert type.isInstance(scaling);
    return type.cast(scaling);
  }

  @Nullable
  @Override
  public String getAssetName() {
    return (String)myWizardState.get(ATTR_ASSET_NAME);
  }

  @Override
  public GraphicGenerator.Shape getShape() {
    return getNonNullValue(ATTR_SHAPE, GraphicGenerator.Shape.class);
  }

  @Override
  public void setShape(GraphicGenerator.Shape shape) {
    myWizardState.put(ATTR_SHAPE, shape);
  }

  @Override
  public Color getBackgroundColor() {
    return getNonNullValue(ATTR_BACKGROUND_COLOR, Color.class);
  }

  @Override
  public void setBackgroundColor(Color bg) {
    myWizardState.put(ATTR_BACKGROUND_COLOR, bg);
  }

  @Nullable
  @Override
  public String getAssetTheme() {
    return (String)myWizardState.get(ATTR_ASSET_THEME);
  }

  @Override
  public void setErrorLog(String log) {
    myWizardState.put(ATTR_ERROR_LOG, log);
  }

  @Override
  public String getVectorWidth()  {
    return myWizardState.getString(ATTR_VECTOR_DRAWBLE_WIDTH);
  }

  @Override
  public String getVectorHeight()  {
    return myWizardState.getString(ATTR_VECTOR_DRAWBLE_HEIGHT);
  }

  @Override
  public void setOriginalWidth(int width) {
    myWizardState.put(ATTR_ORIGINAL_WIDTH, width);
  }

  @Override
  public void setOriginalHeight(int height) {
    myWizardState.put(ATTR_ORIGINAL_HEIGHT, height);
  }

  @Override
  public void setValidPreview(boolean valid) {
    myWizardState.put(ATTR_VALID_PREVIEW, valid);
  }

  @Override
  public int getVectorOpacity()  {
    return myWizardState.getInt(ATTR_VECTOR_DRAWBLE_OPACTITY);
  }

  @Override
  public boolean getVectorAutoMirrored()  {
    return myWizardState.getBoolean(ATTR_VECTOR_DRAWBLE_AUTO_MIRRORED);
  }
}
