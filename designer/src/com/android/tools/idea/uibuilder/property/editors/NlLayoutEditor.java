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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.handlers.ui.RotatedLabel;
import com.android.tools.idea.uibuilder.property.NlFlagPropertyItem;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.assetstudiolib.AndroidVectorIcons.LayoutEditorIcons.*;

public class NlLayoutEditor extends JPanel {
  private enum ControlGroup {CONSTRAINT_MARGIN, LAYOUT_GRAVITY, LAYOUT_MARGIN, PADDING, GRAVITY, LAYOUT}

  private static final Font SMALL_FONT = UIUtil.getLabelFont().deriveFont(JBUI.scale(8.0f));
  private static final JBColor PURPLE = new JBColor(new Color(180, 167, 214), new Color(132, 119, 166));
  private static final JBColor ORANGE = new JBColor(new Color(249, 203, 156), new Color(183, 149, 114));
  private static final JBColor GREEN = new JBColor(new Color(182, 215, 168), new Color(134, 158, 123));
  private static final JBColor RED = new JBColor(new Color(234, 153, 153), new Color(172, 112, 112));
  private static final JBColor BLUE = new JBColor(new Color(153, 204, 255), new Color(0, 89, 179));
  private static final JBColor YELLOW = new JBColor(new Color(255, 252, 170), new Color(187, 187, 0));
  private static final GravityDisplayTable myNorthGravityTable = new GravityDisplayTable(ATTR_GRAVITY, GRAVITY_VALUE_TOP);
  private static final GravityDisplayTable mySouthGravityTable = new GravityDisplayTable(ATTR_GRAVITY, GRAVITY_VALUE_BOTTOM);
  private static final GravityDisplayTable myWestGravityTable = new GravityDisplayTable(ATTR_GRAVITY, GRAVITY_VALUE_START);
  private static final GravityDisplayTable myEastGravityTable = new GravityDisplayTable(ATTR_GRAVITY, GRAVITY_VALUE_END);
  private static final GravityDisplayTable myNorthLayoutGravityTable = new GravityDisplayTable(ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_TOP);
  private static final GravityDisplayTable mySouthLayoutGravityTable = new GravityDisplayTable(ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_BOTTOM);
  private static final GravityDisplayTable myWestLayoutGravityTable = new GravityDisplayTable(ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_START);
  private static final GravityDisplayTable myEastLayoutGravityTable = new GravityDisplayTable(ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_END);
  private static final Dimension ourFontSize = measureFontSize();

  private final List<CardinalDirectionControls> myControls = new ArrayList<>();
  private Map<String, NlProperty> myProperties;

  public NlLayoutEditor() {
    super(new BorderLayout());
    JPanel constraintMargin = createPanel(ControlGroup.CONSTRAINT_MARGIN, " ", PURPLE);
    JPanel layoutGravity = createPanel(ControlGroup.LAYOUT_GRAVITY, "Layout Gravity", ORANGE);
    JPanel layoutMargin = createPanel(ControlGroup.LAYOUT_MARGIN, "Layout Margin", GREEN);
    JPanel padding = createPanel(ControlGroup.PADDING, "Padding", BLUE);
    JPanel gravity = createPanel(ControlGroup.GRAVITY, "Gravity", RED);
    JPanel layout = createPanel(ControlGroup.LAYOUT, "Layout", YELLOW);

    gravity.add(layout, BorderLayout.CENTER);
    padding.add(gravity, BorderLayout.CENTER);
    layoutMargin.add(padding, BorderLayout.CENTER);
    layoutGravity.add(layoutMargin, BorderLayout.CENTER);
    constraintMargin.add(layoutGravity, BorderLayout.CENTER);
    add(constraintMargin, BorderLayout.CENTER);
  }

  public void setProperties(@NotNull Map<String, NlProperty> properties) {
    myProperties = properties;
  }

  public void refresh() {
    myControls.stream().forEach(this::refresh);
  }

  private void refresh(@NotNull CardinalDirectionControls controls) {
    switch (controls.myGroup) {
      case LAYOUT:
        controls.myEast.setText(getValue(ATTR_LAYOUT_HEIGHT));
        controls.mySouth.setText(getValue(ATTR_LAYOUT_WIDTH));
        break;
      case GRAVITY:
        controls.myNorth.setIcon(myNorthGravityTable.getValue(myProperties));
        controls.mySouth.setIcon(mySouthGravityTable.getValue(myProperties));
        controls.myEast.setIcon(myEastGravityTable.getValue(myProperties));
        controls.myWest.setIcon(myWestGravityTable.getValue(myProperties));
        break;
      case PADDING:
        controls.myNorth.setText(getValue(ATTR_PADDING, ATTR_PADDING_TOP));
        controls.mySouth.setText(getValue(ATTR_PADDING, ATTR_PADDING_BOTTOM));
        controls.myEast.setText(getValue(ATTR_PADDING, ATTR_PADDING_END, ATTR_PADDING_RIGHT));
        controls.myWest.setText(getValue(ATTR_PADDING, ATTR_PADDING_START, ATTR_PADDING_LEFT));
        break;
      case LAYOUT_GRAVITY:
        controls.myNorth.setIcon(myNorthLayoutGravityTable.getValue(myProperties));
        controls.mySouth.setIcon(mySouthLayoutGravityTable.getValue(myProperties));
        controls.myEast.setIcon(myEastLayoutGravityTable.getValue(myProperties));
        controls.myWest.setIcon(myWestLayoutGravityTable.getValue(myProperties));
        break;
      case LAYOUT_MARGIN:
        controls.myNorth.setText(getValue(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_TOP));
        controls.mySouth.setText(getValue(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_BOTTOM));
        controls.myEast.setText(getValue(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_RIGHT));
        controls.myWest.setText(getValue(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_LEFT));
        break;
      case CONSTRAINT_MARGIN:
        controls.myNorth.setText(getValue(ATTR_LAYOUT_TOP_MARGIN));
        controls.mySouth.setText(getValue(ATTR_LAYOUT_BOTTOM_MARGIN));
        controls.myEast.setText(getValue(ATTR_LAYOUT_RIGHT_MARGIN));
        controls.myWest.setText(getValue(ATTR_LAYOUT_LEFT_MARGIN));
        break;
    }
  }

  /**
   * Return the value to display for a certain direction.
   * @param attributes the attributes in order of dominance
   * @return the property value
   */
  @NotNull
  private String getValue(@NotNull String... attributes) {
    for (String attribute : attributes) {
      NlProperty property = myProperties.get(attribute);
      String value = property != null ? property.getValue() : null;
      if (!StringUtil.isEmpty(value)) {
        return property.resolveValue(value);
      }
    }
    return " ";
  }

  private JPanel createPanel(@NotNull ControlGroup group, @NotNull String name, @NotNull Color color) {
    CardinalDirectionControls controls = new CardinalDirectionControls(group);
    myControls.add(controls);
    SpringLayout layout = new SpringLayout();
    JPanel header = new JPanel(layout);
    header.setOpaque(false);
    JLabel label = new JLabel(name);
    label.setFont(SMALL_FONT);
    JLabel north = controls.myNorth;
    layout.putConstraint(SpringLayout.NORTH, label, 0, SpringLayout.NORTH, header);
    layout.putConstraint(SpringLayout.WEST, label, 0, SpringLayout.WEST, header);
    layout.putConstraint(SpringLayout.NORTH, north, 0, SpringLayout.NORTH, header);
    layout.putConstraint(SpringLayout.HORIZONTAL_CENTER, north, 0, SpringLayout.HORIZONTAL_CENTER, header);
    header.add(label);
    header.add(north);
    header.setPreferredSize(ourFontSize);
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(color);
    panel.add(header, BorderLayout.NORTH);
    panel.add(controls.myEast, BorderLayout.EAST);
    panel.add(controls.mySouth, BorderLayout.SOUTH);
    panel.add(controls.myWest, BorderLayout.WEST);
    return panel;
  }

  private static Dimension measureFontSize() {
    JLabel label = new JLabel(" ");
    label.setFont(SMALL_FONT);
    return label.getPreferredSize();
  }

  /**
   * Declarative description of how a gravity attribute should be displayed in the {@link NlLayoutEditor}.
   * Each gravity attribute is described with a {@link GravityDisplayTable} for each of the 4 cardinal
   * directions: north, east, south, and west.
   */
  private static class GravityDisplayTable {
    private final String myAttribute;
    private final String myValue;
    private final String myCenterValue;
    private final Icon myDefaultIcon;
    private final Icon myGravityOnIcon;

    @NotNull
    public Icon getValue(@NotNull Map<String, NlProperty> properties) {
      return isGravityOn(getProperty(properties)) ? myGravityOnIcon : myDefaultIcon;
    }

    private GravityDisplayTable(@NotNull String attribute, @NotNull String value) {
      myAttribute = attribute;
      myValue = value;
      switch (value) {
        case GRAVITY_VALUE_TOP:
          myCenterValue = GRAVITY_VALUE_CENTER_VERTICAL;
          myGravityOnIcon = ArrowUp;
          myDefaultIcon = UnSetUp;
          break;
        case GRAVITY_VALUE_BOTTOM:
          myCenterValue = GRAVITY_VALUE_CENTER_VERTICAL;
          myGravityOnIcon = ArrowDown;
          myDefaultIcon = UnSetDown;
          break;
        case GRAVITY_VALUE_START:
          myCenterValue = GRAVITY_VALUE_CENTER_HORIZONTAL;
          myGravityOnIcon = ArrowUp;
          myDefaultIcon = UnSetUp;
          break;
        case GRAVITY_VALUE_END:
          myCenterValue = GRAVITY_VALUE_CENTER_HORIZONTAL;
          myGravityOnIcon = ArrowDown;
          myDefaultIcon = UnSetDown;
          break;
        default:
          throw new IllegalArgumentException(value);
      }
    }

    @Nullable
    private NlFlagPropertyItem getProperty(@NotNull Map<String, NlProperty> properties) {
      NlProperty property = properties.get(myAttribute);
      return property instanceof NlFlagPropertyItem ? (NlFlagPropertyItem)property : null;
    }

    private boolean isGravityOn(@Nullable NlFlagPropertyItem property) {
      if (property == null) {
        return false;
      }
      return property.isAnyItemSet(myValue, myCenterValue, GRAVITY_VALUE_CENTER);
    }
  }

  /**
   * This class holds a {@link JLabel} for each of the 4 cardinal directions: north, east, south, and west.
   * The labels for east and west are rotated 90 degrees which allows for more efficient use of space for
   * vertical values.
   */
  private static class CardinalDirectionControls {
    private final JLabel myNorth = new JLabel();
    private final JLabel myEast = new RotatedLabel();
    private final JLabel mySouth = new JLabel();
    private final JLabel myWest = new RotatedLabel();

    private final ControlGroup myGroup;

    public CardinalDirectionControls(@NotNull ControlGroup group) {
      myGroup = group;
      myNorth.setFont(SMALL_FONT);
      myEast.setFont(SMALL_FONT);
      myWest.setFont(SMALL_FONT);
      mySouth.setFont(SMALL_FONT);
      mySouth.setHorizontalAlignment(SwingConstants.CENTER);
    }
  }
}
