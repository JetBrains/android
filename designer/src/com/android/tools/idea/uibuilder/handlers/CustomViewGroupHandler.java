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

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.xml.XmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.*;

public class CustomViewGroupHandler extends DelegatingViewGroupHandler {

  private final Icon myIcon16;
  private final Icon myIcon24;
  private final String myTagName;
  private final String myClassName;
  @Language("XML")
  private final String myXml;
  @Language("XML")
  private final String myPreviewXml;
  private final String myLibraryCoordinate;
  private final String myPreferredProperty;
  private final List<String> myProperties;
  private final List<String> myLayoutProperties;

  public CustomViewGroupHandler(@NotNull ViewGroupHandler handler,
                                @Nullable Icon icon16,
                                @Nullable Icon icon24,
                                @NotNull String tagName,
                                @NotNull String className,
                                @Nullable @Language("XML") String xml,
                                @Nullable @Language("XML") String previewXml,
                                @NotNull String libraryCoordinate,
                                @Nullable String preferredProperty,
                                @NotNull List<String> properties,
                                @NotNull List<String> layoutProperties) {
    super(handler);
    myIcon16 = icon16;
    myIcon24 = icon24;
    myTagName = tagName;
    myClassName = className;
    myXml = xml;
    myPreviewXml = previewXml;
    myLibraryCoordinate = libraryCoordinate;
    myPreferredProperty = preferredProperty;
    myProperties = properties;
    myLayoutProperties = layoutProperties;
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
    if (xmlType != XmlType.COMPONENT_CREATION) {
      return tagName.equals(myTagName) && !StringUtil.isEmpty(myPreviewXml) ? myPreviewXml : NO_PREVIEW;
    }
    else if (tagName.equals(myTagName) && !StringUtil.isEmpty(myXml)) {
      return myXml;
    }
    else if (myClassName.equals(myTagName)) {
      return super.getXml(tagName, xmlType);
    }
    else {
      return new XmlBuilder()
        .startTag(VIEW_TAG)
        .attribute(ATTR_CLASS, myClassName)
        .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
        .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT)
        .seperateEndTag(VIEW_TAG)
        .toString();
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
  @NotNull
  public List<String> getLayoutInspectorProperties() {
    return myLayoutProperties;
  }

  @Override
  @Nullable
  public String getPreferredProperty() {
    return myPreferredProperty;
  }
}
