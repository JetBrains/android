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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintInspectorProvider;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlDesignProperties;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.uiDesigner.core.GridConstraints.*;

public class InspectorPanel extends JPanel {
  public enum SplitLayout {SINGLE_ROW, STACKED, SEPARATE}

  private final List<InspectorProvider> myProviders;
  private final NlDesignProperties myDesignProperties;
  private final Font myBoldLabelFont = UIUtil.getLabelFont().deriveFont(Font.BOLD);
  private final Icon myExpandedIcon;
  private final Icon myCollapsedIcon;
  private final JPanel myInspector;
  private List<InspectorComponent> myInspectors = Collections.emptyList();
  private List<Component> myGroup;
  private boolean myGroupInitiallyOpen;
  private GridConstraints myConstraints = new GridConstraints();
  private int myRow;

  public InspectorPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProviders = createProviders(project);
    myDesignProperties = new NlDesignProperties();
    myExpandedIcon = (Icon)UIManager.get("Tree.expandedIcon");
    myCollapsedIcon = (Icon)UIManager.get("Tree.collapsedIcon");
    myInspector = new JPanel();
    myInspector.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
    add(myInspector, BorderLayout.CENTER);
  }

  private static List<InspectorProvider> createProviders(@NotNull Project project) {
    return ImmutableList.of(new ConstraintInspectorProvider(),
                            new IdInspectorProvider(),
                            new ViewInspectorProvider(project),
                            new TextInspectorProvider(),
                            new FontInspectorProvider()
    );
  }

  private static GridLayoutManager createLayoutManager(int rows, int columns) {
    Insets margin = new Insets(0, 0, 0, 0);
    // Hack: Use this constructor to get myMinCellSize = 0 which is not possible in the recommended constructor.
    return new GridLayoutManager(rows, columns, margin, 0, 0);
  }

  public void setComponent(@NotNull List<NlComponent> components,
                           @NotNull Table<String, String, ? extends NlProperty> properties,
                           @NotNull NlPropertiesManager propertiesManager) {
    myInspector.removeAll();
    myInspector.repaint();
    myRow = 0;

    Map<String, NlProperty> propertiesByName = Maps.newHashMapWithExpectedSize(properties.size());
    for (NlProperty property : properties.row(SdkConstants.ANDROID_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row(SdkConstants.AUTO_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row("").values()) {
      propertiesByName.put(property.getName(), property);
    }
    // Add access to known design properties
    for (NlProperty property : myDesignProperties.getKnownProperties(components)) {
      propertiesByName.put(property.getName(), property);
    }

    List<InspectorComponent> inspectors = createInspectorComponents(components, propertiesManager, propertiesByName, myProviders);

    int rows = 1;  // 1 for the spacer below
    for (InspectorComponent inspector : inspectors) {
      rows += inspector.getMaxNumberOfRows();
    }
    myInspector.setLayout(createLayoutManager(rows, 2));
    for (InspectorComponent inspector : inspectors) {
      inspector.attachToInspector(this);
    }

    endGroup();

    // Add a vertical spacer
    myInspector.add(new Spacer(), new GridConstraints(myRow++, 0, 1, 2, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_CAN_GROW, SIZEPOLICY_CAN_GROW | SIZEPOLICY_WANT_GROW, null, null, null, 0, false));

    myInspectors = inspectors;
  }

  public void refresh() {
    ApplicationManager.getApplication().invokeLater(() -> myInspectors.stream().forEach(InspectorComponent::refresh));
  }

  @NotNull
  private static List<InspectorComponent> createInspectorComponents(@NotNull List<NlComponent> components,
                                                                    @NotNull NlPropertiesManager propertiesManager,
                                                                    @NotNull Map<String, NlProperty> properties,
                                                                    @NotNull List<InspectorProvider> allProviders) {
    List<InspectorComponent> inspectors = Lists.newArrayListWithExpectedSize(allProviders.size());

    if (components.isEmpty()) {
      // create just the id inspector, which we know can handle a null component
      // this is simply to avoid the screen flickering when switching components
      return ImmutableList.of(new IdInspectorProvider().createCustomInspector(components, properties, propertiesManager));
    }

    for (InspectorProvider provider : allProviders) {
      if (provider.isApplicable(components, properties)) {
        inspectors.add(provider.createCustomInspector(components, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  public JLabel addTitle(@NotNull String title) {
    JLabel label = createLabel(title, null, null);
    label.setFont(myBoldLabelFont);
    addLineComponent(label, myRow++);
    return label;
  }

  public JLabel addExpandableTitle(@NotNull String title, @NotNull NlProperty groupStartProperty) {
    JLabel label = addTitle(title);
    startGroup(label, groupStartProperty);
    return label;
  }

  public void addSeparator() {
    endGroup();
    addLineComponent(new JSeparator(), myRow++);
  }

  public JLabel addLabel(@NotNull String title) {
    JLabel label = createLabel(title, null, null);
    addLineComponent(label, myRow++);
    return label;
  }

  public JLabel addComponent(@NotNull String labelText,
                             @Nullable String tooltip,
                             @NotNull Component component) {
    JLabel label = createLabel(labelText, tooltip, component);
    addLabelComponent(label, myRow);
    addValueComponent(component, myRow++);
    return label;
  }

  public void addSplitComponents(@NotNull SplitLayout layout,
                                 @NotNull String labelText1,
                                 @Nullable String tooltip1,
                                 @NotNull Component component1,
                                 @NotNull String labelText2,
                                 @Nullable String tooltip2,
                                 @NotNull Component component2) {
    JLabel label1 = createLabel(labelText1, tooltip1, component1);
    JLabel label2 = createLabel(labelText2, tooltip2, component2);
    JPanel panel;

    switch (layout) {
      case SEPARATE:
        addLabelComponent(label1, myRow);
        addValueComponent(component1, myRow++);
        addLabelComponent(label2, myRow);
        addValueComponent(component2, myRow++);
        break;

      case SINGLE_ROW:
        panel = new JPanel(createLayoutManager(1, 4));
        addToGridPanel(panel, label1, 0, 0, 1, ANCHOR_WEST, FILL_NONE);
        addToGridPanel(panel, component1, 0, 1, 1, ANCHOR_EAST, FILL_HORIZONTAL);
        addToGridPanel(panel, label2, 0, 2, 1, ANCHOR_WEST, FILL_NONE);
        addToGridPanel(panel, component2, 0, 3, 1, ANCHOR_EAST, FILL_HORIZONTAL);
        addLineComponent(panel, myRow++);
        break;

      case STACKED:
        panel = new JPanel(createLayoutManager(2, 2));
        addToGridPanel(panel, label1, 0, 0, 1, ANCHOR_WEST, FILL_NONE);
        addToGridPanel(panel, label2, 0, 1, 1, ANCHOR_WEST, FILL_NONE);
        addToGridPanel(panel, component1, 1, 0, 1, ANCHOR_EAST, FILL_HORIZONTAL);
        addToGridPanel(panel, component2, 1, 1, 1, ANCHOR_EAST, FILL_HORIZONTAL);
        addLineComponent(panel, myRow++);
        break;
    }
  }

  /**
   * Adds a custom panel that spans the entire width, just set the preferred height on the panel
   */
  public void addPanel(@NotNull JComponent panel) {
    addLineComponent(panel, myRow++);
  }

  public void restartExpansionGroup() {
    assert myGroup != null;
    myGroup.stream().forEach(component -> component.setVisible(true));
    myGroup.clear();
  }

  private static JLabel createLabel(@NotNull String labelText, @Nullable String tooltip, @Nullable Component component) {
    JBLabel label = new JBLabel(labelText);
    label.setLabelFor(component);
    label.setToolTipText(tooltip);
    return label;
  }

  private void addLineComponent(@NotNull Component component, int row) {
    addComponent(component, row, 0, 2, ANCHOR_WEST, FILL_HORIZONTAL);
  }

  private void addLabelComponent(@NotNull Component component, int row) {
    addComponent(component, row, 0, 1, ANCHOR_WEST, FILL_HORIZONTAL);
  }

  private void addValueComponent(@NotNull Component component, int row) {
    addComponent(component, row, 1, 1, ANCHOR_EAST, FILL_HORIZONTAL);
  }

  private void addComponent(@NotNull Component component, int row, int column, int columnSpan, int anchor, int fill) {
    addToGridPanel(myInspector, component, row, column, columnSpan, anchor, fill);
    if (myGroup != null) {
      myGroup.add(component);
      component.setVisible(myGroupInitiallyOpen);
    }
  }

  private void addToGridPanel(@NotNull JPanel panel, @NotNull Component component,
                              int row, int column, int columnSpan, int anchor, int fill) {
    myConstraints.setRow(row);
    myConstraints.setColumn(column);
    myConstraints.setColSpan(columnSpan);
    myConstraints.setAnchor(anchor);
    myConstraints.setFill(fill);

    panel.add(component, myConstraints);
  }

  private void startGroup(@NotNull JLabel label, @NotNull NlProperty groupStartProperty) {
    assert myGroup == null;
    List<Component> group = new ArrayList<>();
    String savedKey = "inspector.open." + groupStartProperty.getName();
    myGroupInitiallyOpen = PropertiesComponent.getInstance().getBoolean(savedKey);

    label.setIcon(myGroupInitiallyOpen ? myExpandedIcon : myCollapsedIcon);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        boolean wasExpanded = label.getIcon() == myExpandedIcon;
        label.setIcon(wasExpanded ? myCollapsedIcon : myExpandedIcon);
        group.stream().forEach(component -> component.setVisible(!wasExpanded));
        PropertiesComponent.getInstance().setValue(savedKey, !wasExpanded);
      }
    });

    myGroup = group;
  }

  private void endGroup() {
    myGroup = null;
  }
}
