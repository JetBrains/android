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

import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.xml.XmlBuilder;
import icons.StudioIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.*;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_APPCOMPAT;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_CARDVIEW;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_CONSTRAINTLAYOUT;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_COORDINATORLAYOUT;
import static com.android.ide.common.repository.GoogleMavenArtifactId.MATERIAL;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_GRIDLAYOUT;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_LEANBACK;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_RECYCLERVIEW;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_LEGACY_SUPPORT_V4;
import static com.android.ide.common.repository.GoogleMavenArtifactId.ANDROIDX_VIEWPAGER;
import static com.android.ide.common.repository.GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7;
import static com.android.ide.common.repository.GoogleMavenArtifactId.CONSTRAINT_LAYOUT;
import static com.android.ide.common.repository.GoogleMavenArtifactId.SUPPORT_DESIGN;
import static com.android.ide.common.repository.GoogleMavenArtifactId.SUPPORT_LEANBACK_V17;
import static com.android.ide.common.repository.GoogleMavenArtifactId.PLAY_SERVICES_ADS;
import static com.android.ide.common.repository.GoogleMavenArtifactId.PLAY_SERVICES_MAPS;
import static com.android.ide.common.repository.GoogleMavenArtifactId.SUPPORT_V4;

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
   * Returns the {@link GoogleMavenArtifactId} of the library
   * this component belongs to. The palette will use this information to provide a download
   * link if the library is not present in the project dependencies.<br>
   *
   * @return the Gradle Coordinate ID of the library this component belongs to, or null if
   *         the component is included in the SDK platform.
   */
  @Nullable
  public GoogleMavenArtifactId getGradleCoordinateId(@NotNull String tagName) {
    if (tagName.startsWith(ANDROID_SUPPORT_V4_PKG)) {
      return SUPPORT_V4;
    }
    else if (tagName.startsWith(ANDROID_SUPPORT_V7_PKG)) {
      return SUPPORT_APPCOMPAT_V7;
    }
    else if (tagName.startsWith(ANDROID_SUPPORT_DESIGN_PKG)) {
      return SUPPORT_DESIGN;
    }
    else if (tagName.startsWith(ANDROID_MATERIAL_PKG)) {
      return MATERIAL;
    }
    else if (tagName.startsWith(ANDROID_SUPPORT_LEANBACK_V17_PKG)) {
      return SUPPORT_LEANBACK_V17;
    }
    else if (tagName.startsWith(GOOGLE_PLAY_SERVICES_ADS_PKG)) {
      return PLAY_SERVICES_ADS;
    }
    else if (tagName.startsWith(GOOGLE_PLAY_SERVICES_MAPS_PKG)) {
      return PLAY_SERVICES_MAPS;
    }
    else if (tagName.startsWith(CONSTRAINT_LAYOUT_PKG)) {
      return CONSTRAINT_LAYOUT;
    }
    else if (tagName.startsWith(ANDROIDX_CONSTRAINT_LAYOUT_PKG)) {
      return ANDROIDX_CONSTRAINTLAYOUT;
    }
    else if (tagName.startsWith(ANDROIDX_RECYCLER_VIEW_PKG)) {
      return ANDROIDX_RECYCLERVIEW;
    }
    else if (tagName.startsWith(ANDROIDX_CARD_VIEW_PKG)) {
      return ANDROIDX_CARDVIEW;
    }
    else if (tagName.startsWith(ANDROIDX_GRID_LAYOUT_PKG)) {
      return ANDROIDX_GRIDLAYOUT;
    }
    else if (tagName.startsWith(ANDROIDX_LEANBACK_PKG)) {
      return ANDROIDX_LEANBACK;
    }
    else if (tagName.startsWith(ANDROIDX_CORE_PKG)) {
      return ANDROIDX_LEGACY_SUPPORT_V4;
    }
    else if (tagName.startsWith(ANDROIDX_VIEWPAGER_PKG)) {
      return ANDROIDX_VIEWPAGER;
    }
    else if (tagName.startsWith(ANDROIDX_APPCOMPAT_PKG)) {
      return ANDROIDX_APPCOMPAT;
    }
    else if (tagName.startsWith(ANDROIDX_COORDINATOR_LAYOUT_PKG)) {
      return ANDROIDX_COORDINATORLAYOUT;
    }
    return null;
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
   * @param xmlType how the caller intends to use the XML returned
   * @return the XML for a newly created component
   */
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tagName)
      .toString();
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
    Icon icon = AndroidDomElementDescriptorProvider.getIconForViewTag(getSimpleTagName(tagName));
    return icon != null ? icon : StudioIcons.LayoutEditor.Palette.VIEW;
  }
}
