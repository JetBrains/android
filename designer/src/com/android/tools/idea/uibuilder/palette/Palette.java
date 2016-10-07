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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.uibuilder.api.PaletteComponentHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.IconLoader;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.bind.*;
import javax.xml.bind.annotation.*;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A {@link Palette} contains a list of palette groups and items.
 * Each item correspond to a component type.
 */
@XmlRootElement(name = "palette")
public class Palette {
  // @formatter:off
  @XmlElements({
    @XmlElement(name = "group", type = Group.class),
    @XmlElement(name = "item", type = Item.class)
  })
  private final List<BaseItem> myItems;
  // @formatter:on

  private final Set<GradleCoordinate> myGradleCoordinates;

  private Palette() {
    myItems = new ArrayList<BaseItem>();
    myGradleCoordinates = new HashSet<GradleCoordinate>();
  }

  /**
   * Handles parsing a palette.xml file. A palette file specifies the layout of the palette.
   */
  public static Palette parse(@NotNull Reader xmlReader, @NotNull ViewHandlerManager manager) throws JAXBException {
    Palette palette = unMarshal(xmlReader);

    palette.resolve(manager);
    palette.addGradleCoordinates(palette.myItems);

    return palette;
  }

  private void addGradleCoordinates(@NotNull Iterable<BaseItem> items) {
    for (Object item : items) {
      if (item instanceof Item) {
        String coordinateAsString = ((Item)item).getGradleCoordinate();

        if (!Strings.isNullOrEmpty(coordinateAsString)) {
          GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateAsString + ":+");

          if (coordinate != null) {
            myGradleCoordinates.add(coordinate);
          }
        }
      }
      else if (item instanceof Group) {
        addGradleCoordinates(((Group)item).getItems());
      }
    }
  }

  @NotNull
  public List<BaseItem> getItems() {
    return myItems;
  }

  @NotNull
  Set<GradleCoordinate> getGradleCoordinates() {
    return myGradleCoordinates;
  }

  private static Palette unMarshal(@NotNull Reader xmlReader) throws JAXBException {
    Unmarshaller unmarshaller = JAXBContext.newInstance(Palette.class).createUnmarshaller();
    unmarshaller.setEventHandler(new ValidationEventHandler() {
      @Override
      public boolean handleEvent(ValidationEvent event) {
        throw new RuntimeException(event.getLinkedException());
      }
    });
    return (Palette)unmarshaller.unmarshal(xmlReader);
  }

  private void resolve(@NotNull ViewHandlerManager manager) {
    for (BaseItem item : myItems) {
      item.resolve(manager);
    }
  }

  /**
   * A {@link BaseItem} is an interface implemented by both {@link Group} and {@link Item}.
   */
  interface BaseItem {
    /**
     * Resolve each {@link Item} contained in the current class to its corresponding {@link PaletteComponentHandler} if any.
     */
    void resolve(@NotNull ViewHandlerManager manager);
  }

  @SuppressWarnings("unused")
  public static class Group implements BaseItem {
    @XmlAttribute(required = true, name = "name")
    @NotNull
    @SuppressWarnings("NullableProblems")
    private String myName;

    // @formatter:off
    @XmlElements({
      @XmlElement(name = "group", type = Group.class),
      @XmlElement(name = "item", type = Item.class)
    })
    private List<BaseItem> myItems = Lists.newArrayList();
    // @formatter:on

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public List<BaseItem> getItems() {
      return myItems;
    }

    @NotNull
    public BaseItem getItem(int index) {
      return myItems.get(index);
    }

    @Override
    public void resolve(@NotNull ViewHandlerManager manager) {
      for (BaseItem item : myItems) {
        item.resolve(manager);
      }
    }

    @NotNull
    @Override
    public String toString() {
      return myName;
    }
  }

  @SuppressWarnings("unused")
  public static class Item implements BaseItem {
    @XmlAttribute(required = true, name = "tag")
    @NotNull
    @SuppressWarnings({"NullableProblems", "unused"})
    private String myTagName;

    @XmlAttribute(name = "id")
    @Nullable
    private String myId;

    @XmlAttribute(name = "title")
    @Nullable
    private String myTitle;

    @XmlAttribute(name = "icon")
    @Nullable
    private String myIconName;

    @XmlAttribute(name = "coordinate")
    @Nullable
    private String myGradleCoordinate;

    @XmlAttribute(name = "scale")
    @Nullable
    private Double myPreviewScale;

    @XmlAttribute(name = "render-separately")
    @Nullable
    private Boolean myPreviewRenderSeparately;

    @XmlElement(name = "xml", type = XmlValuePart.class)
    private XmlValuePart myXmlValuePart;

    @Language("XML")
    @Nullable
    private String myXml;

    @XmlElement(name = "preview")
    @Language("XML")
    @Nullable
    private String myPreviewXml;

    @XmlElement(name = "drag-preview")
    @Language("XML")
    @Nullable
    private String myDragPreviewXml;

    private PaletteComponentHandler myHandler;

    @NotNull
    public String getTagName() {
      return myTagName;
    }

    @NotNull
    public String getId() {
      return myId != null ? myId : myTagName;
    }

    @NotNull
    public String getTitle() {
      if (myTitle != null) {
        return myTitle;
      }
      return myHandler.getTitle(myTagName);
    }

    @NotNull
    public Icon getIcon() {
      if (myIconName != null) {
        Icon icon = IconLoader.findIcon(myIconName, getClass());
        if (icon != null) {
          return icon;
        }
      }
      return myHandler.getIcon(myTagName);
    }

    @Nullable
    public String getGradleCoordinate() {
      if (myGradleCoordinate != null) {
        return myGradleCoordinate;
      }
      return myHandler.getGradleCoordinate(myTagName);
    }

    @NotNull
    @Language("XML")
    public String getXml() {
      if (myXml != null) {
        return myXml;
      }
      return myHandler.getXml(myTagName, XmlType.COMPONENT_CREATION);
    }

    @NotNull
    @Language("XML")
    public String getPreviewXml() {
      if (myPreviewXml != null) {
        return myPreviewXml;
      }
      return myHandler.getXml(myTagName, XmlType.PREVIEW_ON_PALETTE);
    }

    @NotNull
    @Language("XML")
    public String getDragPreviewXml() {
      if (myDragPreviewXml != null) {
        return myDragPreviewXml;
      }
      return myHandler.getXml(myTagName, XmlType.DRAG_PREVIEW);
    }

    public double getPreviewScale() {
      if (myPreviewScale != null) {
        return myPreviewScale;
      }
      return myHandler.getPreviewScale(myTagName);
    }

    public boolean isPreviewRenderedSeparately() {
      if (myPreviewRenderSeparately != null) {
        return myPreviewRenderSeparately;
      }
      return false;
    }

    @Override
    public void resolve(@NotNull ViewHandlerManager manager) {
      myHandler = manager.getHandlerOrDefault(myTagName);
      if (myXmlValuePart != null) {
        myXml = myXmlValuePart.getValue();
        if (myPreviewXml == null && myXmlValuePart.reuseForPreview()) {
          myPreviewXml = addId(myXml);  // The preview must have an ID for custom XML
        }
        if (myDragPreviewXml == null && myXmlValuePart.reuseForDragPreview()) {
          myDragPreviewXml = myXml;
        }
        myXmlValuePart = null; // No longer used
      }
    }

    @Language("XML")
    @Nullable
    private String addId(@Nullable @Language("XML") String xml) {
      if (xml == null || myId == null) {
        return xml;
      }
      int index = xml.indexOf("<" + myTagName);
      if (index < 0) {
        return xml;
      }
      index += 1 + myTagName.length();
      return xml.substring(0, index) + "\n  android:id=\"@+id/" + getId() + "\"\n" + xml.substring(index);
    }

    @NotNull
    @Override
    public String toString() {
      return myTagName;
    }
  }

  @SuppressWarnings("unused")
  public static class XmlValuePart {

    @XmlValue
    @Language("XML")
    @Nullable
    private String myValue;

    @XmlAttribute(name = "reuse")
    private String myReuse;

    @Nullable
    public String getValue() {
      return myValue;
    }

    private boolean reuseFor(@NotNull String part) {
      return myReuse != null && Splitter.on(",").trimResults().splitToList(myReuse).contains(part);
    }

    public boolean reuseForPreview() {
      return reuseFor("preview");
    }

    public boolean reuseForDragPreview() {
      return reuseFor("drag-preview");
    }
  }
}

