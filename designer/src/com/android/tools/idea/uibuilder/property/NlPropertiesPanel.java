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
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.inspector.InspectorPanel;
import com.android.util.PropertiesMap;
import com.google.common.collect.Table;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import sun.awt.CausedFocusEvent;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.uibuilder.property.ToggleXmlPropertyEditor.NL_XML_PROPERTY_EDITOR;

public class NlPropertiesPanel extends JPanel implements ViewAllPropertiesAction.Model, Disposable,
                                                         DataProvider, DeleteProvider, CutProvider, CopyProvider, PasteProvider {
  static final String PROPERTY_MODE = "properties.mode";
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 50;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 25;

  private final TableRowSorter<PTableModel> myRowSorter;
  private final MyFilter myFilter;
  private final MyFilterKeyListener myFilterKeyListener;
  private final PTable myTable;
  private final JPanel myTablePanel;
  private final PTableModel myModel;
  private final InspectorPanel myInspectorPanel;
  private final JBCardLayout myCardLayout;
  private final JPanel myCardPanel;
  private final PropertyChangeListener myPropertyChangeListener = this::scrollIntoView;

  private List<NlComponent> myComponents;
  private List<NlPropertyItem> myProperties;
  @NotNull
  private PropertiesViewMode myPropertiesViewMode;
  private Runnable myRestoreToolWindowCallback;

  public NlPropertiesPanel(@NotNull NlPropertiesManager propertiesManager, @NotNull Disposable parentDisposable) {
    this(propertiesManager, parentDisposable, new NlPTable(new PTableModel()), null);
  }

  @VisibleForTesting
  NlPropertiesPanel(@NotNull NlPropertiesManager propertiesManager,
                    @NotNull Disposable parentDisposable,
                    @NotNull PTable table,
                    @Nullable InspectorPanel inspectorPanel) {
    super(new BorderLayout());
    setOpaque(true);
    setBackground(UIUtil.TRANSPARENT_COLOR);

    myRowSorter = new TableRowSorter<>();
    myFilter = new MyFilter();
    myFilterKeyListener = new MyFilterKeyListener();
    myModel = table.getModel();
    myTable = table;
    myTable.getEmptyText().setText("No selected component");
    JComponent fewerPropertiesLink = createViewAllPropertiesLinkPanel(false);
    fewerPropertiesLink.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 0));
    myTablePanel = new JPanel(new BorderLayout());
    myTablePanel.setVisible(false);
    myTablePanel.setBackground(myTable.getBackground());
    myTablePanel.add(myTable, BorderLayout.NORTH);
    myTablePanel.add(fewerPropertiesLink, BorderLayout.SOUTH);
    myTablePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        myTable.editingStopped(new ChangeEvent(myTablePanel));
      }
    });

    myInspectorPanel = inspectorPanel != null
                       ? inspectorPanel
                       : new InspectorPanel(propertiesManager, parentDisposable, createViewAllPropertiesLinkPanel(true));

    Disposer.register(parentDisposable, this);

    myCardLayout = new JBCardLayout();
    myCardPanel = new JPanel(myCardLayout);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myInspectorPanel,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    myCardPanel.add(PropertiesViewMode.INSPECTOR.name(), scrollPane);
    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTablePanel);
    tableScrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    tableScrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);
    tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
    myCardPanel.add(PropertiesViewMode.TABLE.name(), tableScrollPane);
    myPropertiesViewMode = getPropertiesViewModeInitially();
    myCardLayout.show(myCardPanel, myPropertiesViewMode.name());
    myComponents = Collections.emptyList();
    myProperties = Collections.emptyList();
    add(myCardPanel, BorderLayout.CENTER);
  }

  @Override
  public void dispose() {
    JBCardLayout layout = (JBCardLayout)myCardPanel.getLayout();
    // This will stop the timer started in JBCardLayout:
    layout.first(myCardPanel);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(myPropertyChangeListener);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(myPropertyChangeListener);
  }

  public void setRestoreToolWindow(@NotNull Runnable restoreToolWindowCallback) {
    myRestoreToolWindowCallback = restoreToolWindowCallback;
  }

  public int getFilterMatchCount() {
    if (myTable.getRowSorter() == null) {
      return -1;
    }
    return myTable.getRowCount();
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

  @NotNull
  public KeyListener getFilterKeyListener() {
    return myFilterKeyListener;
  }

  private void enterInFilter(@NotNull KeyEvent event) {
    if (myTable.getRowCount() != 1) {
      PTableItem item = (PTableItem)myTable.getValueAt(0, 1);
      if (!(item.isExpanded() && myTable.getRowCount() == item.getChildren().size() + 1)) {
        return;
      }
    }
    if (myTable.isCellEditable(0, 1)) {
      myTable.editCellAt(0, 1);
      myTable.transferFocus();
      event.consume();
    }
    else {
      myModel.expand(myTable.convertRowIndexToModel(0));
      myTable.requestFocus();
      myTable.setRowSelectionInterval(0, 0);
      event.consume();
    }
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
    Project project = propertiesManager.getProject();

    if (PropertiesComponent.getInstance().getBoolean(NL_XML_PROPERTY_EDITOR)) {
      myTablePanel.setVisible(new NlXmlPropertyBuilder(propertiesManager, myTable, components, properties).build());
    }
    else {
      myTablePanel.setVisible(new NlPropertyTableBuilder(project, myTable, components, myProperties).build());
    }

    updateDefaultProperties(propertiesManager);
    myInspectorPanel.setComponent(components, properties, propertiesManager);
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
      if (PropertiesComponent.getInstance().getBoolean(NL_XML_PROPERTY_EDITOR)) {
        propertiesManager.updateSelection();
      }
      else {
        updateDefaultProperties(propertiesManager);
      }
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
    textLink.setHyperlinkText(
      viewAllProperties ? ViewAllPropertiesAction.VIEW_ALL_ATTRIBUTES : ViewAllPropertiesAction.VIEW_FEWER_ATTRIBUTES);
    textLink.addHyperlinkListener(event -> setAllPropertiesPanelVisible(event, viewAllProperties));
    textLink.setFocusable(false);
    HyperlinkLabel iconLink = new HyperlinkLabel();
    iconLink.setIcon(StudioIcons.LayoutEditor.Properties.TOGGLE_PROPERTIES);
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
    return myPropertiesViewMode == PropertiesViewMode.TABLE;
  }

  @Override
  public void setAllPropertiesPanelVisible(boolean viewAllProperties) {
    Component next = viewAllProperties ? myTable : myInspectorPanel;
    setAllPropertiesPanelVisibleInternal(viewAllProperties, next::requestFocus);
  }

  private void setAllPropertiesPanelVisibleInternal(boolean viewAllProperties, @Nullable Runnable onDone) {
    myPropertiesViewMode = viewAllProperties ? PropertiesViewMode.TABLE : PropertiesViewMode.INSPECTOR;
    myCardLayout.swipe(myCardPanel, myPropertiesViewMode.name(), JBCardLayout.SwipeDirection.AUTO, onDone);
    PropertiesComponent.getInstance().setValue(PROPERTY_MODE, myPropertiesViewMode.name());
  }

  @NotNull
  public PropertiesViewMode getPropertiesViewMode() {
    return myPropertiesViewMode;
  }

  @NotNull
  private static PropertiesViewMode getPropertiesViewModeInitially() {
    String name = PropertiesComponent.getInstance().getValue(PROPERTY_MODE, PropertiesViewMode.INSPECTOR.name());

    PropertiesViewMode mode;
    try {
      mode = PropertiesViewMode.valueOf(name);
    }
    catch (IllegalArgumentException e) {
      mode = PropertiesViewMode.INSPECTOR;
      Logger.getInstance(NlPropertiesPanel.class)
        .warn("There is no PropertiesViewMode called " + name + ", uses " + mode.name() + " instead", e);
      // store the new property mode as preference
      PropertiesComponent.getInstance().setValue(PROPERTY_MODE, mode.name());
    }
    return mode;
  }

  public void activatePreferredEditor(@NotNull String propertyName, boolean afterload) {
    Runnable selectEditor = () -> {
      // Restore a possibly minimized tool window
      if (myRestoreToolWindowCallback != null) {
        myRestoreToolWindowCallback.run();
      }
      // Set focus on the editor of preferred property
      myInspectorPanel.activatePreferredEditor(propertyName, afterload);
    };
    if (!isAllPropertiesPanelVisible()) {
      selectEditor.run();
    }
    else {
      // Switch to the inspector. The switch is animated, so we need to delay the editor selection.
      setAllPropertiesPanelVisibleInternal(false, selectEditor);
    }
  }

  private void scrollIntoView(@NotNull PropertyChangeEvent event) {
    if (needToScrollInView(event)) {
      Component newFocusedComponent = (Component)event.getNewValue();
      JComponent parent = (JComponent)newFocusedComponent.getParent();
      Rectangle bounds = newFocusedComponent.getBounds();
      if (newFocusedComponent == myTable) {
        bounds = myTable.getCellRect(myTable.getSelectedRow(), 1, true);
        bounds.x = 0;
      }
      parent.scrollRectToVisible(bounds);
    }
  }

  private boolean needToScrollInView(@NotNull PropertyChangeEvent event) {
    AWTEvent awtEvent = EventQueue.getCurrentEvent();
    if (!"focusOwner".equals(event.getPropertyName()) ||
        !(event.getNewValue() instanceof Component)) {
      return false;
    }
    Component newFocusedComponent = (Component)event.getNewValue();
    if (!isAncestorOf(newFocusedComponent) ||
        !(newFocusedComponent.getParent() instanceof JComponent) ||
        !(awtEvent instanceof CausedFocusEvent)) {
      return false;
    }
    CausedFocusEvent focusEvent = (CausedFocusEvent)awtEvent;
    switch (focusEvent.getCause()) {
      case TRAVERSAL:
      case TRAVERSAL_UP:
      case TRAVERSAL_DOWN:
      case TRAVERSAL_FORWARD:
      case TRAVERSAL_BACKWARD:
        break;
      default:
        return false;
    }
    return true;
  }

  @NotNull
  public InspectorPanel getInspector() {
    return myInspectorPanel;
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

  public enum PropertiesViewMode {
    TABLE,
    INSPECTOR
  }

  @VisibleForTesting
  static class MyFilter extends RowFilter<PTableModel, Integer> {
    private final SpeedSearchComparator myComparator = new SpeedSearchComparator(false);
    private String myPattern = "";

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

  private class MyFilterKeyListener extends KeyAdapter {

    @Override
    public void keyPressed(@NotNull KeyEvent event) {
      if (!myFilter.myPattern.isEmpty() && event.getKeyCode() == KeyEvent.VK_ENTER && event.getModifiers() == 0) {
        if (myPropertiesViewMode == PropertiesViewMode.TABLE) {
          enterInFilter(event);
        }
        else {
          myInspectorPanel.enterInFilter(event);
        }
      }
    }
  }
}
