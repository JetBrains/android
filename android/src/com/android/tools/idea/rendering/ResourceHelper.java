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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION;

public class ResourceHelper {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ResourceHelper");
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

  /**
   * Tries to resolve the given resource value to an actual RGB color. For state lists
   * it will pick the simplest/fallback color.
   *
   * @param resources the resource resolver to use to follow color references
   * @param color the color to resolve
   * @return the corresponding {@link Color} color, or null
   */
  @Nullable
  public static Color resolveColor(@NotNull ResourceResolver resources, @Nullable ResourceValue color) {
    if (color != null) {
      color = resources.resolveResValue(color);
    }
    if (color == null) {
      return null;
    }
    String value = color.getValue();

    int depth = 0;
    while (value != null && depth < MAX_RESOURCE_INDIRECTION) {
      if (value.startsWith("#")) {
        return parseColor(value);
      }
      if (value.startsWith(PREFIX_RESOURCE_REF)) {
        boolean isFramework = color.isFramework();
        color = resources.findResValue(value, isFramework);
        if (color != null) {
          value = color.getValue();
        } else {
          break;
        }
      } else {
        File file = new File(value);
        if (file.exists() && file.getName().endsWith(DOT_XML)) {
          // Parse
          try {
            String xml = Files.toString(file, Charsets.UTF_8);
            Document document = XmlUtils.parseDocumentSilently(xml, true);
            if (document != null) {
              NodeList items = document.getElementsByTagName(TAG_ITEM);

              value = findColorValue(items);
              continue;
            }
          } catch (Exception e) {
            LOG.warn(String.format("Failed parsing color file %1$s", file.getName()), e);
          }
        }

        return null;
      }

      depth++;
    }

    return null;
  }

  /**
   * Searches a color XML file for the color definition element that does not
   * have an associated state and returns its color
   */
  @Nullable
  private static String findColorValue(@NotNull NodeList items) {
    for (int i = 0, n = items.getLength(); i < n; i++) {
      // Find non-state color definition
      Node item = items.item(i);
      boolean hasState = false;
      if (item.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) item;
        if (element.hasAttributeNS(ANDROID_URI, ATTR_COLOR)) {
          NamedNodeMap attributes = element.getAttributes();
          for (int j = 0, m = attributes.getLength(); j < m; j++) {
            Attr attribute = (Attr) attributes.item(j);
            if (attribute.getLocalName().startsWith("state_")) { //$NON-NLS-1$
              hasState = true;
              break;
            }
          }

          if (!hasState) {
            return element.getAttributeNS(ANDROID_URI, ATTR_COLOR);
          }
        }
      }
    }

    return null;
  }

  /**
   * Converts the supported color formats (#rgb, #argb, #rrggbb, #aarrggbb to a Color
   * http://developer.android.com/guide/topics/resources/more-resources.html#Color
   */
  @Nullable
  @VisibleForTesting
  public static Color parseColor(String s) {
    if (StringUtil.isEmpty(s)) {
      return null;
    }

    if (s.charAt(0) == '#') {
      long longColor;
      try {
        longColor = Long.parseLong(s.substring(1), 16);
      }
      catch (NumberFormatException e) {
        return null;
      }

      if (s.length() == 4 || s.length() == 5) {
        long a = s.length() == 4 ? 0xff : extend((longColor & 0xf000) >> 12);
        long r = extend((longColor & 0xf00) >> 8);
        long g = extend((longColor & 0x0f0) >> 4);
        long b = extend((longColor & 0x00f));
        longColor = (a << 24) | (r << 16) | (g << 8) | b;
        return new Color((int)longColor, true);
      }

      if (s.length() == 7) {
        longColor |= 0x00000000ff000000;
      }
      else if (s.length() != 9) {
        return null;
      }
      return new Color((int)longColor, true);
    }

    return null;
  }

  private static long extend(long nibble) {
    return nibble | nibble << 4;
  }
}
