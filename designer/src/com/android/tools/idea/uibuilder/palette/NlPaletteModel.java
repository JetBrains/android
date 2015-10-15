/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import java.io.InputStream;
import java.util.*;

public class NlPaletteModel {
  private static final Logger LOG = Logger.getInstance(NlPaletteModel.class);

  private static final String METADATA = "palette.xml";

  private static final String ELEM_ITEM = "item";
  private static final String ELEM_PALETTE = "palette";
  private static final String ELEM_CREATION = "creation";
  private static final String ELEM_PRESENTATION = "presentation";

  private static final String ATTR_TAG = "tag";
  private static final String ATTR_ID = "id";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_TITLE = "title";
  private static final String ATTR_TOOLTIP = "tooltip";
  private static final String ATTR_ICON = "icon";
  private static final String ATTR_LIBRARY = "library";

  private final List<NlPaletteGroup> myGroups;
  private final Map<String, NlPaletteItem> myTag2Item;
  private final Set<String> myLibrariesUsed;
  private static NlPaletteModel ourInstance;

  @NonNull
  public static NlPaletteModel get() {
    if (ourInstance == null) {
      ourInstance = new NlPaletteModel();
      ourInstance.loadPalette();
    }
    return ourInstance;
  }

  @NonNull
  public List<NlPaletteGroup> getGroups() {
    return myGroups;
  }

  @NonNull
  public Set<String> getLibrariesUsed() {
    return myLibrariesUsed;
  }

  @Nullable
  public NlPaletteItem getItemByTagName(@NonNull String tagName) {
    return myTag2Item.get(tagName);
  }

  @VisibleForTesting
  NlPaletteModel() {
    myGroups = new ArrayList<NlPaletteGroup>();
    myTag2Item = new HashMap<String, NlPaletteItem>();
    myLibrariesUsed = new HashSet<String>();
  }

  private void loadPalette() {
    Document document = loadDocument(METADATA);
    if (document != null) {
      loadPalette(document);
    }
  }

  @Nullable
  private Document loadDocument(String metadata) {
    try {
      InputStream stream = getClass().getResourceAsStream(metadata);
      Document document = new SAXBuilder().build(stream);
      stream.close();
      return document;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }

  @VisibleForTesting
  void loadPalette(@NonNull Document document) {
    ModelLoader loader = new ModelLoader();
    loader.loadPalette(document);
  }

  private class ModelLoader {

    @VisibleForTesting
    void loadPalette(@NonNull Document document) {
      loadModels(document);
      Element palette = document.getRootElement().getChild(ELEM_PALETTE);
      if (palette == null) {
        LOG.warn("Missing palette tag");
        return;
      }
      for (Element groupElement : palette.getChildren()) {
        NlPaletteGroup group = loadGroup(groupElement);
        if (group != null) {
          myGroups.add(group);
        }
      }
    }

    @Nullable
    private NlPaletteGroup loadGroup(@NonNull Element groupElement) {
      String name = groupElement.getAttributeValue(ATTR_NAME);
      if (name == null) {
        LOG.warn("Group element without a name");
        return null;
      }
      NlPaletteGroup group = new NlPaletteGroup(name);
      for (Element itemElement : groupElement.getChildren(ELEM_ITEM)) {
        String tag = itemElement.getAttributeValue(ATTR_TAG);
        if (tag == null) {
          LOG.warn(String.format("Item without a tag for group: %s", name));
          continue;
        }
        NlPaletteItem base = myTag2Item.get(tag);
        if (base == null) {
          LOG.warn(String.format("Model not found for group: %s with tag: %s", name, tag));
          continue;
        }
        NlPaletteItem item = loadItem(tag, itemElement, null, base);
        if (item == null) {
          continue;
        }
        group.add(item);
        myLibrariesUsed.addAll(item.getLibraries());
        for (Element subItemElement : itemElement.getChildren(ELEM_ITEM)) {
          NlPaletteItem subItem = loadItem(tag, subItemElement, null, item);
          if (subItem == null) {
            continue;
          }
          group.add(subItem);
        }
      }
      return group;
    }

    @Nullable
    private NlPaletteItem loadItem(@NonNull String tagName, @NonNull Element itemElement, @Nullable Element modelElement,
                                   @Nullable NlPaletteItem base) {
      assert modelElement != null ^ base != null;
      String title = getAttributeValue(itemElement, ATTR_TITLE, base != null ? base.getTitle() : "");
      String tooltip = getAttributeValue(itemElement, ATTR_TOOLTIP, base != null ? base.getTooltip() : "");
      String iconPath = getAttributeValue(itemElement, ATTR_ICON, base != null ? base.getIconPath() : "");
      String id = getAttributeValue(itemElement, ATTR_ID, base != null ? base.getId() : "");
      String libraries = base != null
                         ? Joiner.on(",").join(base.getLibraries())
                         : getAttributeValue(modelElement, ATTR_LIBRARY, "");
      String creation = base != null
                        ? getElementValue(itemElement, ELEM_CREATION, base.getRepresentation())
                        : getElementValue(modelElement, ELEM_CREATION, "");
      String structureTitle = base != null ? base.getStructureTitle() : title;
      String format = base != null ? base.getStructureFormat() : getFormatValue(modelElement);
      if (title.isEmpty()) {
        LOG.warn(String.format("No title found for item with tag: %s", tagName));
        return null;
      }
      if (creation.isEmpty()) {
        creation = "<" + tagName +"/>";
      }
      if (id.isEmpty()) {
        id = tagName;
      }
      return new NlPaletteItem(title, iconPath, tooltip, creation, id, libraries, structureTitle, format);
    }

    @NonNull
    private String getAttributeValue(@NonNull Element itemElement, @NonNull String attributeName, @NonNull String defaultValue) {
      String value = itemElement.getAttributeValue(attributeName);
      return value != null ? value : defaultValue;
    }

    @Nullable
    private String getFormatValue(@NonNull Element modelElement) {
      Element presentationElement = modelElement.getChild(ELEM_PRESENTATION);
      if (presentationElement == null) {
        return null;
      }
      return presentationElement.getAttributeValue(ATTR_TITLE);
    }

    @NonNull
    private String getElementValue(@NonNull Element fromElement, @NonNull String tagName, @NonNull String defaultValue) {
      Element element = fromElement.getChild(tagName);
      return element != null ? element.getText() : defaultValue;
    }

    private void loadModels(@NonNull Document document) {
      for (Element element : document.getRootElement().getChildren()) {
        String tag = element.getAttributeValue(ATTR_TAG);
        if (tag != null) {
          Element paletteElement = element.getChild(ELEM_PALETTE);
          if (paletteElement == null) {
            LOG.warn(String.format("Palette not found on model with tag: %s", tag));
          } else {
            myTag2Item.put(tag, loadItem(tag, paletteElement, element, null));
          }
        }
      }
    }
  }
}
