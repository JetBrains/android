/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.common.model.NlComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class CustomViewHandler extends DelegatingViewHandler {
  private final Icon myIcon16;
  private final Icon myIcon24;
  private final String myTagName;
  @Language("XML")
  private final String myXml;
  @Language("XML")
  private final String myPreviewXml;
  private final String myLibraryCoordinate;
  private final String myPreferredProperty;
  private final List<String> myProperties;

  public CustomViewHandler(@NotNull ViewHandler handler,
                           @Nullable Icon icon16,
                           @Nullable Icon icon24,
                           @NotNull String tagName,
                           @Nullable @Language("XML") String xml,
                           @Nullable @Language("XML") String previewXml,
                           @NotNull String libraryCoordinate,
                           @Nullable String preferredProperty,
                           @NotNull List<String> properties) {
    super(handler);
    myIcon16 = icon16;
    myIcon24 = icon24;
    myTagName = tagName;
    myXml = xml;
    myPreviewXml = previewXml;
    myLibraryCoordinate = libraryCoordinate;
    myPreferredProperty = preferredProperty;
    myProperties = properties;
  }

  // Palette

  @Override
  @NotNull
  public Icon getIcon(@NotNull String tagName) {
    return myIcon16 != null && tagName.equals(myTagName) ? myIcon16 : super.getIcon(tagName);
  }

  @Override
  @NotNull
  public Icon getLargeIcon(@NotNull String tagName) {
    return myIcon24 != null && tagName.equals(myTagName) ? myIcon24 : super.getIcon(tagName);
  }

  @Override
  @NotNull
  public String getGradleCoordinateId(@NotNull String tagName) {
    return tagName.equals(myTagName) ? myLibraryCoordinate : super.getGradleCoordinateId(tagName);
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    if (xmlType == XmlType.COMPONENT_CREATION) {
      return tagName.equals(myTagName) && !StringUtil.isEmpty(myXml) ? myXml : super.getXml(tagName, xmlType);
    }
    else {
      return tagName.equals(myTagName) && !StringUtil.isEmpty(myPreviewXml) ? myPreviewXml : NO_PREVIEW;
    }
  }

  // Component tree

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    return component.getTagName().equals(myTagName) && myIcon16 != null ? myIcon16 : super.getIcon(component);
  }

  // Properties

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return myProperties;
  }

  @Override
  @Nullable
  public String getPreferredProperty() {
    return myPreferredProperty;
  }
}
