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
import com.android.tools.idea.uibuilder.api.PaletteComponentHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.IconLoader;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.io.Reader;
import java.util.*;

/**
 * A {@link Palette} contains a list of palette groups and items.
 * Each item correspond to a component type.
 */
@XmlRootElement(name = "palette")
public class Palette {
  public static final Palette EMPTY = new Palette();

  // @formatter:off
  @XmlElements({
    @XmlElement(name = "group", type = Group.class),
    @XmlElement(name = "item", type = Item.class)
  })
  private final List<BaseItem> myItems;
  // @formatter:on

  private final Map<String, Item> myItemsById;

  private Palette() {
    myItems = new ArrayList<>();
    myItemsById = new HashMap<>();
  }

  /**
   * Handles parsing a palette.xml file. A palette file specifies the layout of the palette.
   */
  public static Palette parse(@NotNull Reader xmlReader, @NotNull ViewHandlerManager manager) throws JAXBException {
    Palette palette = unMarshal(xmlReader);
    palette.accept(item -> item.setUp(palette, manager));
    palette.setParentGroups();
    return palette;
  }

  private void setParentGroups() {
    accept(new Visitor() {
      @Override
      public void visit(@NotNull Item item) {
      }

      @Override
      public void visit(@NotNull Group group) {
        group.getItems().forEach(item -> item.setParent(group));
      }
    });
  }

  @NotNull
  public List<BaseItem> getItems() {
    return myItems;
  }

  @Nullable
  public Item getItemById(@NotNull String id) {
    return myItemsById.get(id);
  }

  @NotNull
  public Set<String> getGradleCoordinateIds() {
    Set<String> gradleCoordinateIds = new HashSet<>();
    accept(item -> item.addGradleCoordinateId(gradleCoordinateIds));
    return gradleCoordinateIds;
  }

  private static Palette unMarshal(@NotNull Reader xmlReader) throws JAXBException {
    Unmarshaller unmarshaller = JAXBContext.newInstance(Palette.class).createUnmarshaller();
    unmarshaller.setEventHandler(event -> {
      throw new RuntimeException(event.getLinkedException());
    });
    return (Palette)unmarshaller.unmarshal(xmlReader);
  }

  public void accept(@NotNull Visitor visitor) {
    for (BaseItem item : myItems) {
      item.accept(visitor);
    }
  }

  /**
   * Interface for a visitor for {@link Group}s and {@link Item}s.
   */
  public interface Visitor {
    @SuppressWarnings("EmptyMethod")
    void visit(@NotNull Item item);

    default void visit(@SuppressWarnings("UnusedParameters") @NotNull Group group) {
    }

    default void visitAfter(@SuppressWarnings("UnusedParameters") @NotNull Group group) {
    }
  }

  /**
   * A {@link BaseItem} is an interface implemented by both {@link Group} and {@link Item}.
   */
  interface BaseItem {
    /**
     * Implementation of the visitor pattern.
     */
    void accept(@NotNull Visitor visitor);

    /**
     * Return the parent group of an item or group.
     */
    @Nullable
    Group getParent();

    /**
     * Set the parent group of this item or group.
     */
    void setParent(@NotNull Group group);
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
    private List<BaseItem> myItems = new ArrayList<>();
    // @formatter:on

    @Nullable
    private Group myParent;

    // Needed for JAXB
    private Group() {
    }

    public Group(@NotNull String name) {
      myName = name;
    }

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
    @Nullable
    public Group getParent() {
      return myParent;
    }

    @Override
    public void setParent(@NotNull Group parent) {
      myParent = parent;
    }

    @Override
    public void accept(@NotNull Visitor visitor) {
      visitor.visit(this);
      for (BaseItem item : myItems) {
        item.accept(visitor);
      }
      visitor.visitAfter(this);
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
    private String myGradleCoordinateId;

    @XmlAttribute(name = "handler-class")
    @Nullable
    private String myHandlerClass;

    @XmlAttribute(name = "suggested")
    @Nullable
    private Boolean mySuggested;

    @XmlAttribute(name = "meta")
    @Nullable
    private String myMeta;

    @XmlAttribute(name = "materialReference")
    @Nullable
    private String myMaterialReference;

    @XmlAttribute(name = "info")
    @Nullable
    private String myInfo;

    @XmlElement(name = "xml", type = XmlValuePart.class)
    private XmlValuePart myXmlValuePart;

    @Language("XML")
    @Nullable
    private String myXml;

    @XmlElement(name = "drag-preview")
    @Language("XML")
    @Nullable
    private String myDragPreviewXml;

    @Nullable
    private Group myParent;

    private List<String> myMetaTags;

    private PaletteComponentHandler myHandler;

    // Needed for JAXB
    private Item() {
    }

    public Item(@NotNull String tagName, @NotNull ViewHandler handler) {
      myTagName = tagName;
      myHandler = handler;
    }

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
        Icon icon = IconLoader.findIcon(myIconName, getClass().getClassLoader());
        if (icon != null) {
          return icon;
        }
      }
      return myHandler.getIcon(myTagName);
    }

    @NonNull
    public String getGradleCoordinateId() {
      if (myGradleCoordinateId != null) {
        return myGradleCoordinateId;
      }
      return myHandler.getGradleCoordinateId(myTagName);
    }

    public boolean isSuggested() {
      if (mySuggested != null) {
        return mySuggested;
      }
      return false;
    }

    @NotNull
    public List<String> getMetaTags() {
      return myMetaTags;
    }

    @Nullable
    public String getMaterialReference() {
      return myMaterialReference;
    }

    @Nullable
    public String getInfo() {
      return myInfo;
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
    public String getDragPreviewXml() {
      if (myDragPreviewXml != null) {
        return myDragPreviewXml;
      }
      return myHandler.getXml(myTagName, XmlType.DRAG_PREVIEW);
    }

    @Override
    @Nullable
    public Group getParent() {
      return myParent;
    }

    @Override
    public void setParent(@NotNull Group parent) {
      myParent = parent;
    }

    @Override
    public void accept(@NotNull Visitor visitor) {
      visitor.visit(this);
    }

    void setUp(@NotNull Palette palette, @NotNull ViewHandlerManager manager) {
      resolve();
      initHandler(manager);
      palette.myItemsById.put(getId(), this);
    }

    private void resolve() {
      if (myXmlValuePart != null) {
        myXml = myXmlValuePart.getValue();
        if (myDragPreviewXml == null && myXmlValuePart.reuseForDragPreview()) {
          myDragPreviewXml = myXml;
        }
        myXmlValuePart = null; // No longer used
      }
      if (myMetaTags == null) {
        if (myMeta == null || myMeta.trim().isEmpty()) {
          myMetaTags = Collections.emptyList();
        }
        else if (myMeta.indexOf(',') < 0) {
          myMetaTags = Collections.singletonList(myMeta.trim());
        }
        else {
          myMetaTags = Splitter.on(",").trimResults().splitToList(myMeta);
        }
      }
    }

    private void initHandler(@NotNull ViewHandlerManager manager) {
      if (myHandlerClass != null) {
        try {
          myHandler = (PaletteComponentHandler)Class.forName(myHandlerClass).newInstance();
        }
        catch (ReflectiveOperationException exception) {
          myHandler = ViewHandlerManager.NONE;
        }
      }
      else {
        myHandler = manager.getHandlerOrDefault(myTagName);
      }
    }

    private void addGradleCoordinateId(@NotNull Set<String> coordinateIds) {
      String coordinateId = getGradleCoordinateId();

      if (!coordinateId.isEmpty()) {
        coordinateIds.add(coordinateId);
      }
    }

    @NotNull
    @Override
    public String toString() {
      return getTitle();
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

    public boolean reuseForDragPreview() {
      return myReuse != null && Splitter.on(",").trimResults().splitToList(myReuse).contains("drag-preview");
    }
  }
}
