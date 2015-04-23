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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.Color;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.SdkConstants.*;

public class ThemePreviewBuilder {
  /** Namespace schema to use for attributes specific to the preview builder. */
  public static final String BUILDER_URI = "http://schemas.android.com/tools/preview/builder";
  public static final String BUILDER_NS_NAME = "builder";
  /** Attribute to use to specify the ComponentGroup that a specific item in the preview belongs to. */
  public static final String BUILDER_ATTR_GROUP = "group";

  private static final Map<String, String> NAMESPACE_TO_URI = ImmutableMap.of(
    ANDROID_NS_NAME, ANDROID_URI,
    BUILDER_NS_NAME, BUILDER_URI
  );

  private final ArrayList<Predicate<ComponentDefinition>> myComponentFilters = new ArrayList<Predicate<ComponentDefinition>>();

  /**
   * Defines groups for the framework widgets. All the project widgets will go into "Custom".
   */
  public enum ComponentGroup {
    // The order of the groups is important since it will be the one used to display the preview.
    TOOLBAR("App bar", VALUE_VERTICAL),
    RAISED_BUTTON("Raised button", VALUE_VERTICAL),
    FLAT_BUTTON("Flat button", VALUE_VERTICAL),
    FAB_BUTTON("Fab button", VALUE_HORIZONTAL),
    HORIZONTAL_PROGRESSBAR("Horizontal Progressbar", VALUE_HORIZONTAL),
    INDETERMINATE_PROGRESSBAR("Progressbar (indeterminate)", VALUE_HORIZONTAL),
    SLIDERS("Seekbar", VALUE_HORIZONTAL),
    RADIO_BUTTON("Radiobutton", VALUE_HORIZONTAL),
    CHECKBOX("Checkbox", VALUE_HORIZONTAL),
    SWITCH("Switch", VALUE_HORIZONTAL),
    TEXT("TextView", VALUE_VERTICAL),
    NAVIGATION_BAR("Navigation bar", VALUE_HORIZONTAL),
    STATUS_BAR("Status bar", VALUE_HORIZONTAL),
    CUSTOM("Custom", VALUE_VERTICAL),;

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
      this(description, group, name, 0);
    }

    /**
     * Set a component attribute. Most of the possible keys are defined in the {@link SdkConstants} class and they are of the form
     * ATTR_*.
     * @param ns the key namespace.
     * @param key the attribute name.
     * @param value the attribute value.
     */
    @NotNull
    public ComponentDefinition set(@Nullable String ns, @NotNull String key, @NotNull String value) {
      String prefix = (ns != null ? (ns + ":") : "");
      attributes.put(prefix + key, value);

      return this;
    }

    /**
     * Set a component attribute. Most of the possible keys are defined in the {@link SdkConstants} class and they are of the form
     * ATTR_*.
     * @param key the attribute name. The key namespace will default to "android:"
     * @param value the attribute value.
     */
    @NotNull
    public ComponentDefinition set(@NotNull String key, @NotNull String value) {
      set(ANDROID_NS_NAME, key, value);

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
    new ComponentDefinition("Toolbar", ComponentGroup.TOOLBAR, "Toolbar")
      .setApiLevel(21)
      .set(ATTR_TITLE, "Toolbar")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .set("minHeight", "?attr/actionBarSize").set(ATTR_BACKGROUND, "?attr/colorPrimary")
      .addAlias("Actionbar"),

    // Buttons
    new ComponentDefinition("Normal", ComponentGroup.RAISED_BUTTON, BUTTON),
    /*new ComponentDefinition("Pressed",   ComponentGroup.RAISED_BUTTON, BUTTON)
      .set(ATTR_BACKGROUND, "@drawable/abc_btn_default_mtrl_shape"),*/
    new ComponentDefinition("Disabled", ComponentGroup.RAISED_BUTTON, BUTTON)
      .set(ATTR_ENABLED, VALUE_FALSE),

    new ComponentDefinition("Normal", ComponentGroup.FLAT_BUTTON, BUTTON)
      .set(null, ATTR_STYLE, "?android:attr/borderlessButtonStyle"),
    /*new ComponentDefinition("Pressed",   ComponentGroup.FLAT_BUTTON, BUTTON)
      .set(ATTR_BACKGROUND, "@drawable/abc_btn_default_mtrl_shape"),*/
    new ComponentDefinition("Disabled", ComponentGroup.FLAT_BUTTON, BUTTON)
      .set(null, ATTR_STYLE, "?android:attr/borderlessButtonStyle")
      .set(ATTR_ENABLED, VALUE_FALSE),

    new ComponentDefinition("Radio button", ComponentGroup.RADIO_BUTTON, RADIO_BUTTON)
      .setText(""),
    new ComponentDefinition("Pressed Radio button", ComponentGroup.RADIO_BUTTON, RADIO_BUTTON)
      .set(ATTR_CHECKED, VALUE_TRUE).setText(""),

    new ComponentDefinition("Checkbox", ComponentGroup.CHECKBOX, CHECK_BOX)
      .setText(""),
    new ComponentDefinition("Pressed Checkbox", ComponentGroup.CHECKBOX, CHECK_BOX)
      .set(ATTR_CHECKED, VALUE_TRUE).setText(""),


    new ComponentDefinition("Switch", ComponentGroup.SWITCH, SWITCH)
      .setApiLevel(14)
      .setText(""),
    new ComponentDefinition("On Switch", ComponentGroup.SWITCH, SWITCH)
      .setApiLevel(14)
      .set(ATTR_CHECKED, VALUE_TRUE).setText(""),

    // Text,
    new ComponentDefinition("Large text", ComponentGroup.TEXT, TEXT_VIEW).set("textAppearance", "?android:attr/textAppearanceLarge"),
    new ComponentDefinition("Medium text", ComponentGroup.TEXT, TEXT_VIEW).set("textAppearance", "?android:attr/textAppearanceMedium"),
    new ComponentDefinition("Small text", ComponentGroup.TEXT, TEXT_VIEW).set("textAppearance", "?android:attr/textAppearanceSmall"),

    // Bars
    new ComponentDefinition("Status bar", ComponentGroup.STATUS_BAR, "com.android.layoutlib.bridge.bars.StatusBar")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT),
    new ComponentDefinition("Navigation bar", ComponentGroup.NAVIGATION_BAR, "com.android.layoutlib.bridge.bars.NavigationBar")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT),

    // Misc
    new ComponentDefinition("ProgressBar", ComponentGroup.INDETERMINATE_PROGRESSBAR, PROGRESS_BAR)
      .set("progress", "50")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT),
    new ComponentDefinition("ProgressBar", ComponentGroup.HORIZONTAL_PROGRESSBAR, PROGRESS_BAR)
      .set("progress", "50")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .set(null, ATTR_STYLE, "?android:attr/progressBarStyleHorizontal"),
    new ComponentDefinition("SeekBar", ComponentGroup.SLIDERS, SEEK_BAR)
      .set(ATTR_VALUE, "50")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT));

  // All the sizes are defined in pixels so they are not rescaled depending on the selected device dpi.
  private static final int VERTICAL_GROUP_PADDING = 15;
  private static final int LINE_PADDING = 5;
  private static final int GROUP_TITLE_FONT_SIZE = 9;

  private List<ComponentDefinition> myComponents = new ArrayList<ComponentDefinition>();
  private String myGroupHeaderColor = "@android:color/darker_gray";
  private String mySeparatorColor = "@android:color/darker_gray";
  private int mySeparatorHeight = 5;
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
      List<String> keyComponents = Splitter.on(":").limit(2).splitToList(entry.getKey());

      if (keyComponents.size() != 1) {
        component.setAttributeNS(NAMESPACE_TO_URI.get(keyComponents.get(0)), keyComponents.get(1), entry.getValue());
      } else {
        component.setAttribute(entry.getKey(), entry.getValue());
      }
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

    if (def.weight != 0 && !component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT, Integer.toString(def.weight));
    }
    if (!component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) {
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_CENTER);
    }
    if (!component.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN)) {
      // Default box around every component.
      component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN, toDp(5));
    }

    component.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, def.group.name());

    return component;
  }

  @NotNull
  private static Element buildElementGroup(@NotNull Document document, @NotNull ComponentGroup group, @NotNull String verticalPadding) {
    Element elementGroup = document.createElement(LINEAR_LAYOUT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_ORIENTATION, group.orientation);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_GRAVITY, GRAVITY_VALUE_CENTER);

    elementGroup.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, group.name());

    if (VALUE_VERTICAL.equals(group.orientation)) {
      elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, verticalPadding);
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
    return ImmutableList.copyOf(Iterables.filter(myComponents, new Predicate<ComponentDefinition>() {
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
    myComponents.addAll(definitions);

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
    setGroupHeaderColor('#' + Integer.toHexString(color.getRGB()));

    return this;
  }

  public ThemePreviewBuilder setSeparatorColor(@NotNull String color) {
    mySeparatorColor = color;

    return this;
  }

  public ThemePreviewBuilder setSeparatorColor(@NotNull Color color) {
    setSeparatorColor('#' + Integer.toHexString(color.getRGB()));

    return this;
  }

  public ThemePreviewBuilder setSeparatorHeight(int height) {
    mySeparatorHeight = height;
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
    document.appendChild(layout);

    // Iterate over all the possible classes.
    boolean isFirstGroup = true;
    for (ComponentGroup group : ComponentGroup.values()) {
      List<ComponentDefinition> components = getComponentsByGroup(group);
      if (components.isEmpty()) {
        continue;
      }

      if (!isFirstGroup) {
        Element separator = document.createElement(VIEW);
        separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
        separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, toDp(mySeparatorHeight));
        separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, toDp(LINE_PADDING));
        separator.setAttributeNS(ANDROID_URI, ATTR_BACKGROUND, mySeparatorColor);
        separator.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, group.name());

        layout.appendChild(separator);
      } else {
        isFirstGroup = false;
      }

      Element groupTitle = document.createElement(TEXT_VIEW);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_TEXT_SIZE, toSp(GROUP_TITLE_FONT_SIZE));
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, toDp(LINE_PADDING));
      groupTitle.setAttributeNS(ANDROID_URI, "textColor", myGroupHeaderColor);
      groupTitle.setAttributeNS(ANDROID_URI, "text", group.name.toUpperCase());
      groupTitle.setAttributeNS(BUILDER_URI, BUILDER_ATTR_GROUP, group.name());
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN, toDp(LINE_PADDING));

      Element elementGroup = buildElementGroup(document, group, toDp(VERTICAL_GROUP_PADDING));

      layout.appendChild(elementGroup);
      layout.appendChild(groupTitle);

      for (ComponentDefinition definition : components) {
        elementGroup.appendChild(buildComponent(document, definition));
      }
    }

    if (myDebugPrintStream != null) {
      printDebug(myDebugPrintStream, document);
    }

    return document;
  }
}
