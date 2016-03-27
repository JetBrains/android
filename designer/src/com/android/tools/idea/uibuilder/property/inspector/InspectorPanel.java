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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
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
    String layoutConstraints = "wrap 2, insets 2";
    if (DEBUG_BOUNDS) {
      layoutConstraints += ", debug";
    }

    // first column should take up 30% of the overall space, and the labels should be aligned right
    // the second column should grow and fill to take available space
    String columnConstraints = "[30%!,align right][grow,fill]";
    return new MigLayout(layoutConstraints, columnConstraints);
  }

  public void setComponent(@Nullable NlComponent component,
                           @NonNull List<NlProperty> properties,
                           @NonNull NlPropertiesManager propertiesManager) {
    removeAll();

    JPanel panel = new JPanel();
    add(panel, BorderLayout.CENTER);

    panel.setLayout(createGridLayout());

    Map<String, NlProperty> propertiesByName = Maps.newHashMapWithExpectedSize(properties.size());
    for (NlProperty property : properties) {
      propertiesByName.put(property.getName(), property);
    }

    InspectorProvider[] allProviders = new InspectorProvider[]{
      new IdInspectorProvider()
    };

    List<InspectorComponent> inspectors = createInspectorComponents(component, propertiesManager, propertiesByName, allProviders);

    for (InspectorComponent inspector : inspectors) {
      inspector.attachToInspector(panel);
    }
  }

  @NotNull
  private static List<InspectorComponent> createInspectorComponents(@Nullable NlComponent component,
                                                                    @NonNull NlPropertiesManager propertiesManager,
                                                                    @NonNull Map<String, NlProperty> properties,
                                                                    @NonNull InspectorProvider[] allProviders) {
    List<InspectorComponent> inspectors = Lists.newArrayListWithExpectedSize(allProviders.length);

    if (component == null) {
      // create just the id inspector, which we know can handle a null component
      // this is simply to avoid the screen flickering when switching components
      return Collections.singletonList(new IdInspectorProvider().createCustomInspector(null, properties, propertiesManager));
    }

    for (InspectorProvider provider : allProviders) {
      if (provider.isApplicable(component, properties)) {
        inspectors.add(provider.createCustomInspector(component, properties, propertiesManager));
      }
    }

    return inspectors;
  }

  public static void addTitle(@NonNull JPanel inspector, @NonNull String title) {
    JBLabel label = new JBLabel(title);
    label.setForeground(TITLE_COLOR);
    inspector.add(label, "gapbottom 1, span, split 2, aligny center");
    inspector.add(new JSeparator(), "gapleft rel, growx");
  }

  public static void addSeparator(@NonNull JPanel inspector) {
    inspector.add(new JSeparator(), "span 2, grow");
  }

  public static void addComponent(@NonNull JPanel inspector, @NonNull String labelText, @NonNull JComponent component) {
    JBLabel l = new JBLabel(labelText);
    l.setLabelFor(component);

    inspector.add(l);
    inspector.add(component, "width null:null:70%"); // max 70% of container
  }

  /**
   * Adds a custom panel that spans the entire width, just set the preferred height on the panel
   */
  public static void addPanel(@NonNull JPanel inspector, @NonNull JPanel panel) {
    inspector.add(panel, "span 2, grow");
  }
}
