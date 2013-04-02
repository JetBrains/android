/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

public class ResourceHelper {
  /**
   * Returns true if the given style represents a project theme
   *
   * @param style a theme style string
   * @return true if the style string represents a project theme, as opposed
   *         to a framework theme
   */
  public static boolean isProjectStyle(@NotNull String style) {
    assert style.startsWith(STYLE_RESOURCE_PREFIX) || style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) : style;

    return style.startsWith(STYLE_RESOURCE_PREFIX);
  }

  /**
   * Returns the theme name to be shown for theme styles, e.g. for "@style/Theme" it
   * returns "Theme"
   *
   * @param style a theme style string
   * @return the user visible theme name
   */
  @NotNull
  public static String styleToTheme(@NotNull String style) {
    if (style.startsWith(STYLE_RESOURCE_PREFIX)) {
      style = style.substring(STYLE_RESOURCE_PREFIX.length());
    }
    else if (style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      style = style.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
    }
    else if (style.startsWith(PREFIX_RESOURCE_REF)) {
      // @package:style/foo
      int index = style.indexOf('/');
      if (index != -1) {
        style = style.substring(index + 1);
      }
    }
    return style;
  }

  /**
   * Is this a resource that can be defined in any file within the "values" folder?
   * <p/>
   * Some resource types can be defined <b>both</b> as a separate XML file as well
   * as defined within a value XML file. This method will return true for these types
   * as well. In other words, a ResourceType can return true for both
   * {@link #isValueBasedResourceType} and {@link #isFileBasedResourceType}.
   *
   * @param type the resource type to check
   * @return true if the given resource type can be represented as a value under the
   *         values/ folder
   */
  public static boolean isValueBasedResourceType(@NotNull ResourceType type) {
    List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
    for (ResourceFolderType folderType : folderTypes) {
      if (folderType == ResourceFolderType.VALUES) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns the resource name of the given file.
   * <p>
   * For example, {@code getResourceName(</res/layout-land/foo.xml, false) = "foo"}.
   *
   * @param file the file to compute a resource name for
   * @return the resource name
   */
  @NotNull
  public static String getResourceName(@NotNull VirtualFile file) {
    // Note that we use getBaseName here rather than {@link VirtualFile#getNameWithoutExtension}
    // because that method uses lastIndexOf('.') rather than indexOf('.') -- which means that
    // for a nine patch drawable it would include ".9" in the resource name
    return LintUtils.getBaseName(file.getName());
  }

  /**
   * Returns the resource name of the given file.
   * <p>
   * For example, {@code getResourceName(</res/layout-land/foo.xml, false) = "foo"}.
   *
   * @param file the file to compute a resource name for
   * @return the resource name
   */
  @NotNull
  public static String getResourceName(@NotNull PsiFile file) {
    // See getResourceName(VirtualFile)
    // We're replicating that code here rather than just calling
    // getResourceName(file.getVirtualFile());
    // since file.getVirtualFile can return null
    return LintUtils.getBaseName(file.getName());
  }

  /**
   * Returns the resource URL of the given file. The file <b>must</b> be a valid resource
   * file, meaning that it is in a proper resource folder, and it <b>must</b> be a
   * file-based resource (e.g. layout, drawable, menu, etc) -- not a values file.
   * <p>
   * For example, {@code getResourceUrl(</res/layout-land/foo.xml, false) = "@layout/foo"}.
   *
   * @param file the file to compute a resource url for
   * @return the resource url
   */
  @NotNull
  public static String getResourceUrl(@NotNull VirtualFile file) {
    ResourceFolderType type = ResourceFolderType.getFolderType(file.getParent().getName());
    assert type != null && type != ResourceFolderType.VALUES;
    return PREFIX_RESOURCE_REF + type.getName() + '/' + getResourceName(file);
  }

  /**
   * Is this a resource that is defined in a file named by the resource plus the XML
   * extension?
   * <p/>
   * Some resource types can be defined <b>both</b> as a separate XML file as well as
   * defined within a value XML file along with other properties. This method will
   * return true for these resource types as well. In other words, a ResourceType can
   * return true for both {@link #isValueBasedResourceType} and
   * {@link #isFileBasedResourceType}.
   *
   * @param type the resource type to check
   * @return true if the given resource type is stored in a file named by the resource
   */
  public static boolean isFileBasedResourceType(@NotNull ResourceType type) {
    List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
    for (ResourceFolderType folderType : folderTypes) {
      if (folderType != ResourceFolderType.VALUES) {

        if (type == ResourceType.ID) {
          // The folder types for ID is not only VALUES but also
          // LAYOUT and MENU. However, unlike resources, they are only defined
          // inline there so for the purposes of isFileBasedResourceType
          // (where the intent is to figure out files that are uniquely identified
          // by a resource's name) this method should return false anyway.
          return false;
        }

        return true;
      }
    }

    return false;
  }

  /**
   * Returns all resource variations for the given file
   *
   * @param file resource file, which should be an XML file in one of the
   *            various resource folders, e.g. res/layout, res/values-xlarge, etc.
   * @param includeSelf if true, include the file itself in the list,
   *            otherwise exclude it
   * @return a list of all the resource variations
   */
  public static List<VirtualFile> getResourceVariations(@Nullable VirtualFile file, boolean includeSelf) {
    if (file == null) {
      return Collections.emptyList();
    }

    // Compute the set of layout files defining this layout resource
    List<VirtualFile> variations = new ArrayList<VirtualFile>();
    String name = file.getName();
    VirtualFile parent = file.getParent();
    if (parent != null) {
      VirtualFile resFolder = parent.getParent();
      if (resFolder != null) {
        String parentName = parent.getName();
        String prefix = parentName;
        int qualifiers = prefix.indexOf('-');

        if (qualifiers != -1) {
          parentName = prefix.substring(0, qualifiers);
          prefix = prefix.substring(0, qualifiers + 1);
        } else {
          prefix += '-';
        }
        for (VirtualFile resource : resFolder.getChildren()) {
          String n = resource.getName();
          if ((n.startsWith(prefix) || n.equals(parentName))
              && resource.isDirectory()) {
            VirtualFile variation = resource.findChild(name);
            if (variation != null) {
              if (!includeSelf && file.equals(variation)) {
                continue;
              }
              variations.add(variation);
            }
          }
        }
      }
    }

    return variations;
  }

  /**
   * Returns true if views with the given fully qualified class name need to include
   * their package in the layout XML tag
   *
   * @param fqcn the fully qualified class name, such as android.widget.Button
   * @return true if the full package path should be included in the layout XML element
   *         tag
   */
  public static boolean viewNeedsPackage(String fqcn) {
    return !(fqcn.startsWith(ANDROID_WIDGET_PREFIX) || fqcn.startsWith(ANDROID_VIEW_PKG) || fqcn.startsWith(ANDROID_WEBKIT_PKG));
  }

  @Nullable
  public static Color parseColor(String value) {
    if (value == null || !value.startsWith("#")) {
      return null;
    }
    switch (value.length() - 1) {
      case 3:  // #RGB
        return parseColor(value, 1, false);
      case 4:  // #ARGB
        return parseColor(value, 1, true);
      case 6:  // #RRGGBB
        return parseColor(value, 2, false);
      case 8:  // #AARRGGBB
        return parseColor(value, 2, true);
      default:
        return null;
    }
  }

  private static Color parseColor(String value, int size, boolean isAlpha) {
    int alpha = 255;
    int offset = 1;

    if (isAlpha) {
      alpha = parseInt(value, offset, size);
      offset += size;
    }

    int red = parseInt(value, offset, size);
    offset += size;

    int green = parseInt(value, offset, size);
    offset += size;

    int blue = parseInt(value, offset, size);

    return new Color(red, green, blue, alpha);
  }

  private static int parseInt(String value, int offset, int size) {
    String number = value.substring(offset, offset + size);
    if (size == 1) {
      number += number;
    }
    return Integer.parseInt(number, 16);
  }
}
