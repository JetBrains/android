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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.font.TextAttribute;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.assetstudiolib.AndroidVectorIcons.LayoutEditorIcons.*;

public class NlLayoutEditor extends JPanel {
  private enum ControlGroup {CONSTRAINT_MARGIN, LAYOUT_GRAVITY, LAYOUT_MARGIN, PADDING, GRAVITY, LAYOUT}

  private static final Font SMALL_FONT = UIUtil.getLabelFont().deriveFont(JBUI.scale(8.0f));
  private static final Font SMALL_BOLD_FONT = SMALL_FONT.deriveFont(Font.BOLD);
  private static final Font SMALL_LINK_FONT = SMALL_FONT.deriveFont(ImmutableMap.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
  private static final JBColor PURPLE = new JBColor(new Color(180, 167, 214), new Color(132, 119, 166));
  private static final JBColor ORANGE = new JBColor(new Color(249, 203, 156), new Color(183, 149, 114));
  private static final JBColor GREEN = new JBColor(new Color(182, 215, 168), new Color(134, 158, 123));
  private static final JBColor RED = new JBColor(new Color(234, 153, 153), new Color(172, 112, 112));
  private static final JBColor YELLOW = new JBColor(new Color(255, 252, 170), new Color(187, 187, 0));
  private static final JBColor BLACK = new JBColor(Gray._0, Gray._0);
  private static final JBColor WHITE = new JBColor(Gray._255, Gray._255);
  private static final JBColor LINK_COLOR = JBColor.BLUE;
  private static final Dimension ourFontSize = measureFontSize();
  private static final int MULTI_CLICK_INTERVAL = getDefaultClickInterval();
  private static final String GRAVITY_DISPLAY_TABLE_KEY = "GravityDisplayTable";
  private static final String ATTRIBUTE_DEPENDENCY_KEY = "AttributeDependency";
  private static final String DIRECTION_LABEL_KEY = "DirectionLabel";
  private static final String TOP_TITLE_PREFIX = "Top ";
  private static final String BOTTOM_TITLE_PREFIX = "Bottom ";
  private static final String START_TITLE_PREFIX = "Start ";
  private static final String END_TITLE_PREFIX = "End ";
  private static final String LEFT_TITLE_PREFIX = "Left ";
  private static final String RIGHT_TITLE_PREFIX = "Right ";
  private static final String EDIT_TEXT_VALUE = "edit";
  private static final String EMPTY_TEXT_VALUE = " ";

  private final List<CardinalDirectionControls> myControls = new ArrayList<>();
  private final NlEnumEditor myEnumEditor;
  private final NlReferenceEditor myReferenceEditor;
  private final NlGravityEditor myGravityEditor;
  private final JPanel myConstraintMarginPanel;
  private final JPanel myLayoutGravityPanel;
  private final JPanel myLayoutMarginPanel;
  private final JPanel myPaddingPanel;
  private final JPanel myGravityPanel;
  private final JPanel myLayoutPanel;
  private Map<String, NlProperty> myProperties;
  private NlComponentEditor myActiveEditor;

  public NlLayoutEditor(@NotNull Project project) {
    super(new BorderLayout());
    myConstraintMarginPanel = createPanel(ControlGroup.CONSTRAINT_MARGIN, "Constraint Margin", GREEN);
    myLayoutGravityPanel = createPanel(ControlGroup.LAYOUT_GRAVITY, "Layout Gravity", ORANGE);
    myLayoutMarginPanel = createPanel(ControlGroup.LAYOUT_MARGIN, "Layout Margin", GREEN);
    myPaddingPanel = createPanel(ControlGroup.PADDING, "Padding", PURPLE);
    myGravityPanel = createPanel(ControlGroup.GRAVITY, "Gravity", RED);
    myLayoutPanel = createPanel(ControlGroup.LAYOUT, "Layout", YELLOW);
    myEnumEditor = NlEnumEditor.createForInspector(createEnumListener());
    myReferenceEditor = NlReferenceEditor.createForInspector(project, createReferenceListener());
    myGravityEditor = new NlGravityEditor();
  }

  public NlEnumEditor getEnumPropertyEditor() {
    return myEnumEditor;
  }

  public NlReferenceEditor getReferencePropertyEditor() {
    return myReferenceEditor;
  }

  public NlGravityEditor getGravityEditor() {
    return myGravityEditor;
  }

  public void setSelectedComponents(@NotNull Map<String, NlProperty> properties) {
    myProperties = properties;
    removeAll();
    if (hasProperties(ATTR_GRAVITY)) {
      myPaddingPanel.add(myGravityPanel, BorderLayout.CENTER);
    }
    myLayoutPanel.add(myPaddingPanel, BorderLayout.CENTER);
    if (hasProperties(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_GRAVITY)) {
      myLayoutMarginPanel.add(myLayoutPanel, BorderLayout.CENTER);
      myLayoutGravityPanel.add(myLayoutMarginPanel, BorderLayout.CENTER);
      add(myLayoutGravityPanel, BorderLayout.CENTER);
    }
    else if (hasProperties(ATTR_LAYOUT_TOP_MARGIN, ATTR_LAYOUT_BOTTOM_MARGIN, ATTR_LAYOUT_LEFT_MARGIN, ATTR_LAYOUT_RIGHT_MARGIN)) {
      myConstraintMarginPanel.add(myLayoutPanel, BorderLayout.CENTER);
      add(myConstraintMarginPanel, BorderLayout.CENTER);
    }
  }

  private boolean hasProperties(@NotNull String... propertyNames) {
    for (String propertyName : propertyNames) {
      if (!myProperties.containsKey(propertyName)) {
        return false;
      }
    }
    return true;
  }

  public void refresh() {
    myControls.forEach(control -> control.refresh(myProperties));
    if (myActiveEditor != null) {
      myActiveEditor.refresh();
    }
  }

  public void setHoverAttributes(@NotNull MouseEvent event,
                                 @NotNull Font font,
                                 @NotNull Color foregroundColor,
                                 @NotNull Color backgroundColor) {
    if (event.getSource() instanceof Component) {
      Component component = (Component)event.getSource();
      while (component != null && !component.isOpaque()) {
        component = component.getParent();
      }
      if (component instanceof JComponent) {
        JComponent direction = (JComponent)component;
        Object dependency = direction.getClientProperty(ATTRIBUTE_DEPENDENCY_KEY);
        Object gravityTable = direction.getClientProperty(GRAVITY_DISPLAY_TABLE_KEY);
        if (dependency != null || gravityTable != null) {
          component.setBackground(backgroundColor);
          Object directionLabel = direction.getClientProperty(DIRECTION_LABEL_KEY);
          if (directionLabel instanceof Component) {
            component = (Component)directionLabel;
          }
          if (component instanceof JLabel) {
            JLabel label = (JLabel)component;
            label.setFont(font);
            label.setForeground(foregroundColor);
            if (foregroundColor.equals(LINK_COLOR)) {
              if (label.getText().equals(EMPTY_TEXT_VALUE)) {
                label.setText(EDIT_TEXT_VALUE);
              }
            }
            else {
              if (label.getText().equals(EDIT_TEXT_VALUE)) {
                label.setText(EMPTY_TEXT_VALUE);
              }
            }
          }
        }
      }
    }
  }

  private void startEditing(@NotNull NlComponentEditor editor, @NotNull MouseEvent event) {
    closeEditors();
    Object source = event.getSource();
    if (source == null || !(source instanceof JComponent)) {
      return;
    }
    JComponent component = (JComponent)source;
    AttributeDependency dependency = (AttributeDependency)component.getClientProperty(ATTRIBUTE_DEPENDENCY_KEY);
    if (dependency == null) {
      return;
    }
    NlProperty property = myProperties.get(dependency.getAttribute());
    if (property == null) {
      return;
    }
    editor.setProperty(property);
    editor.setVisible(true);
    editor.requestFocus();
    JLabel editorLabel = editor.getLabel();
    assert editorLabel != null;
    editorLabel.setText(dependency.getTitle());
    editorLabel.setToolTipText(property.getTooltipText());
    editorLabel.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, dependency);
    myActiveEditor = editor;
  }

  private void startEditingGravity(@NotNull MouseEvent event) {
    closeEditors();
    Object source = event.getSource();
    if (source == null || !(source instanceof JComponent)) {
      return;
    }
    JComponent component = (JComponent)source;
    GravityDisplayTable table = (GravityDisplayTable)component.getClientProperty(GRAVITY_DISPLAY_TABLE_KEY);
    if (table == null) {
      return;
    }
    if (table.initEditor(myGravityEditor, myProperties)) {
      myGravityEditor.setVisible(true);
      myActiveEditor = myGravityEditor;
    }
  }

  private void closeEditor(@NotNull NlComponentEditor editor) {
    if (editor == myActiveEditor) {
      editor.setVisible(false);
      myActiveEditor = null;
    }
  }

  private void closeEditors() {
    myEnumEditor.setVisible(false);
    myReferenceEditor.setVisible(false);
    myGravityEditor.setVisible(false);
    myActiveEditor = null;
  }

  private void toggleGravityValue(@NotNull MouseEvent event) {
    closeEditors();
    Object source = event.getSource();
    if (source == null || !(source instanceof JComponent)) {
      return;
    }
    JComponent component = (JComponent)source;
    GravityDisplayTable displayTable = (GravityDisplayTable)component.getClientProperty(GRAVITY_DISPLAY_TABLE_KEY);
    if (displayTable == null) {
      return;
    }
    displayTable.toggleValue(myProperties);
  }

  private JPanel createPanel(@NotNull ControlGroup group, @NotNull String name, @NotNull JBColor color) {
    CardinalDirectionControls controls = new CardinalDirectionControls(group, name, color);
    myControls.add(controls);
    SpringLayout layout = new SpringLayout();
    JPanel header = new JPanel(layout);
    JLabel label = new JLabel(name);
    label.setFont(SMALL_FONT);
    label.setForeground(BLACK);
    JLabel north = controls.myNorth;
    north.setMaximumSize(new Dimension(Integer.MAX_VALUE, ourFontSize.height));
    layout.putConstraint(SpringLayout.NORTH, label, 0, SpringLayout.NORTH, header);
    layout.putConstraint(SpringLayout.WEST, label, 0, SpringLayout.WEST, header);
    layout.putConstraint(SpringLayout.NORTH, north, 0, SpringLayout.NORTH, header);
    layout.putConstraint(SpringLayout.HORIZONTAL_CENTER, north, 0, SpringLayout.HORIZONTAL_CENTER, header);
    header.add(label);
    header.add(north);
    header.setBackground(color);
    header.setOpaque(true);
    north.setOpaque(false);
    header.setPreferredSize(ourFontSize);
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(color);
    panel.add(header, BorderLayout.NORTH);
    panel.add(controls.myEast, BorderLayout.EAST);
    panel.add(controls.mySouth, BorderLayout.SOUTH);
    panel.add(controls.myWest, BorderLayout.WEST);
    MouseListener listener = createMouseListener(group, label, color);
    header.addMouseListener(listener);
    north.addMouseListener(listener);
    controls.mySouth.addMouseListener(listener);
    controls.myEast.addMouseListener(listener);
    controls.myWest.addMouseListener(listener);
    header.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, north.getClientProperty(GRAVITY_DISPLAY_TABLE_KEY));
    header.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, north.getClientProperty(ATTRIBUTE_DEPENDENCY_KEY));
    header.putClientProperty(DIRECTION_LABEL_KEY, north);
    return panel;
  }

  private static Dimension measureFontSize() {
    JLabel label = new JLabel(" ");
    label.setFont(SMALL_FONT);
    return label.getPreferredSize();
  }

  private static int getDefaultClickInterval() {
    Object property = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
    if (property instanceof Integer) {
      return (Integer)property;
    }
    return 300;
  }

  private MouseListener createMouseListener(@NotNull ControlGroup group, @NotNull JLabel label, @NotNull JBColor backgroundColor) {
    return new MouseAdapter() {
      private final Timer myTimer = new Timer(MULTI_CLICK_INTERVAL, event -> timedOut());
      private MouseEvent myLastEvent;

      @Override
      public void mouseClicked(MouseEvent event) {
        switch (event.getClickCount()) {
          case 1:
            myLastEvent = event;
            myTimer.restart();
            break;
          case 2:
            myTimer.stop();
            mouseDoubleClickEvent(group, event);
            break;
        }
      }

      private void timedOut() {
        myTimer.stop();
        mouseClickEvent(group, myLastEvent);
      }

      @Override
      public void mouseEntered(@NotNull MouseEvent event) {
        setHoverAttributes(event, SMALL_LINK_FONT, LINK_COLOR, WHITE);
        label.setFont(SMALL_BOLD_FONT);
      }

      @Override
      public void mouseExited(@NotNull MouseEvent event) {
        setHoverAttributes(event, SMALL_FONT, BLACK, backgroundColor);
        label.setFont(SMALL_FONT);
      }

      private void mouseClickEvent(@NotNull ControlGroup group, @NotNull MouseEvent event) {
        switch (group) {
          case LAYOUT:
            startEditing(myEnumEditor, event);
            break;
          case PADDING:
          case LAYOUT_MARGIN:
          case CONSTRAINT_MARGIN:
            startEditing(myReferenceEditor, event);
            break;
          case GRAVITY:
          case LAYOUT_GRAVITY:
            startEditingGravity(event);
            break;
        }
      }

      private void mouseDoubleClickEvent(@NotNull ControlGroup group, @NotNull MouseEvent event) {
        switch (group) {
          case GRAVITY:
          case LAYOUT_GRAVITY:
            toggleGravityValue(event);
            break;
          case LAYOUT:
            startEditing(myEnumEditor, event);
            break;
          case PADDING:
          case LAYOUT_MARGIN:
          case CONSTRAINT_MARGIN:
            startEditing(myReferenceEditor, event);
            break;
        }
      }
    };
  }

  private NlEnumEditor.Listener createEnumListener() {
    return new NlEnumEditor.Listener() {
      @Override
      public void itemPicked(@NotNull NlEnumEditor source, @Nullable String value) {
        NlProperty property = source.getProperty();
        if (property != null) {
          property.setValue(value);
          closeEditor(source);
          refresh();
        }
      }

      @Override
      public void resourcePicked(@NotNull NlEnumEditor source, @NotNull String value) {
        itemPicked(source, value);
      }

      @Override
      public void resourcePickerCancelled(@NotNull NlEnumEditor source) {
      }
    };
  }

  private NlReferenceEditor.EditingListener createReferenceListener() {
    return new NlReferenceEditor.EditingListener() {
      @Override
      public void stopEditing(@NotNull NlReferenceEditor editor, @NotNull String value) {
        JLabel label = editor.getLabel();
        assert label != null;
        AttributeDependency dependency = (AttributeDependency)label.getClientProperty(ATTRIBUTE_DEPENDENCY_KEY);
        assert dependency != null;
        dependency.setValue(myProperties, value);
        closeEditor(editor);
      }

      @Override
      public void cancelEditing(@NotNull NlReferenceEditor editor) {
        closeEditor(editor);
      }
    };
  }

  private static AttributeDependency createLayoutWidthAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(title + " Width", ATTR_LAYOUT_WIDTH)
      .build();
  }

  private static AttributeDependency createLayoutHeightAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(title + " Height", ATTR_LAYOUT_HEIGHT)
      .build();
  }

  private static AttributeDependency createPaddingTopAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(TOP_TITLE_PREFIX + title, ATTR_PADDING_TOP)
      .addDependencies(ATTR_PADDING_TOP, ATTR_PADDING)
      .setAllSideAttribute(ATTR_PADDING)
      .addOtherSides(ATTR_PADDING_BOTTOM, ATTR_PADDING_START, ATTR_PADDING_END)
      .build();
  }

  private static AttributeDependency createPaddingBottomAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(BOTTOM_TITLE_PREFIX + title, ATTR_PADDING_BOTTOM)
      .addDependencies(ATTR_PADDING_BOTTOM, ATTR_PADDING)
      .setAllSideAttribute(ATTR_PADDING)
      .addOtherSides(ATTR_PADDING_TOP, ATTR_PADDING_START, ATTR_PADDING_END)
      .build();
  }

  private static AttributeDependency createPaddingStartAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(START_TITLE_PREFIX + title, ATTR_PADDING_START)
      .addDependencies(ATTR_PADDING_START, ATTR_PADDING, ATTR_PADDING_LEFT)
      .setAllSideAttribute(ATTR_PADDING)
      .addOtherSides(ATTR_PADDING_TOP, ATTR_PADDING_BOTTOM, ATTR_PADDING_END)
      .build();
  }

  private static AttributeDependency createPaddingEndAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(END_TITLE_PREFIX + title, ATTR_PADDING_END)
      .addDependencies(ATTR_PADDING_END, ATTR_PADDING, ATTR_PADDING_RIGHT)
      .setAllSideAttribute(ATTR_PADDING)
      .addOtherSides(ATTR_PADDING_TOP, ATTR_PADDING_BOTTOM, ATTR_PADDING_START)
      .build();
  }

  private static AttributeDependency createLayoutMarginTopAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(TOP_TITLE_PREFIX + title, ATTR_LAYOUT_MARGIN_TOP)
      .addDependencies(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_TOP)
      .setAllSideAttribute(ATTR_LAYOUT_MARGIN)
      .addOtherSides(ATTR_LAYOUT_MARGIN_BOTTOM, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END)
      .build();
  }

  private static AttributeDependency createLayoutMarginBottomAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(BOTTOM_TITLE_PREFIX + title, ATTR_LAYOUT_MARGIN_BOTTOM)
      .addDependencies(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_BOTTOM)
      .setAllSideAttribute(ATTR_LAYOUT_MARGIN)
      .addOtherSides(ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END)
      .build();
  }

  private static AttributeDependency createLayoutMarginStartAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(START_TITLE_PREFIX + title, ATTR_LAYOUT_MARGIN_START)
      .addDependencies(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_LEFT)
      .setAllSideAttribute(ATTR_LAYOUT_MARGIN)
      .addOtherSides(ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM, ATTR_LAYOUT_MARGIN_END)
      .build();
  }

  private static AttributeDependency createLayoutMarginEndAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(END_TITLE_PREFIX + title, ATTR_LAYOUT_MARGIN_END)
      .addDependencies(ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_RIGHT)
      .setAllSideAttribute(ATTR_LAYOUT_MARGIN)
      .addOtherSides(ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM, ATTR_LAYOUT_MARGIN_START)
      .build();
  }

  private static AttributeDependency createConstraintMarginTopAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(TOP_TITLE_PREFIX + title, ATTR_LAYOUT_TOP_MARGIN)
      .build();
  }

  private static AttributeDependency createConstraintMarginBottomAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(BOTTOM_TITLE_PREFIX + title, ATTR_LAYOUT_BOTTOM_MARGIN)
      .build();
  }

  private static AttributeDependency createConstraintMarginStartAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(LEFT_TITLE_PREFIX + title, ATTR_LAYOUT_LEFT_MARGIN)
      .build();
  }

  private static AttributeDependency createConstraintMarginEndAttributeDependency(@NotNull String title) {
    return AttributeDependency.Builder.newBuilder(RIGHT_TITLE_PREFIX + title, ATTR_LAYOUT_RIGHT_MARGIN)
      .build();
  }

  /**
   * The complexity of padding and margin attributes is isolated in this class.
   * {@link #myAttribute} is the actual attribute being handled.
   * The value is determined by checking {@link #myDependentAttributes} in order:
   * the first non null value is the value we want to show for this attribute.
   * If this value if set we must reset all dependent attributes. If the attribute
   * for all sides is reset {@link #myAllSideAttribute} then the {@link #myOtherSideAttributes}
   * should be set to the current value of the all side attribute.
   */
  private static class AttributeDependency {
    private final String myTitle;
    private final String myAttribute;
    private final List<String> myDependentAttributes;
    private final String myAllSideAttribute;
    private final List<String> myOtherSideAttributes;

    private AttributeDependency(@NotNull String title,
                                @NotNull String attribute,
                                @NotNull List<String> dependentAttributes,
                                @Nullable String allSideAttribute,
                                @NotNull List<String> otherSideAttributes) {
      myTitle = title;
      myAttribute = attribute;
      myDependentAttributes = dependentAttributes;
      myAllSideAttribute = allSideAttribute;
      myOtherSideAttributes = otherSideAttributes;
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    @NotNull
    public String getAttribute() {
      return myAttribute;
    }

    @NotNull
    public String getValue(@NotNull Map<String, NlProperty> properties) {
      for (String attribute : myDependentAttributes) {
        NlProperty property = properties.get(attribute);
        String value = property != null ? property.getResolvedValue() : null;
        if (!StringUtil.isEmpty(value)) {
          return value;
        }
      }
      if (myDependentAttributes.isEmpty()) {
        NlProperty property = properties.get(myAttribute);
        String value = property != null ? property.getResolvedValue() : null;
        if (!StringUtil.isEmpty(value)) {
          return value;
        }
      }
      return " ";
    }

    public void setValue(@NotNull Map<String, NlProperty> properties, @Nullable String newValue) {
      NlProperty property = properties.get(myAttribute);
      assert property != null;
      if (newValue != null) {
        for (String attribute : myDependentAttributes) {
          if (attribute.equals(myAttribute)) {
            continue;
          }
          NlProperty dependency = properties.get(attribute);
          assert dependency != null;
          String oldValue = dependency.getValue();
          if (oldValue != null) {
            if (attribute.equals(myAllSideAttribute)) {
              for (String other : myOtherSideAttributes) {
                NlProperty otherSide = properties.get(other);
                assert otherSide != null;
                otherSide.setValue(oldValue);
              }
            }
            dependency.setValue(null);
          }
        }
      }
      property.setValue(newValue);
    }

    private static class Builder {
      private String myTitle;
      private String myAttribute;
      private ImmutableList.Builder<String> myDependentAttributes;
      private String myAllSideAttribute;
      private ImmutableList.Builder<String> myOtherSideAttributes;

      @NotNull
      public static Builder newBuilder(@NotNull String title, @NotNull String attribute) {
        return new Builder(title, attribute);
      }

      private Builder(@NotNull String title, @NotNull String attribute) {
        myTitle = title;
        myAttribute = attribute;
        myDependentAttributes = ImmutableList.builder();
        myOtherSideAttributes = ImmutableList.builder();
      }

      @NotNull
      public Builder addDependencies(@NotNull String... dependencies) {
        myDependentAttributes.addAll(Arrays.asList(dependencies));
        return this;
      }

      @NotNull
      public Builder setAllSideAttribute(@NotNull String allSideAttribute) {
        myAllSideAttribute = allSideAttribute;
        return this;
      }

      @NotNull
      public Builder addOtherSides(@NotNull String... others) {
        myOtherSideAttributes.addAll(Arrays.asList(others));
        return this;
      }

      @NotNull
      public AttributeDependency build() {
        return new AttributeDependency(myTitle, myAttribute, myDependentAttributes.build(), myAllSideAttribute,
                                       myOtherSideAttributes.build());
      }
    }
  }

  /**
   * Declarative description of how a gravity attribute should be displayed and edited in the {@link NlLayoutEditor}.
   * Each gravity attribute is described with a {@link GravityDisplayTable} for each of the 4 cardinal
   * directions: north, east, south, and west.
   */
  private static class GravityDisplayTable {
    private final String myTitle;
    private final String myAttribute;
    private final String myValue;
    private final String myOppositeValue;
    private final String myCenterValue;
    private final String myOrthogonalCenterValue;
    private final Icon myDefaultIcon;
    private final Icon myGravityOnIcon;

    @NotNull
    public Icon getValue(@NotNull Map<String, NlProperty> properties) {
      return isGravityOn(getProperty(properties)) ? myGravityOnIcon : myDefaultIcon;
    }

    public void setValue(@NotNull Map<String, NlProperty> properties, boolean newValue) {
      NlFlagPropertyItem property = getProperty(properties);
      if (property == null) {
        return;
      }
      Set<String> itemsToRemove = new HashSet<>(4);
      Set<String> itemsToAdd = new HashSet<>(4);
      if (newValue) {
        if (property.isAnyItemSet(myOppositeValue, myCenterValue, GRAVITY_VALUE_CENTER)) {
          itemsToRemove.add(myOppositeValue);
          itemsToRemove.add(myValue);
          if (property.isAnyItemSet(myOrthogonalCenterValue, GRAVITY_VALUE_CENTER)) {
            itemsToAdd.add(GRAVITY_VALUE_CENTER);
            itemsToRemove.add(myOrthogonalCenterValue);
            itemsToRemove.add(myCenterValue);
          }
          else {
            itemsToAdd.add(myCenterValue);
          }
        }
        else {
          itemsToAdd.add(myValue);
        }
      }
      else {
        itemsToRemove.add(myValue);
        if (property.isItemSet(GRAVITY_VALUE_CENTER)) {
          itemsToRemove.add(GRAVITY_VALUE_CENTER);
          itemsToAdd.add(myOrthogonalCenterValue);
          itemsToAdd.add(myOppositeValue);
        }
        else if (property.isItemSet(myCenterValue)) {
          itemsToRemove.add(myCenterValue);
          itemsToAdd.add(myOppositeValue);
        }
      }
      property.updateItems(itemsToAdd, itemsToRemove);
    }

    public void toggleValue(@NotNull Map<String, NlProperty> properties) {
      setValue(properties, !isGravityOn(getProperty(properties)));
    }

    public boolean initEditor(@NotNull NlGravityEditor editor, @NotNull Map<String, NlProperty> properties) {
      NlFlagPropertyItem property = getProperty(properties);
      if (property == null) {
        return false;
      }
      editor.getComponent().putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, this);
      editor.setProperty(property);
      JLabel label = editor.getLabel();
      assert label != null;
      label.setText(myTitle);
      return true;
    }

    private GravityDisplayTable(@NotNull String title,
                                @NotNull String attribute,
                                @NotNull String value) {
      myTitle = title;
      myAttribute = attribute;
      myValue = value;
      switch (value) {
        case GRAVITY_VALUE_TOP:
          myOppositeValue = GRAVITY_VALUE_BOTTOM;
          myCenterValue = GRAVITY_VALUE_CENTER_VERTICAL;
          myOrthogonalCenterValue = GRAVITY_VALUE_CENTER_HORIZONTAL;
          myGravityOnIcon = ArrowUp;
          myDefaultIcon = UnSetUp;
          break;
        case GRAVITY_VALUE_BOTTOM:
          myOppositeValue = GRAVITY_VALUE_TOP;
          myCenterValue = GRAVITY_VALUE_CENTER_VERTICAL;
          myOrthogonalCenterValue = GRAVITY_VALUE_CENTER_HORIZONTAL;
          myGravityOnIcon = ArrowDown;
          myDefaultIcon = UnSetDown;
          break;
        case GRAVITY_VALUE_START:
          myOppositeValue = GRAVITY_VALUE_END;
          myCenterValue = GRAVITY_VALUE_CENTER_HORIZONTAL;
          myOrthogonalCenterValue = GRAVITY_VALUE_CENTER_VERTICAL;
          myGravityOnIcon = ArrowUp;
          myDefaultIcon = UnSetUp;
          break;
        case GRAVITY_VALUE_END:
          myOppositeValue = GRAVITY_VALUE_START;
          myCenterValue = GRAVITY_VALUE_CENTER_HORIZONTAL;
          myOrthogonalCenterValue = GRAVITY_VALUE_CENTER_VERTICAL;
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

    public CardinalDirectionControls(@NotNull ControlGroup group, @NotNull String title, @NotNull Color backgroundColor) {
      for (JLabel label : new JLabel[]{myNorth, mySouth, myEast, myWest}) {
        label.setFont(SMALL_FONT);
        label.setForeground(BLACK);
        label.setOpaque(true);
        label.setBackground(backgroundColor);
      }
      mySouth.setHorizontalAlignment(SwingConstants.CENTER);
      addEditSupport(group, title);
    }

    private void addEditSupport(@NotNull ControlGroup group, @NotNull String title) {
      switch (group) {
        case GRAVITY:
          myNorth.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_GRAVITY, GRAVITY_VALUE_TOP));
          mySouth.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_GRAVITY, GRAVITY_VALUE_BOTTOM));
          myWest.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_GRAVITY, GRAVITY_VALUE_START));
          myEast.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_GRAVITY, GRAVITY_VALUE_END));
          break;
        case LAYOUT_GRAVITY:
          myNorth.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_TOP));
          mySouth.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_BOTTOM));
          myWest.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_START));
          myEast.putClientProperty(GRAVITY_DISPLAY_TABLE_KEY, new GravityDisplayTable(title, ATTR_LAYOUT_GRAVITY, GRAVITY_VALUE_END));
          break;
        case PADDING:
          myNorth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createPaddingTopAttributeDependency(title));
          mySouth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createPaddingBottomAttributeDependency(title));
          myEast.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createPaddingEndAttributeDependency(title));
          myWest.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createPaddingStartAttributeDependency(title));
          break;
        case LAYOUT_MARGIN:
          myNorth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createLayoutMarginTopAttributeDependency(title));
          mySouth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createLayoutMarginBottomAttributeDependency(title));
          myEast.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createLayoutMarginEndAttributeDependency(title));
          myWest.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createLayoutMarginStartAttributeDependency(title));
          break;
        case CONSTRAINT_MARGIN:
          myNorth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createConstraintMarginTopAttributeDependency(title));
          mySouth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createConstraintMarginBottomAttributeDependency(title));
          myEast.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createConstraintMarginEndAttributeDependency(title));
          myWest.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createConstraintMarginStartAttributeDependency(title));
          break;
        case LAYOUT:
          mySouth.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createLayoutWidthAttributeDependency(title));
          myEast.putClientProperty(ATTRIBUTE_DEPENDENCY_KEY, createLayoutHeightAttributeDependency(title));
          myNorth.setText(" ");
          myWest.setText(" ");
          break;
        default:
          break;
      }
    }

    public void refresh(@NotNull Map<String, NlProperty> properties) {
      for (JLabel direction : new JLabel[]{myNorth, mySouth, myEast, myWest}) {
        AttributeDependency dependency = (AttributeDependency)direction.getClientProperty(ATTRIBUTE_DEPENDENCY_KEY);
        if (dependency != null) {
          direction.setText(dependency.getValue(properties));
        }
        GravityDisplayTable displayTable = (GravityDisplayTable)direction.getClientProperty(GRAVITY_DISPLAY_TABLE_KEY);
        if (displayTable != null) {
          direction.setIcon(displayTable.getValue(properties));
        }
      }
    }
  }
}
