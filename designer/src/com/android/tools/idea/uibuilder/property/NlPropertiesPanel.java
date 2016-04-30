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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableModel;
import com.android.util.PropertiesMap;
import com.google.common.collect.Table;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class NlPropertiesPanel extends JPanel implements ShowExpertProperties.Model {
  private static final String CARD_ADVANCED = "table";
  private static final String CARD_DEFAULT = "default";

  private final PTable myTable;
  private final PTableModel myModel;
  private final InspectorPanel myInspectorPanel;

  private JBLabel mySelectedComponentLabel;
  private JPanel myCardPanel;
  private NlComponent myComponent;
  private List<NlPropertyItem> myProperties;
  private boolean myShowAdvancedProperties;

  public NlPropertiesPanel() {
    super(new BorderLayout());
    setOpaque(true);
    setFocusable(true);
    setRequestFocusEnabled(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myModel = new PTableModel();

    myTable = new PTable(myModel);
    myTable.getEmptyText().setText("No selected component");
    myInspectorPanel = new InspectorPanel();

    myCardPanel = new JPanel(new JBCardLayout());

    JPanel headerPanel = createHeaderPanel();
    add(headerPanel, BorderLayout.NORTH);
    add(myCardPanel, BorderLayout.CENTER);

    myCardPanel.add(CARD_DEFAULT, ScrollPaneFactory.createScrollPane(myInspectorPanel,
                                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
    myCardPanel.add(CARD_ADVANCED, ScrollPaneFactory.createScrollPane(myTable));
  }

  @NotNull
  private JPanel createHeaderPanel() {
    JBPanel panel = new JBPanel(new BorderLayout());

    mySelectedComponentLabel = new JBLabel("");
    panel.add(mySelectedComponentLabel, BorderLayout.CENTER);

    ShowExpertProperties showExpertAction = new ShowExpertProperties(this);
    ActionButton showExpertButton = new ActionButton(showExpertAction, showExpertAction.getTemplatePresentation(), ActionPlaces.UNKNOWN,
                                                     ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
    panel.add(showExpertButton, BorderLayout.LINE_END);

    return panel;
  }

  public void setItems(@Nullable NlComponent component,
                       @NotNull Table<String, String, NlPropertyItem> properties,
                       @NotNull NlPropertiesManager propertiesManager) {
    String componentName = component == null ? "" : component.getTagName();
    myComponent = component;
    myProperties = extractPropertiesForTable(properties);
    mySelectedComponentLabel.setText(componentName);

    List<PTableItem> sortedProperties;
    if (component == null) {
      sortedProperties = Collections.emptyList();
    }
    else {
      List<PTableItem> groupedProperties = new NlPropertiesGrouper().group(myProperties, component);
      sortedProperties = new NlPropertiesSorter().sort(groupedProperties, component);
    }
    if (myTable.isEditing()) {
      myTable.removeEditor();
    }
    myModel.setItems(sortedProperties);

    updateDefaultProperties(propertiesManager);
    myInspectorPanel.setComponent(component, properties, propertiesManager);
  }

  @NotNull
  private static List<NlPropertyItem> extractPropertiesForTable(@NotNull Table<String, String, NlPropertyItem> properties) {
    Map<String, NlPropertyItem> androidProperties = properties.row(SdkConstants.ANDROID_URI);
    Map<String, NlPropertyItem> autoProperties = properties.row(SdkConstants.AUTO_URI);

    // Include all auto (app) properties and all android properties that are not also auto properties.
    // TODO: Include design properties here.
    List<NlPropertyItem> result = new ArrayList<>(properties.size());
    result.addAll(autoProperties.values());
    for (Map.Entry<String, NlPropertyItem> entry : androidProperties.entrySet()) {
      if (!autoProperties.containsKey(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  public void modelRendered(@NotNull NlPropertiesManager propertiesManager) {
    updateDefaultProperties(propertiesManager);
    myInspectorPanel.refresh();
  }

  private void updateDefaultProperties(@NotNull NlPropertiesManager propertiesManager) {
    if (myComponent == null || myProperties == null) {
      return;
    }
    PropertiesMap defaultValues = propertiesManager.getDefaultProperties(myComponent);
    if (defaultValues.isEmpty()) {
      return;
    }
    for (NlPropertyItem property : myProperties) {
      property.setDefaultValue(defaultValues.get(property.getName()));
    }
  }

  @Override
  public boolean isShowingExpertProperties() {
    return myShowAdvancedProperties;
  }

  @Override
  public void setShowExpertProperties(boolean en) {
    myShowAdvancedProperties = en;
    JBCardLayout cardLayout = (JBCardLayout)myCardPanel.getLayout();
    String name = en ? CARD_ADVANCED : CARD_DEFAULT;
    cardLayout.swipe(myCardPanel, name, JBCardLayout.SwipeDirection.AUTO);
  }
}
