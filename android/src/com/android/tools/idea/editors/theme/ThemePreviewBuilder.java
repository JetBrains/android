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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;
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
  private static final LoremGenerator ourLoremGenerator = new LoremGenerator();

  public enum ComponentGroup {
    TEXT("Text", VALUE_VERTICAL),
    EDIT("Edit", VALUE_VERTICAL),
    BUTTONS("Buttons", VALUE_HORIZONTAL),
    TOOLBAR("Toolbar", VALUE_VERTICAL),
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

  public static class ComponentDefinition {
    private static AtomicInteger ourCounter = new AtomicInteger(0);

    private final int id;
    final String description;
    final ComponentGroup group;
    final String name;

    final HashMap<String, String> attributes = new HashMap<String, String>();
    int apiLevel = 0;

    public ComponentDefinition(String description, ComponentGroup group, String name) {
      id = ourCounter.incrementAndGet();

      this.description = description;
      this.name = name;
      this.group = group;
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
     * Set the API level this component is present in. The component won't be displayed for API levels lower than the specified one.
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
  }

  public static final List<ComponentDefinition> AVAILABLE_BASE_COMPONENTS = ImmutableList.of(
    // Toolbar
    new ComponentDefinition("Toolbar",        ComponentGroup.TOOLBAR, "Toolbar")
      .setApiLevel(21)
      .set(ATTR_TITLE, "Toolbar")
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .set("minHeight", "?attr/actionBarSize")
      .set(ATTR_BACKGROUND, "?attr/colorPrimary"),

    // Buttons
    new ComponentDefinition("Button",         ComponentGroup.BUTTONS, BUTTON),
    new ComponentDefinition("Small button",   ComponentGroup.BUTTONS, BUTTON).set(ATTR_STYLE, "?android:attr/buttonStyleSmall"),
    new ComponentDefinition("Toggle button",  ComponentGroup.BUTTONS, TOGGLE_BUTTON).setText(""),
    new ComponentDefinition("Radio button",   ComponentGroup.BUTTONS, RADIO_BUTTON).setText(""),
    new ComponentDefinition("Checkbox",       ComponentGroup.BUTTONS, CHECK_BOX).setText(""),
    //new ComponentDefinition("Switch", ComponentGroup.BUTTONS, SWITCH).setText(""),

    // Text
    new ComponentDefinition("New text",       ComponentGroup.TEXT, TEXT_VIEW),
    new ComponentDefinition("Large text",     ComponentGroup.TEXT, TEXT_VIEW).set("textAppearance", "?android:attr/textAppearanceLarge"),
    new ComponentDefinition("Medium text",    ComponentGroup.TEXT, TEXT_VIEW).set("textAppearance", "?android:attr/textAppearanceMedium"),
    new ComponentDefinition("Small text",     ComponentGroup.TEXT, TEXT_VIEW).set("textAppearance", "?android:attr/textAppearanceSmall"),

    // Edit
    new ComponentDefinition("Input text",     ComponentGroup.EDIT, EDIT_TEXT),
    new ComponentDefinition("Long text",      ComponentGroup.EDIT, EDIT_TEXT)
      .set(ATTR_INPUT_TYPE, "textLongMessage").setText(ourLoremGenerator.generate(50, true)),
    new ComponentDefinition("Long text",      ComponentGroup.EDIT, EDIT_TEXT)
      .set(ATTR_INPUT_TYPE, "textMultiLine").setText(ourLoremGenerator.generate(50, true)),

    // Misc
    new ComponentDefinition("ProgressBar",    ComponentGroup.OTHER, PROGRESS_BAR),
    new ComponentDefinition("ProgressBar",    ComponentGroup.OTHER, PROGRESS_BAR)
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .set(ATTR_STYLE, "?android:attr/progressBarStyleSmall"),
    new ComponentDefinition("ProgressBar",    ComponentGroup.OTHER, PROGRESS_BAR)
      .set(ATTR_STYLE, "?android:attr/progressBarStyleHorizontal"),
    new ComponentDefinition("SeekBar",        ComponentGroup.OTHER, SEEK_BAR)
      .set(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT),
    new ComponentDefinition("RatingBar",      ComponentGroup.OTHER, "RatingBar"),
    new ComponentDefinition("Spinner",        ComponentGroup.OTHER, SPINNER)
  );

  // All the sizes are defined in pixels so they are not rescaled depending on the selected device dpi.
  static final int VERTICAL_GROUP_PADDING = 15;
  static final int LINE_PADDING = 5;
  static final int GROUP_TITLE_FONT_SIZE = 12;
  static final int GROUP_PADDING = 45;

  private List<ComponentDefinition> myAdditionalComponents;
  private int myApiLevel = Integer.MAX_VALUE;
  private double myScale = 1;
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

    component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT, "1");
    component.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN, "5dp");

    return component;
  }

  @NotNull
  private static Element buildElementGroup(@NotNull Document document, @NotNull ComponentGroup group, @NotNull String verticalPadding) {
    Element elementGroup = document.createElement(LINEAR_LAYOUT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_CENTER_VERTICAL);
    elementGroup.setAttributeNS(ANDROID_URI, ATTR_ORIENTATION, group.orientation);

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
        return (input.apiLevel <= myApiLevel) && group.equals(input.group);
      }
    }));
  }

  /**
   * Returns the passed number as a string with format %dpx. The set scaling factor is applied to the passed number before returning it.
   */
  private String toPx(int n) {
    return String.format("%d" + UNIT_PX, (int)(n * myScale));
  }

  /**
   * Sets a scaling factor that will be applied to the group fonts and paddings (but not to the controls displayed themselves).
   */
  public ThemePreviewBuilder setScale(double scale) {
    myScale = scale;

    return this;
  }

  /**
   * Filter controls that were added after the given API level.
   */
  @NotNull
  public ThemePreviewBuilder setApiLevel(int apiLevel) {
    myApiLevel = apiLevel;

    return this;
  }

  @NotNull
  public ThemePreviewBuilder addAllComponents(@NotNull List<ComponentDefinition> definitions) {
    if (myAdditionalComponents == null) {
      myAdditionalComponents = new ArrayList<ComponentDefinition>();
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
    myGroupHeaderColor = "#" + Integer.toHexString(color.getRGB());

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
    layout.setAttributeNS(ANDROID_URI, ATTR_PADDING, toPx(LINE_PADDING));
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
        padding.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, toPx(GROUP_PADDING));
        layout.appendChild(padding);
      } else {
        isFirstGroup = false;
      }

      Element separator = document.createElement(VIEW);
      separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
      separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "1px");
      separator.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_TOP, toPx(LINE_PADDING));
      separator.setAttributeNS(ANDROID_URI, ATTR_BACKGROUND, myGroupHeaderColor);

      Element groupTitle = document.createElement(TEXT_VIEW);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_TEXT_SIZE, toPx(GROUP_TITLE_FONT_SIZE));
      groupTitle.setAttributeNS(ANDROID_URI, ATTR_LAYOUT_MARGIN_BOTTOM, toPx(LINE_PADDING));
      groupTitle.setAttributeNS(ANDROID_URI, "textColor", myGroupHeaderColor);
      groupTitle.setAttributeNS(ANDROID_URI, "text", group.name());

      Element elementGroup = buildElementGroup(document, group, toPx(VERTICAL_GROUP_PADDING));

      layout.appendChild(separator);
      layout.appendChild(groupTitle);
      layout.appendChild(elementGroup);

      int elementCounter = 1;
      for (ComponentDefinition definition : components) {
        elementGroup.appendChild(buildComponent(document, definition));

        // Break layout for big groups.
        // TODO: Make the number of elements per row configurable.
        if (elementCounter++ % 3 == 0) {
          elementGroup = buildElementGroup(document, group, toPx(VERTICAL_GROUP_PADDING));
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
