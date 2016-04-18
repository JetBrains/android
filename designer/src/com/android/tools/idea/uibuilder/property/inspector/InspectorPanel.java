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

import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintInspectorProvider;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InspectorPanel extends JPanel {
  private static final boolean DEBUG_BOUNDS = false;

  private final Font myBoldLabelFont = UIUtil.getLabelFont().deriveFont(Font.BOLD);
  private final Icon myExpandedIcon;
  private final Icon myCollapsedIcon;
  private final JPanel myInspector;
  private List<InspectorComponent> myInspectors = Collections.emptyList();
  private List<Component> myGroup;

  public InspectorPanel() {
    super(new BorderLayout());
    myExpandedIcon = (Icon)UIManager.get("Tree.expandedIcon");
    myCollapsedIcon = (Icon)UIManager.get("Tree.collapsedIcon");
    myInspector = new JPanel();
    add(myInspector, BorderLayout.CENTER);
    myInspector.setLayout(createGridLayout());
  }

  private static LayoutManager createGridLayout() {
    // 2 column grid by default
    String layoutConstraints = "wrap 6, insets 2pt, novisualpadding, hidemode 3";
    if (DEBUG_BOUNDS) {
      layoutConstraints += ", debug";
    }
    // Dual configuration:
    // 1) [Single component] 1st column 30%, 2nd column 70%
    // 2) [Two components]   1st and 3rd column 15%, 2nd and 4th column 35%
    String columnConstraints = "[15%!][15%!][20%!][15%!][15%!][20%!]";

    return new MigLayout(layoutConstraints, columnConstraints);
  }

  public void setComponent(@Nullable NlComponent component,
                           @NotNull List<? extends NlProperty> properties,
                           @NotNull NlPropertiesManager propertiesManager) {
    myInspector.removeAll();

    Map<String, NlProperty> propertiesByName = Maps.newHashMapWithExpectedSize(properties.size());
    for (NlProperty property : properties) {
      propertiesByName.put(property.getName(), property);
    }

    InspectorProvider[] allProviders = new InspectorProvider[]{
      new ConstraintInspectorProvider(),
      new IdInspectorProvider(),
      new FontInspectorProvider(),
    };

    List<InspectorComponent> inspectors = createInspectorComponents(component, propertiesManager, propertiesByName, allProviders);

    for (InspectorComponent inspector : inspectors) {
      inspector.attachToInspector(this);
    }

    endGroup();
    myInspectors = inspectors;
  }

  public void refresh() {
    ApplicationManager.getApplication().invokeLater(() -> myInspectors.stream().forEach(InspectorComponent::refresh));
  }

  @NotNull
  private static List<InspectorComponent> createInspectorComponents(@Nullable NlComponent component,
                                                                    @NotNull NlPropertiesManager propertiesManager,
                                                                    @NotNull Map<String, NlProperty> properties,
                                                                    @NotNull InspectorProvider[] allProviders) {
    List<InspectorComponent> inspectors = Lists.newArrayListWithExpectedSize(allProviders.length);

    if (component == null) {
      // create just the id inspector, which we know can handle a null component
      // this is simply to avoid the screen flickering when switching components
      return ImmutableList.of(
        new IdInspectorProvider().createCustomInspector(null, properties, propertiesManager));
    }

    for (InspectorProvider provider : allProviders) {
      if (provider.isApplicable(component, properties)) {
        inspectors.add(provider.createCustomInspector(component, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  public void addExpandableTitle(@NotNull String title) {
    JLabel label = createLabel(title, null, null);
    label.setFont(myBoldLabelFont);
    addComponent(label, "span 6");

    startGroup(label);
  }

  public void addSeparator() {
    endGroup();
    addComponent(new JSeparator(), "span 6, grow");
  }

  public void addComponent(@NotNull String labelText,
                           @Nullable String tooltip,
                           @NotNull Component component) {
    addComponent(createLabel(labelText, tooltip, component), "span 2"); // 30%
    addComponent(component, "span 4"); // 70%
  }

  public void addSplitComponents(@Nullable String labelText1,
                                 @Nullable String tooltip1,
                                 @Nullable Component component1,
                                 @Nullable String labelText2,
                                 @Nullable String tooltip2,
                                 @Nullable Component component2) {
    assert (labelText1 != null || component1 != null) && (labelText2 != null || component2 != null);
    if (labelText1 != null) {
      int span = component1 != null ? 1 : 3;
      addComponent(createLabel(labelText1, tooltip1, component1), "span " + span);
    }
    if (component1 != null) {
      int span = labelText1 != null ? 2 : 3;
      addComponent(component1, "span " + span);
    }
    if (labelText2 != null) {
      int span = component2 != null ? 1 : 3;
      addComponent(createLabel(labelText2, tooltip2, component2), "span " + span);
    }
    if (component2 != null) {
      int span = labelText2 != null ? 2 : 3;
      addComponent(component2, "span " + span);
    }
  }

  /**
   * Adds a custom panel that spans the entire width, just set the preferred height on the panel
   */
  public void addPanel(@NotNull JPanel panel) {
    addComponent(panel, "span 5, grow");
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

  private void addComponent(@NotNull Component component, @NotNull String migConstraints) {
    myInspector.add(component, migConstraints);
    if (myGroup != null) {
      myGroup.add(component);
      component.setVisible(false);
    }
  }

  private void startGroup(@NotNull JLabel label) {
    assert myGroup == null;
    List<Component> group = new ArrayList<>();

    label.setIcon(myCollapsedIcon);
    label.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        boolean wasExpanded = label.getIcon() == myExpandedIcon;
        label.setIcon(wasExpanded ? myCollapsedIcon : myExpandedIcon);
        group.stream().forEach(component -> component.setVisible(!wasExpanded));
      }
    });

    myGroup = group;
  }

  private void endGroup() {
    myGroup = null;
  }
}
