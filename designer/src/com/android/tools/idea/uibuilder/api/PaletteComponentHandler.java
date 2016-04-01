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
package com.android.tools.idea.uibuilder.api;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.IconLoader;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;

import javax.swing.*;

import static com.android.SdkConstants.*;

/**
 * A handler for a component on the component Palette.
 */
public abstract class PaletteComponentHandler {

  /**
   * A special value returned from {@link #getXml} for {@link XmlType#PREVIEW_ON_PALETTE}
   * or {@link XmlType#DRAG_PREVIEW} to indicate that the palette should not attempt to
   * generate a preview of this component but instead use the palette default representation.
   */
  @Language("XML")
  public static final String NO_PREVIEW = "";

  /**
   * A special value returned from {@link #getGradleCoordinate} to indicate that this
   * component is included in the SDK platform.
   */
  public static final String IN_PLATFORM = "";

  /**
   * Returns the title used to identify this component type on the palette.<br>
   * This default implementation assumes the title is the same as the non qualified tag name.
   *
   * @param tagName the tag name of the component
   * @return a title of the component
   */
  @NotNull
  public String getTitle(@NotNull String tagName) {
    return getSimpleTagName(tagName);
  }

  /**
   * Returns the icon used to represent this component on the palette.<br>
   * This default implementation assumes the icon is one of the builtin icons.
   *
   * @param tagName the tag name of the component
   * @return an icon to identify the component
   */
  @NotNull
  public Icon getIcon(@NotNull String tagName) {
    return loadBuiltinIcon(tagName);
  }

  /**
   * Returns the Gradle coordinate (ex. "com.android.support:support-v4") of the library
   * this component belongs to. The palette will use this information to provide a download
   * link if the library is not present in the project dependencies.<br>
   *
   * The value {@link #IN_PLATFORM} means the component is included in the SDK platform.
   *
   * @return the Gradle Coordinate of the library this component belongs to
   */
  @NotNull
  public String getGradleCoordinate(@NotNull String tagName) {
    return getBuiltinCoordinate(tagName);
  }

  /**
   * Returns the XML representation of a component.<br>
   * Depending on {@param xmlType} the generated XML is used to:
   * <ul>
   *   <li>{@link XmlType#COMPONENT_CREATION} create the component in the designer (must be not null)</li>
   *   <li>{@link XmlType#PREVIEW_ON_PALETTE} preview the component on the palette (or {@link #NO_PREVIEW})</li>
   *   <li>{@link XmlType#DRAG_PREVIEW} preview the component while dragging from the palette (or {@link #NO_PREVIEW})</li>
   * </ul>
   *
   * @param tagName the tag name of the component
   * @param xmlType how the caller intend to use the XML returned
   * @return the XML for a newly created component
   */
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    // Most components that are not containers are rendered by this simple XML
    return String.format("<%1$s\n" +
                         "  android:layout_width=\"wrap_content\"\n" +
                         "  android:layout_height=\"wrap_content\">\n" +
                         "</%1$s>\n", tagName);
  }

  /**
   * Returns the scale factor used for previewing this component on the Palette.
   *
   * @param tagName the tag name of the component
   * @return the scale used to preview the component
   */
  public double getPreviewScale(@NotNull String tagName) {
    return 1.0;
  }

  @NotNull
  protected static String getSimpleTagName(@NotNull String tagName) {
    int lastIndex = tagName.lastIndexOf('.');
    return lastIndex < 0 ? tagName : tagName.substring(lastIndex + 1);
  }

  @NotNull
  protected Icon loadBuiltinIcon(@NotNull String tagName) {
    String path = "AndroidIcons.Views." + getSimpleTagName(tagName);
    Icon icon = IconLoader.findIcon(path, getClass());
    return icon != null ? icon : AndroidIcons.Views.Unknown;
  }

  @NotNull
  private static String getBuiltinCoordinate(@NotNull String tagName) {
    if (tagName.startsWith(ANDROID_SUPPORT_V4_PKG)) {
      return SUPPORT_LIB_ARTIFACT;
    }
    if (tagName.startsWith(ANDROID_SUPPORT_V7_PKG)) {
      return APPCOMPAT_LIB_ARTIFACT;
    }
    if (tagName.startsWith(ANDROID_SUPPORT_DESIGN_PKG)) {
      return DESIGN_LIB_ARTIFACT;
    }
    if (tagName.startsWith(ANDROID_SUPPORT_LEANBACK_V17_PKG)) {
      return LEANBACK_V17_ARTIFACT;
    }
    if (tagName.startsWith(GOOGLE_PLAY_SERVICES_ADS_PKG)) {
      return ADS_ARTIFACT;
    }
    if (tagName.startsWith(GOOGLE_PLAY_SERVICES_MAPS_PKG)) {
      return MAPS_ARTIFACT;
    }
    return IN_PLATFORM;
  }
}
