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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class InspectorPanel extends JPanel {
  private static final boolean DEBUG_BOUNDS = false;
  private static final Color TITLE_COLOR = JBColor.BLUE;

  public InspectorPanel() {
    super(new BorderLayout());
  }

  private static LayoutManager createGridLayout() {
    // 2 column grid by default
    String layoutConstraints = "wrap 6, insets 1";
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
    removeAll();

    JPanel panel = new JPanel();
    add(panel, BorderLayout.CENTER);

    panel.setLayout(createGridLayout());

    Map<String, NlProperty> propertiesByName = Maps.newHashMapWithExpectedSize(properties.size());
    for (NlProperty property : properties) {
      propertiesByName.put(property.getName(), property);
    }

    InspectorProvider[] allProviders = new InspectorProvider[]{
      new IdInspectorProvider(),
      new FontInspectorProvider(),
    };

    List<InspectorComponent> inspectors = createInspectorComponents(component, propertiesManager, propertiesByName, allProviders);

    for (InspectorComponent inspector : inspectors) {
      inspector.attachToInspector(panel);
    }
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

  public static void addTitle(@NotNull JPanel inspector, @NotNull String title) {
    JBLabel label = new JBLabel(title);
    label.setForeground(TITLE_COLOR);
    inspector.add(label, "gapbottom 1, span, split 2, aligny center");
    inspector.add(new JSeparator(), "gapleft rel, growx");
  }

  public static void addSeparator(@NotNull JPanel inspector) {
    inspector.add(new JSeparator(), "span 6, grow");
  }

  public static void addComponent(@NotNull JPanel inspector,
                                  @NotNull String labelText,
                                  @Nullable String tooltip,
                                  @NotNull Component component) {
    JBLabel l = new JBLabel(labelText);
    l.setLabelFor(component);
    l.setToolTipText(tooltip);

    inspector.add(l, "span 2"); // 30%
    inspector.add(component, "span 4"); // 70%
  }

  public static void addSplitComponents(@NotNull JPanel inspector,
                                        @Nullable String labelText1,
                                        @Nullable String tooltip1,
                                        @Nullable Component component1,
                                        @Nullable String labelText2,
                                        @Nullable String tooltip2,
                                        @Nullable Component component2) {
    assert (labelText1 != null || component1 != null) && (labelText2 != null || component2 != null);
    if (labelText1 != null) {
      JBLabel label1 = new JBLabel(labelText1);
      label1.setLabelFor(component1);
      label1.setToolTipText(tooltip1);
      int span = component1 != null ? 1 : 3;
      inspector.add(label1, "span " + span);
    }
    if (component1 != null) {
      int span = labelText1 != null ? 2 : 3;
      inspector.add(component1, "span " + span);
    }
    if (labelText2 != null) {
      JBLabel label2 = new JBLabel(labelText2);
      label2.setLabelFor(component2);
      label2.setToolTipText(tooltip2);
      int span = component2 != null ? 1 : 3;
      inspector.add(label2, "span " + span);
    }
    if (component2 != null) {
      int span = labelText2 != null ? 2 : 3;
      inspector.add(component2, "span " + span);
    }
  }

  /**
   * Adds a custom panel that spans the entire width, just set the preferred height on the panel
   */
  public static void addPanel(@NotNull JPanel inspector, @NotNull JPanel panel) {
    inspector.add(panel, "span 2, grow");
  }
}
