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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.PrintStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.SdkConstants.*;

public class ThemePreviewBuilder {
  /** Namespace schema to use for attributes specific to the preview builder. */
  public static final String BUILDER_URI = "http://schemas.android.com/tools/preview/builder";
  /** Attribute to use to specify the ComponentGroup that a specific item in the preview belongs to. */
  public static final String BUILDER_ATTR_GROUP = "group";

  private final ArrayList<Predicate<ComponentDefinition>> myComponentFilters = new ArrayList<Predicate<ComponentDefinition>>();

  /**
   * Defines groups for the framework widgets. All the project widgets will go into "Custom".
   */
  public enum ComponentGroup {
    // The order of the groups is important since it will be the one used to display the preview.
    TOOLBAR("Toolbar", VALUE_VERTICAL),
    BUTTONS("Buttons", VALUE_HORIZONTAL),
    PROGRESS("Progress & activity", VALUE_HORIZONTAL),
    SLIDERS("Sliders/Seekbar", VALUE_HORIZONTAL),
    SWTICHES("Switches/toggle", VALUE_HORIZONTAL),
    TEXT("Text", VALUE_HORIZONTAL),
    OTHER("Misc", VALUE_VERTICAL),
    CUSTOM("Custom", VALUE_VERTICAL);

    final String name;
    final String orientation;

    /**
     * Creates a new component group with the given name and orientation.
     * @param name the group name
     * @param orientation the group orientation. Must be either {@link SdkConstants.VALUE_VERTICAL} or {@link SdkConstants.VALUE_HORIZONTAL}
     */
    ComponentGroup(@NotNull String name, @NotNull String orientation) {
      this.name = name;
      this.orientation = orientation;
    }
  }

  /**
   * Class that contains the definition of an Android widget so it can be displayed on the preview.
   */
  public static class ComponentDefinition {
    private static final AtomicInteger ourCounter = new AtomicInteger(0);

    private final int id;
    final String description;
    final ComponentGroup group;
    final String name;
    final List<String> aliases = new ArrayList<String>();

    final HashMap<String, String> attributes = new HashMap<String, String>();
    private final int weight;
    int apiLevel;

    public ComponentDefinition(String description, ComponentGroup group, String name, int weight) {
      this.id = ourCounter.incrementAndGet();

      this.description = description;
      this.name = name;
      this.group = group;
      this.weight = weight;
    }

    public ComponentDefinition(String description, ComponentGroup group, String name) {
      this.id = ourCounter.incrementAndGet();

      this.description = description;
      this.name = name;
      this.group = group;
      this.weight = 1;
    }

    /**
     * Set a component attribute. Most of the possible keys are defined in the {@link SdkConstants} class and they are of the form
     * ATTR_*.
     * @param key the attribute name.
     * @param value the attribute value.
     */
    @NotNull
    public ComponentDefinition set(@NotNull String key, @NotNull String value) {
      attributes.put(key, value);

      return this;
    }

    /**
     * Set the API level this component is present in. This can be used to filter components that are not available in certain API levels.
     */
    @NotNull
    public ComponentDefinition setApiLevel(int apiLevel) {
      this.apiLevel = apiLevel;

      return this;
    }

    /**
     * Return an Android id string that can be used when serializing the component to XML.
     */
    String getId() {
      return NEW_ID_PREFIX + "widget" + id;
    }

    /**
     * Sets the component text attribute.
     */
    @NotNull
    public ComponentDefinition setText(@NotNull String text) {
      set(ATTR_TEXT, text);
      return this;
    }

    /**
     * Adds a component name alias to help with text search.
     */
    public ComponentDefinition addAlias(@NotNull String text) {
      aliases.add(text);
      return this;
    }
  }

  public static class ApiLevelFilter implements Predicate<ComponentDefinition> {
    private final int myApiLevel;

    public ApiLevelFilter(int apiLevel) {
      myApiLevel = apiLevel;
    }

    @Override
    public boolean apply(ComponentDefinition input) {
      return input.apiLevel <= myApiLevel;
    }
  }

  /**
   * Preview builder filter that returns components matching a certain string.
   */
  public static class SearchFilter implements Predicate<ComponentDefinition> {
    private final boolean myCaseSensitive;
    private final String mySearchTerm;

    public SearchFilter(@NotNull String searchTerm, boolean caseSensitive) {
      myCaseSensitive = caseSensitive;
      mySearchTerm = caseSensitive ? searchTerm : searchTerm.toLowerCase();
    }

    public SearchFilter(@NotNull String searchTerm) {
      this(searchTerm, false);
    }

    @Override
    public boolean apply(ComponentDefinition input) {
      if (Strings.isNullOrEmpty(mySearchTerm)) {
        return true;
      }

      StringBuilder searchString = new StringBuilder(input.name);
      searchString.append(' ').append(Joiner.on(' ').join(input.aliases))
        .append(' ').append(input.description)
        .append(' ').append(input.group.name);

      return myCaseSensitive
             ? searchString.toString().contains(mySearchTerm)
             : searchString.toString().toLowerCase().contains(mySearchTerm);
    }
  }

  // List containing all the pre-defined components that are displayed in the preview.
  public static final List<ComponentDefinition> AVAILABLE_BASE_COMPONENTS = ImmutableList.of(
    // Toolbar
    new ComponentDefinition("Toolbar",        ComponentGroup.TOOLBAR, "Toolbar")
      .setApiLevel(21)
      .set(ATTR_TITLE, "Toolbar")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .set("minHeight", "?attr/actionBarSize")
      .set(ATTR_BACKGROUND, "?attr/colorPrimary")
      .addAlias("Actionbar"),

    // Buttons
    new ComponentDefinition("Button",         ComponentGroup.BUTTONS, BUTTON, 5),
    new ComponentDefinition("Small button",   ComponentGroup.BUTTONS, BUTTON)
      .set(ATTR_STYLE, "?android:attr/buttonStyleSmall"),

    new ComponentDefinition("Radio button",   ComponentGroup.SWTICHES, RADIO_BUTTON, 4)
      .setText(""),
    new ComponentDefinition("Checkbox",       ComponentGroup.SWTICHES, CHECK_BOX, 4)
      .setText(""),
    new ComponentDefinition("Toggle button",  ComponentGroup.SWTICHES, TOGGLE_BUTTON)
      .setText(""),

    // Text,
    new ComponentDefinition("Small text",     ComponentGroup.TEXT, TEXT_VIEW)
      .set("textAppearance", "?android:attr/textAppearanceSmall"),
    new ComponentDefinition("Medium text",    ComponentGroup.TEXT, TEXT_VIEW)
      .set("textAppearance", "?android:attr/textAppearanceMedium"),
    new ComponentDefinition("Large text",     ComponentGroup.TEXT, TEXT_VIEW)
      .set("textAppearance", "?android:attr/textAppearanceLarge"),

    // Edit
    /*new ComponentDefinition("Input text",     ComponentGroup.EDIT, EDIT_TEXT),
    new ComponentDefinition("Long text",      ComponentGroup.EDIT, EDIT_TEXT)
      .set(ATTR_INPUT_TYPE, "textLongMessage").setText(ourLoremGenerator.generate(50, true)),
    new ComponentDefinition("Long text",      ComponentGroup.EDIT, EDIT_TEXT)
      .set(ATTR_INPUT_TYPE, "textMultiLine").setText(ourLoremGenerator.generate(50, true)),*/

    // Misc
    new ComponentDefinition("ProgressBar",    ComponentGroup.PROGRESS, PROGRESS_BAR),
    new ComponentDefinition("ProgressBar",    ComponentGroup.PROGRESS, PROGRESS_BAR)
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .set(ATTR_STYLE, "?android:attr/progressBarStyleSmall"),
    new ComponentDefinition("ProgressBar",    ComponentGroup.PROGRESS, PROGRESS_BAR)
      .set(ATTR_STYLE, "?android:attr/progressBarStyleHorizontal"),
    new ComponentDefinition("SeekBar",        ComponentGroup.SLIDERS, SEEK_BAR)
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
  );

  // All the sizes are defined in pixels so they are not rescaled depending on the selected device dpi.
  private static final int VERTICAL_GROUP_PADDING = 15;
  private static final int LINE_PADDING = 5;
  private static final int GROUP_TITLE_FONT_SIZE = 9;
  private static final int GROUP_PADDING = 45;

  private List<ComponentDefinition> myAdditionalComponents;
  private String myGroupHeaderColor = "@android:color/darker_gray";
  private PrintStream myDebugPrintStream;

  @NotNull
  private static Element buildMainLayoutElement(@NotNull Document document) {
    Element layout = document.createElement(LINEAR_LAYOUT);
    layout.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    layout.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_MATCH_PARENT);
    layout.setAttributeNS(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL);

    return layout;
  }

  @NotNull
  private static Element buildComponent(@NotNull Document document, @NotNull ComponentDefinition def) {
    Element component = document.createElement(def.name);

    for (Map.Entry<String, String> entry : def.attributes.entrySet()) {
      component.setAttributeNS(ANDROID_URI, entry.getKey(), entry.getValue());
    }

    if (!component.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
      component.setAttributeNS(ANDROID_URI, ATTR_ID, def.getId());
    }

    if (!component.hasAttributeNS(ANDROID_URI, ATTR_TEXT)) {
      component.setAttributeNS(ANDROID_URI, ATTR_TEXT, def.description);
    }
    if (!component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)) {
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
    }
    if (!component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)) {
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    }

    if (!component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_CENTER);
    }

    if (!component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT, Integer.toString(def.weight));
    }
    component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN, "5dp");

    component.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, def.group.name());

    return component;
  }

  @NotNull
  private static Element buildElementGroup(@NotNull Document document, @NotNull ComponentGroup group, @NotNull String verticalPadding) {
    Element elementGroup = document.createElement(LINEAR_LAYOUT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_CENTER_VERTICAL);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_ORIENTATION, group.orientation);

    elementGroup.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, group.name());

    if (VALUE_VERTICAL.equals(group.orientation)) {
      elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, verticalPadding);
      elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, verticalPadding);
    }

    return elementGroup;
  }

  private static void printDebug(@NotNull PrintStream out, @NotNull Document document) {
    try {
      DOMImplementationRegistry reg = DOMImplementationRegistry.newInstance();

      DOMImplementationLS impl = (DOMImplementationLS)reg.getDOMImplementation("LS");
      LSSerializer serializer = impl.createLSSerializer();

      out.println(serializer.writeToString(document));
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace(out);
    }
    catch (InstantiationException e) {
      e.printStackTrace(out);
    }
    catch (IllegalAccessException e) {
      e.printStackTrace(out);
    }
  }

  @NotNull
  private List<ComponentDefinition> getComponentsByGroup(@NotNull final ComponentGroup group) {
    Iterable<ComponentDefinition> components = Iterables.concat(AVAILABLE_BASE_COMPONENTS, myAdditionalComponents != null
                                                                                           ? myAdditionalComponents
                                                                                           : Collections.<ComponentDefinition>emptyList());
    return ImmutableList.copyOf(Iterables.filter(components, new Predicate<ComponentDefinition>() {
      @Override
      public boolean apply(ComponentDefinition input) {
        if (group != input.group) {
          return false;
        }

        for (Predicate<ComponentDefinition> filter : myComponentFilters) {
          if (!filter.apply(input)) {
            return false;
          }
        }
        return true;
      }
    }));
  }

  /**
   * Returns the passed number as a string with format %ddp.
   */
  private static String toDp(int n) {
    return String.format("%d" + UNIT_DP, n);
  }

  /**
   * Returns the passed number as a string with format %dsp.
   */
  private static String toSp(int n) {
    return String.format("%d" + UNIT_SP, n);
  }

  @NotNull
  public ThemePreviewBuilder addComponentFilter(@NotNull Predicate<ComponentDefinition> filter) {
    myComponentFilters.add(filter);

    return this;
  }

  @NotNull
  public ThemePreviewBuilder addAllComponents(@NotNull List<ComponentDefinition> definitions) {
    if (myAdditionalComponents == null) {
      myAdditionalComponents = new ArrayList<ComponentDefinition>(definitions.size());
    }
    myAdditionalComponents.addAll(definitions);

    return this;
  }

  @NotNull
  public ThemePreviewBuilder addComponent(@NotNull ComponentDefinition definition) {
    addAllComponents(ImmutableList.of(definition));

    return this;
  }

  @NotNull
  public ThemePreviewBuilder setGroupHeaderColor(@NotNull String color) {
    myGroupHeaderColor = color;

    return this;
  }

  @NotNull
  public ThemePreviewBuilder setGroupHeaderColor(@NotNull Color color) {
    myGroupHeaderColor = '#' + Integer.toHexString(color.getRGB());

    return this;
  }

  public ThemePreviewBuilder setDebug(@NotNull PrintStream printStream) {
    myDebugPrintStream = printStream;

    return this;
  }

  @NotNull
  public Document build() throws ParserConfigurationException {
    DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document document = documentBuilder.newDocument();

    Element layout = buildMainLayoutElement(document);
    layout.setAttributeNS(ANDROID_URI, ATTR_PADDING, toDp(LINE_PADDING));
    document.appendChild(layout);

    // Iterate over all the possible classes.
    boolean isFirstGroup = true;
    for (ComponentGroup group : ComponentGroup.values()) {
      List<ComponentDefinition> components = getComponentsByGroup(group);
      if (components.isEmpty()) {
        continue;
      }

      if (!isFirstGroup) {
        Element padding = document.createElement(VIEW);
        padding.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
        padding.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, toDp(GROUP_PADDING));
        layout.appendChild(padding);
      } else {
        isFirstGroup = false;
      }

      Element separator = document.createElement(VIEW);
      separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
      separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, toDp(1));
      separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, toDp(LINE_PADDING));
      separator.setAttributeNS(ANDROID_URI, ATTR_BACKGROUND, myGroupHeaderColor);
      separator.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, group.name());

      Element groupTitle = document.createElement(TEXT_VIEW);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_TEXT_SIZE, toSp(GROUP_TITLE_FONT_SIZE));
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, toDp(LINE_PADDING));
      groupTitle.setAttributeNS(ANDROID_URI, "textColor", myGroupHeaderColor);
      groupTitle.setAttributeNS(ANDROID_URI, "text", group.name.toUpperCase());
      groupTitle.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, group.name());

      Element elementGroup = buildElementGroup(document, group, toDp(VERTICAL_GROUP_PADDING));

      layout.appendChild(separator);
      layout.appendChild(groupTitle);
      layout.appendChild(elementGroup);

      int elementCounter = 1;
      for (ComponentDefinition definition : components) {
        elementGroup.appendChild(buildComponent(document, definition));

        // Break layout for big groups.
        // TODO: Make the number of elements per row configurable.
        if (elementCounter++ % 3 == 0) {
          elementGroup = buildElementGroup(document, group, toDp(VERTICAL_GROUP_PADDING));
          layout.appendChild(elementGroup);
        }
      }
    }

    if (myDebugPrintStream != null) {
      printDebug(myDebugPrintStream, document);
    }

    return document;
  }
}
