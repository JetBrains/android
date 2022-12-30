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

import static com.android.SdkConstants.ANDROID_LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NUM_COLUMNS;
import static com.android.SdkConstants.EXPANDABLE_LIST_VIEW;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_AUTO_FIT;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AdapterBinding;
import com.android.ide.common.rendering.api.DataBindingItem;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;

/**
 * Design-time metadata lookup for layouts, such as fragment and AdapterView bindings.
 */
public final class LayoutMetadata {
  /**
   * The default layout to use for list items in expandable list views
   */
  public static final String DEFAULT_EXPANDABLE_LIST_ITEM = "simple_expandable_list_item_2"; //$NON-NLS-1$
  /**
   * The default layout to use for list items in plain list views
   */
  public static final String DEFAULT_LIST_ITEM = "simple_list_item_2"; //$NON-NLS-1$
  /**
   * The default layout to use for list items in spinners
   */
  public static final String DEFAULT_SPINNER_ITEM = "simple_spinner_item"; //$NON-NLS-1$

  /**
   * The property key, included in comments, which references a list item layout
   */
  public static final String KEY_LV_ITEM = "listitem";        //$NON-NLS-1$
  /**
   * The property key, included in comments, which references a list header layout
   */
  public static final String KEY_LV_HEADER = "listheader";    //$NON-NLS-1$
  /**
   * The property key, included in comments, which references a list footer layout
   */
  public static final String KEY_LV_FOOTER = "listfooter";    //$NON-NLS-1$
  /**
   * The property key, included in comments, which references a fragment layout to show
   */
  public static final String KEY_FRAGMENT_LAYOUT = "layout";        //$NON-NLS-1$
  // NOTE: If you add additional keys related to resources, make sure you update the
  // ResourceRenameParticipant

  /**
   * Utility class, do not create instances
   */
  private LayoutMetadata() {
  }

  /**
   * Returns the given property specified in the <b>current</b> element being
   * processed by the given pull parser.
   *
   * @param parser the pull parser, which must be in the middle of processing
   *               the target element
   * @param name   the property name to look up
   * @return the property value, or null if not defined
   */
  @Nullable
  public static String getProperty(@NotNull XmlPullParser parser, @NotNull String name) {
    String value = parser.getAttributeValue(TOOLS_URI, name);
    if (value != null && value.isEmpty()) {
      value = null;
    }

    return value;
  }

  /**
   * Returns the given property of the given DOM node, or null
   *
   * @param node the XML node to associate metadata with
   * @param name the name of the property to look up
   * @return the value stored with the given node and name, or null
   */
  @Nullable
  public static String getProperty(@NotNull Node node, @NotNull String name) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      Element element = (Element)node;
      String value = element.getAttributeNS(TOOLS_URI, name);
      if (value != null && value.isEmpty()) {
        value = null;
      }

      return value;
    }

    return null;
  }

  /**
   * Returns the given property of the given DOM node, or null
   *
   * @param node the XML node to associate metadata with
   * @param name the name of the property to look up
   * @return the value stored with the given node and name, or null
   */
  @Nullable
  public static String getProperty(@NotNull TagSnapshot node, @NotNull String name) {
    String value = node.getAttribute(name, TOOLS_URI);
    if (value != null && value.isEmpty()) {
      value = null;
    }

    return value;
  }

  /**
   * Strips out @layout/ or @android:layout/ from the given layout reference
   */
  private static String stripLayoutPrefix(String layout) {
    if (layout.startsWith(ANDROID_LAYOUT_RESOURCE_PREFIX)) {
      layout = layout.substring(ANDROID_LAYOUT_RESOURCE_PREFIX.length());
    }
    else if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
      layout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
    }

    return layout;
  }

  /**
   * Creates an {@link AdapterBinding} for the given view object, or null if the user
   * has not yet chosen a target layout to use for the given AdapterView.
   *
   * @param viewObject the view object to create an adapter binding for
   * @param map        a map containing tools attribute metadata
   * @return a binding, or null
   */
  @Nullable
  public static AdapterBinding getNodeBinding(@Nullable Object viewObject, @NotNull Map<String, String> map) {
    String header = map.get(KEY_LV_HEADER);
    String footer = map.get(KEY_LV_FOOTER);
    String layout = map.get(KEY_LV_ITEM);
    if (layout != null || header != null || footer != null) {
      int count = 12;
      return getNodeBinding(viewObject, header, footer, layout, count);
    }

    return null;
  }

  /**
   * Creates an {@link AdapterBinding} for the given view object, or null if the user
   * has not yet chosen a target layout to use for the given AdapterView.
   *
   * @param viewObject the view object to create an adapter binding for
   * @param xmlNode    the ui node corresponding to the view object
   * @return a binding, or null
   */
  @Nullable
  public static AdapterBinding getNodeBinding(@Nullable Object viewObject, @NotNull TagSnapshot xmlNode) {
    String header = getProperty(xmlNode, KEY_LV_HEADER);
    String footer = getProperty(xmlNode, KEY_LV_FOOTER);
    String layout = getProperty(xmlNode, KEY_LV_ITEM);
    if (layout != null || header != null || footer != null) {
      int count = 12;
      // If we're dealing with a grid view, multiply the list item count
      // by the number of columns to ensure we have enough items
      if (xmlNode.tagName.endsWith(GRID_VIEW)) {
        String columns = xmlNode.getAttribute(ATTR_NUM_COLUMNS, ANDROID_URI);
        int multiplier = 2;
        if (columns != null && !columns.isEmpty() &&
            !columns.equals(VALUE_AUTO_FIT)) {
          try {
            int c = Integer.parseInt(columns);
            if (c >= 1 && c <= 10) {
              multiplier = c;
            }
          }
          catch (NumberFormatException nufe) {
            // some unexpected numColumns value: just stick with 2 columns for
            // preview purposes
          }
        }
        count *= multiplier;
      }

      return getNodeBinding(viewObject, header, footer, layout, count);
    }

    return null;
  }

  @Nullable
  private static AdapterBinding getNodeBinding(@Nullable Object viewObject,
                                               @Nullable String header,
                                               @Nullable String footer,
                                               @Nullable String layout,
                                               int count) {
    if (layout != null || header != null || footer != null) {
      AdapterBinding binding = new AdapterBinding(count);

      if (header != null) {
        boolean isFramework = header.startsWith(ANDROID_LAYOUT_RESOURCE_PREFIX);
        binding.addHeader(
            new ResourceReference(ResourceNamespace.fromBoolean(isFramework), ResourceType.LAYOUT, stripLayoutPrefix(header)));
      }

      if (footer != null) {
        boolean isFramework = footer.startsWith(ANDROID_LAYOUT_RESOURCE_PREFIX);
        binding.addFooter(
            new ResourceReference(ResourceNamespace.fromBoolean(isFramework), ResourceType.LAYOUT, stripLayoutPrefix(footer)));
      }

      if (layout != null) {
        boolean isFramework = layout.startsWith(ANDROID_LAYOUT_RESOURCE_PREFIX);
        if (isFramework) {
          layout = layout.substring(ANDROID_LAYOUT_RESOURCE_PREFIX.length());
        }
        else if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
          layout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());
        }

        binding.addItem(new DataBindingItem(layout, isFramework, 1));
      }
      else if (viewObject != null) {
        String listFqcn = LayoutlibCallbackImpl.getListAdapterViewFqcn(viewObject.getClass());
        if (listFqcn != null) {
          if (listFqcn.endsWith(EXPANDABLE_LIST_VIEW)) {
            binding.addItem(new DataBindingItem(DEFAULT_EXPANDABLE_LIST_ITEM, true /* isFramework */, 1));
          }
          else {
            binding.addItem(new DataBindingItem(DEFAULT_LIST_ITEM, true /* isFramework */, 1));
          }
        }
      }
      else {
        binding.addItem(new DataBindingItem(DEFAULT_LIST_ITEM, true /* isFramework */, 1));
      }
      return binding;
    }

    return null;
  }

  /**
   * Sets the given property of the given DOM node to a given value, or if null clears
   * the property.
   */
  public static void setProperty(@NotNull final Project project,
                                 @Nullable String title,
                                 @NotNull final XmlFile file,
                                 @NotNull final XmlTag element,
                                 @NotNull final String name,
                                 @Nullable final String namespace,
                                 @Nullable final String value) {

    String capitalizedName = StringUtil.capitalize(name);
    if (title == null) {
      title = String.format(value != null ? "Set %1$s" : "Clear %1$s", capitalizedName);
    }
    WriteCommandAction.writeCommandAction(project, file).withName(title).run(() -> {
      if (value == null) {
        // Clear attribute
        XmlAttribute attribute;
        if (namespace != null) {
          attribute = element.getAttribute(name, namespace);
        }
        else {
          attribute = element.getAttribute(name);
        }
        if (attribute != null) {
          attribute.delete();
        }
      }
      else {
        if (namespace != null) {
          IdeResourcesUtil.ensureNamespaceImported(file, namespace, null);
          element.setAttribute(name, namespace, value);
        }
        else {
          element.setAttribute(name, value);
        }
      }
    });

    // Also set the values on the same elements in any resource variations
    // of the same layout
    // TODO: This should be done after a brief delay, say 50ms
    final List<XmlTag> list = ApplicationManager.getApplication().runReadAction(new Computable<List<XmlTag>>() {
      @Override
      @Nullable
      public List<XmlTag> compute() {
        // Look up the id of the element, if any
        String id = stripIdPrefix(element.getAttributeValue(ATTR_ID, ANDROID_URI));
        if (id.isEmpty()) {
          return null;
        }

        VirtualFile layoutFile = file.getVirtualFile();
        if (layoutFile != null) {
          final List<VirtualFile> variations = IdeResourcesUtil.getResourceVariations(layoutFile, false);
          if (variations.isEmpty()) {
            return null;
          }

          PsiManager manager = PsiManager.getInstance(project);
          List<XmlTag> list = new ArrayList<>();

          for (VirtualFile file : variations) {
            PsiFile psiFile = manager.findFile(file);
            if (psiFile == null) {
              continue;
            }
            for (XmlTag tag : PsiTreeUtil.findChildrenOfType(psiFile, XmlTag.class)) {
              XmlAttribute attribute = tag.getAttribute(ATTR_ID, ANDROID_URI);
              if (attribute == null || attribute.getValue() == null) {
                continue;
              }
              if (attribute.getValue().endsWith(id) && id.equals(stripIdPrefix(attribute.getValue()))) {
                list.add(tag);
                break;
              }
            }
          }

          return list;
        }

        return null;
      }
    });

    if (list != null && !list.isEmpty()) {
      List<PsiFile> affectedFiles = new ArrayList<>();
      for (XmlTag tag : list) {
        PsiFile psiFile = tag.getContainingFile();
        if (psiFile != null) {
          affectedFiles.add(psiFile);
        }
      }
      WriteCommandAction.writeCommandAction(project, affectedFiles.toArray(PsiFile.EMPTY_ARRAY)).withName(title).run(() -> {
        for (XmlTag tag : list) {
          if (value == null) {
            // Clear attribute
            XmlAttribute attribute;
            if (namespace != null) {
              attribute = tag.getAttribute(name, namespace);
            }
            else {
              attribute = tag.getAttribute(name);
            }
            if (attribute != null) {
              attribute.delete();
            }
          }
          else {
            if (namespace != null) {
              IdeResourcesUtil.ensureNamespaceImported(file, namespace, null);
              tag.setAttribute(name, namespace, value);
            }
            else {
              tag.setAttribute(name, value);
            }
          }
        }
      });
    }
  }
}
