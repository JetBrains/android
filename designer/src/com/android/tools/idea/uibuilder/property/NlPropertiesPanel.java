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
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.tools.idea.uibuilder.property.ptable.PTable;
import com.android.tools.idea.uibuilder.property.ptable.PTableGroupItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.ptable.PTableModel;
import com.android.util.PropertiesMap;
import com.google.common.collect.Table;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.TOOLS_URI;

public class NlPropertiesPanel extends JPanel implements ViewAllPropertiesAction.Model,
                                                         DataProvider, DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  private static final String CARD_ADVANCED = "table";
  private static final String CARD_DEFAULT = "default";
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 50;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 25;

  private final TableRowSorter<PTableModel> myRowSorter;
  private final MyFilter myFilter;
  private final PTable myTable;
  private final JPanel myTablePanel;
  private final PTableModel myModel;
  private final InspectorPanel myInspectorPanel;
  private final MyFocusTraversalPolicy myFocusTraversalPolicy;

  private final JPanel myCardPanel;

  private List<NlComponent> myComponents;
  private List<NlPropertyItem> myProperties;
  private boolean myAllPropertiesPanelVisible;

  public NlPropertiesPanel(@NotNull NlPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    super(new BorderLayout());
    setOpaque(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myRowSorter = new TableRowSorter<>();
    myFilter = new MyFilter();
    myModel = new PTableModel();
    myTable = new PTable(myModel);
    myTable.setEditorProvider(NlPropertyEditors.getInstance(propertiesManager.getProject()));
    myTable.getEmptyText().setText("No selected component");
    JComponent fewerPropertiesLink = createViewAllPropertiesLinkPanel(false);
    fewerPropertiesLink.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
    myTablePanel = new JPanel(new BorderLayout());
    myTablePanel.setVisible(false);
    myTablePanel.setBackground(myTable.getBackground());
    myTablePanel.add(myTable, BorderLayout.NORTH);
    myTablePanel.add(fewerPropertiesLink, BorderLayout.SOUTH);

    myInspectorPanel = new InspectorPanel(propertiesManager, parentDisposable, createViewAllPropertiesLinkPanel(true));

    myCardPanel = new JPanel(new JBCardLayout());

    add(myCardPanel, BorderLayout.CENTER);

    myCardPanel.add(CARD_DEFAULT, ScrollPaneFactory.createScrollPane(myInspectorPanel,
                                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));
    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTablePanel);
    tableScrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    tableScrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);
    tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myFocusTraversalPolicy = new MyFocusTraversalPolicy();
    myCardPanel.add(CARD_ADVANCED, tableScrollPane);
    myCardPanel.setFocusCycleRoot(true);
    myCardPanel.setFocusTraversalPolicy(myFocusTraversalPolicy);
    myComponents = Collections.emptyList();
    myProperties = Collections.emptyList();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(this::scrollIntoView);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(this::scrollIntoView);
  }

  public void setFilter(@NotNull String filter) {
    int selectedRow = myTable.getSelectedRow();
    PTableItem selectedItem = myTable.getSelectedItem();
    if (filter.isEmpty()) {
      myTable.setRowSorter(null);
    }
    else {
      myFilter.setPattern(filter);
      myRowSorter.setModel(myModel);
      myRowSorter.setRowFilter(myFilter);
      myRowSorter.setSortKeys(null);
      myTable.setRowSorter(myRowSorter);
    }
    myTable.restoreSelection(selectedRow, selectedItem);

    myInspectorPanel.setFilter(filter);
  }

  public void activatePropertySheet() {
    setAllPropertiesPanelVisible(true);
  }

  public void activateInspector() {
    setAllPropertiesPanelVisible(false);
  }

  public void setItems(@NotNull List<NlComponent> components,
                       @NotNull Table<String, String, NlPropertyItem> properties,
                       @NotNull NlPropertiesManager propertiesManager) {
    myComponents = components;
    myProperties = extractPropertiesForTable(properties);

    List<PTableItem> groupedProperties;
    if (components.isEmpty()) {
      groupedProperties = Collections.emptyList();
    }
    else {
      List<NlPropertyItem> sortedProperties = new NlPropertiesSorter().sort(myProperties, components);
      groupedProperties = new NlPropertiesGrouper().group(sortedProperties, components);
    }
    if (myTable.isEditing()) {
      myTable.removeEditor();
    }

    int selectedRow = myTable.getSelectedRow();
    PTableItem selectedItem = myTable.getSelectedItem();

    myModel.setItems(groupedProperties);
    if (myTable.getRowCount() > 0) {
      myTable.restoreSelection(selectedRow, selectedItem);
    }

    updateDefaultProperties(propertiesManager);
    myInspectorPanel.setComponent(components, properties, propertiesManager);
    myTablePanel.setVisible(!groupedProperties.isEmpty());
  }

  @NotNull
  private static List<NlPropertyItem> extractPropertiesForTable(@NotNull Table<String, String, NlPropertyItem> properties) {
    Map<String, NlPropertyItem> androidProperties = properties.row(SdkConstants.ANDROID_URI);
    Map<String, NlPropertyItem> autoProperties = properties.row(SdkConstants.AUTO_URI);
    Map<String, NlPropertyItem> designProperties = properties.row(TOOLS_URI);
    Map<String, NlPropertyItem> bareProperties = properties.row("");

    // Include all auto (app) properties and all android properties that are not also auto properties.
    List<NlPropertyItem> result = new ArrayList<>(properties.size());
    result.addAll(autoProperties.values());
    for (Map.Entry<String, NlPropertyItem> entry : androidProperties.entrySet()) {
      if (!autoProperties.containsKey(entry.getKey())) {
        result.add(entry.getValue());
      }
    }
    result.addAll(designProperties.values());
    result.addAll(bareProperties.values());
    return result;
  }

  public void modelRendered(@NotNull NlPropertiesManager propertiesManager) {
    UIUtil.invokeLaterIfNeeded(() -> {
      // Bug:219552 : Make sure updateDefaultProperties is always called from the same thread (the UI thread)
      updateDefaultProperties(propertiesManager);
      myInspectorPanel.refresh();
    });
  }

  private void updateDefaultProperties(@NotNull NlPropertiesManager propertiesManager) {
    if (myComponents.isEmpty() || myProperties.isEmpty()) {
      return;
    }
    PropertiesMap defaultValues = propertiesManager.getDefaultProperties(myComponents);
    if (defaultValues.isEmpty()) {
      return;
    }
    for (NlPropertyItem property : myProperties) {
      property.setDefaultValue(getDefaultProperty(defaultValues, property));
    }
  }

  @Nullable
  private static PropertiesMap.Property getDefaultProperty(@NotNull PropertiesMap defaultValues, @NotNull NlProperty property) {
    if (SdkConstants.ANDROID_URI.equals(property.getNamespace())) {
      PropertiesMap.Property defaultValue = defaultValues.get(SdkConstants.PREFIX_ANDROID + property.getName());
      if (defaultValue != null) {
        return defaultValue;
      }
      return defaultValues.get(SdkConstants.ANDROID_PREFIX + property.getName());
    }
    return defaultValues.get(property.getName());
  }

  @NotNull
  private JComponent createViewAllPropertiesLinkPanel(boolean viewAllProperties) {
    HyperlinkLabel textLink = new HyperlinkLabel();
    textLink.setHyperlinkText(viewAllProperties ? ViewAllPropertiesAction.VIEW_ALL_PROPERTIES : ViewAllPropertiesAction.VIEW_FEWER_PROPERTIES);
    textLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    HyperlinkLabel iconLink = new HyperlinkLabel();
    iconLink.setIcon(AndroidIcons.NeleIcons.ToggleProperties);
    iconLink.setFocusable(false);
    iconLink.setUseIconAsLink(true);
    iconLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    JPanel linkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    linkPanel.setOpaque(false);
    linkPanel.add(textLink);
    linkPanel.add(iconLink);
    return linkPanel;
  }

  private void setAllPropertiesPanelVisible(@NotNull HyperlinkEvent event, boolean viewAllProperties) {
    if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      setAllPropertiesPanelVisible(viewAllProperties);
    }
  }

  @Override
  public boolean isAllPropertiesPanelVisible() {
    return myAllPropertiesPanelVisible;
  }

  @Override
  public void setAllPropertiesPanelVisible(boolean viewAllProperties) {
    myAllPropertiesPanelVisible = viewAllProperties;
    JBCardLayout cardLayout = (JBCardLayout)myCardPanel.getLayout();
    String name = viewAllProperties ? CARD_ADVANCED : CARD_DEFAULT;
    Component next = viewAllProperties ? myTable : myInspectorPanel;
    cardLayout.swipe(myCardPanel, name, JBCardLayout.SwipeDirection.AUTO, next::requestFocus);
  }

  public boolean activatePreferredEditor(@NotNull String propertyName, boolean afterload) {
    if (isAllPropertiesPanelVisible()) {
      setAllPropertiesPanelVisible(false);
    }
    return myInspectorPanel.activatePreferredEditor(propertyName, afterload);
  }

  private void scrollIntoView(@NotNull PropertyChangeEvent event) {
    if (event.getNewValue() instanceof Component && "focusOwner".equals(event.getPropertyName())) {
      Component newFocusedComponent = (Component)event.getNewValue();
      if (isAncestorOf(newFocusedComponent) &&
          newFocusedComponent.getParent() instanceof JComponent &&
          myFocusTraversalPolicy.isLastFocusRecipient(newFocusedComponent)) {
        JComponent parent1 = (JComponent)newFocusedComponent.getParent();
        Rectangle bounds = newFocusedComponent.getBounds();
        if (newFocusedComponent == myTable) {
          bounds = myTable.getCellRect(myTable.getSelectedRow(), 1, true);
          bounds.x = 0;
        }
        parent1.scrollRectToVisible(bounds);
      }
    }
  }

  // ---- Implements DataProvider ----

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  // ---- Implements CopyProvider ----
  // Avoid the copying of components while editing the properties.

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
  }

  // ---- Implements CutProvider ----
  // Avoid the deletion of components while editing the properties.

  @Override
  public boolean isCutEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isCutVisible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performCut(@NotNull DataContext dataContext) {
  }

  // ---- Implements DeleteProvider ----
  // Avoid the deletion of components while editing the properties.

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
  }

  // ---- Implements PasteProvider ----
  // Avoid the paste of components while editing the properties.

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return false;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
  }

  @TestOnly
  public PTable getTable() {
    return myTable;
  }

  @VisibleForTesting
  static class MyFilter extends RowFilter<PTableModel, Integer> {
    private final SpeedSearchComparator myComparator = new SpeedSearchComparator(false);
    private String myPattern;

    @VisibleForTesting
    void setPattern(@NotNull String pattern) {
      myPattern = pattern;
    }

    @Override
    public boolean include(Entry<? extends PTableModel, ? extends Integer> entry) {
      PTableItem item = (PTableItem)entry.getValue(0);
      if (isMatch(item.getName())) {
        return true;
      }
      if (item.getParent() != null && isMatch(item.getParent().getName())) {
        return true;
      }
      if (!(item instanceof PTableGroupItem)) {
        return false;
      }
      PTableGroupItem group = (PTableGroupItem)item;
      for (PTableItem child : group.getChildren()) {
        if (isMatch(child.getName())) {
          return true;
        }
      }
      return false;
    }

    private boolean isMatch(@NotNull String text) {
      return myComparator.matchingFragments(myPattern, text) != null;
    }
  }

  private static class MyFocusTraversalPolicy extends LayoutFocusTraversalPolicy {

    private Component myLastFocusRecipient;

    private boolean isLastFocusRecipient(@NotNull Component component) {
      boolean isLastRecipient = component == myLastFocusRecipient;
      myLastFocusRecipient = null;
      return isLastRecipient;
    }

    @Override
    protected boolean accept(@NotNull Component component) {
      if (!super.accept(component)) {
        return false;
      }
      myLastFocusRecipient = component;
      return true;
    }
  }
}
