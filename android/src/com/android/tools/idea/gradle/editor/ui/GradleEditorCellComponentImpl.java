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
package com.android.tools.idea.gradle.editor.ui;

import com.android.tools.idea.gradle.editor.GradleEditorNotificationListener;
import com.android.tools.idea.gradle.editor.action.GradleEntityHelpAction;
import com.android.tools.idea.gradle.editor.action.GradleEntityNavigateAction;
import com.android.tools.idea.gradle.editor.action.GradleEntityRemoveAction;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEntityDefinitionValueLocationAware;
import com.android.tools.idea.gradle.editor.metadata.StdGradleEditorEntityMetaData;
import com.google.common.base.Strings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.android.tools.idea.gradle.editor.ui.GradleEditorUiConstants.GRADLE_EDITOR_TABLE_PLACE;
import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

/**
 * Wraps target
 * {@link GradleEditorEntityUi#getComponent(JComponent, JTable, GradleEditorEntity, Project, boolean, boolean, boolean, boolean, int, int) UI}
 * which renders/allows to edit target cell's data.
 */
public class GradleEditorCellComponentImpl extends JBPanel implements GradleEditorCellComponent, ActionListener {

  private final JLabel myLabel = new JLabel();
  private final GridBag myConstraints = new GridBag().anchor(GridBagConstraints.WEST);
  private final JBPanel myToolBarPanel = new JBPanel(new GridBagLayout()) {
    @Override
    public void paint(Graphics g) {
      super.paint(g);
      myToolBarPanelBounds = getBounds();
    }
  };

  // We want to try to keep either renderer or editor within the same visual bounds, that's why we put payload control
  // inside a panel which delegates it's size processing to a pre-defined value.
  private final JBPanel myPayloadWrapper = new JBPanel(new GridBagLayout()) {
    @Override
    public Dimension getPreferredSize() {
      return myPreferredSize == null ? super.getPreferredSize() : myPreferredSize;
    }

    @Override
    public Dimension getMinimumSize() {
      return myPreferredSize == null ? super.getMinimumSize() : myPreferredSize;
    }

    @Override
    public Dimension getMaximumSize() {
      return myPreferredSize == null ? super.getMaximumSize() : myPreferredSize;
    }
  };
  private final Timer myTimer = new Timer(GradleEditorUiConstants.ENTITY_TOOLBAR_APPEARANCE_DELAY_MILLIS, this);

  private final ActionButton myHelpButton = button(new GradleEntityHelpAction());
  private final ActionButton myNavigateButton = button(new GradleEntityNavigateAction());
  private final ActionButton myRemoveButton = button(new GradleEntityRemoveAction());

  @NotNull private final GradleEditorEntityTable myTable;

  @Nullable private List<GradleEditorEntityUi<?>> myUis;
  @Nullable private Component myLastComponentUnderMouse;
  @Nullable private Rectangle myToolBarPanelBounds;
  @Nullable private Dimension myPreferredSize;
  private int myRow;
  private boolean myEditor;

  public GradleEditorCellComponentImpl(@NotNull GradleEditorEntityTable table) {
    super(new GridBagLayout());
    myTable = table;
    add(myPayloadWrapper, myConstraints);
    myToolBarPanel.add(myHelpButton, myConstraints);
    myToolBarPanel.add(myNavigateButton, myConstraints);
    myToolBarPanel.add(myRemoveButton, myConstraints);
    myToolBarPanel.setVisible(false);
    add(myToolBarPanel, new GridBag().anchor(GridBagConstraints.WEST).insets(0, 16, 0, 0));
    add(myLabel, new GridBag().weightx(1).fillCellHorizontally());

    myConstraints.weightx(1);
  }

  @NotNull
  private static ActionButton button(@NotNull AnAction action) {
    return new ActionButton(action, action.getTemplatePresentation().clone(), GRADLE_EDITOR_TABLE_PLACE, DEFAULT_MINIMUM_BUTTON_SIZE);
  }

  @NotNull
  @Override
  @SuppressWarnings("unchecked")
  public JComponent bind(@NotNull JTable table,
                         @Nullable Object value,
                         @NotNull Project project,
                         int row,
                         int column,
                         boolean editing,
                         boolean selected,
                         boolean focus) {
    myUis = null;
    myRow = -1;
    myEditor = editing;
    if (!(value instanceof GradleEditorEntity)) {
      myLabel.setText(value == null ? "<null>" : value.toString());
      return myLabel;
    }
    GradleEditorEntity entity = (GradleEditorEntity)value;
    GradleEditorEntityUiRegistry registry = ServiceManager.getService(GradleEditorEntityUiRegistry.class);

    myUis = registry.getEntityUis(entity);
    myRow = row;
    JComponent component = null;
    for (GradleEditorEntityUi ui : myUis) {
      component = ui.getComponent(component, table, entity, project, editing, selected, focus, false, row, column);
    }
    myLabel.setText(" ");
    myPayloadWrapper.removeAll();
    if (editing) {
      myToolBarPanel.setVisible(true);
    }

    // Calculate sizes from either renderer or editor components and use their max width.
    assert component != null;
    Dimension size = component.getPreferredSize();
    JComponent component2 = null;
    for (GradleEditorEntityUi ui : myUis) {
      component2 = ui.getComponent(component2, table, entity, project, !editing, selected, focus, true, row, column);
    }
    assert component2 != null;
    Dimension size2 = component2.getPreferredSize();
    myPreferredSize = new Dimension(Math.max(size.width, size2.width), Math.max(size.height, size2.height));

    myHelpButton.setVisible(!Strings.isNullOrEmpty(entity.getHelpId()));
    myNavigateButton.setVisible(entity instanceof GradleEntityDefinitionValueLocationAware &&
                                ((GradleEntityDefinitionValueLocationAware)entity).getDefinitionValueLocation() != null);
    myRemoveButton.setVisible(entity.getMetaData().contains(StdGradleEditorEntityMetaData.REMOVABLE));

    myPayloadWrapper.add(component, myConstraints);
    final Color backgroundColor;
    if (entity.getMetaData().contains(StdGradleEditorEntityMetaData.INJECTED)) {
      backgroundColor = GradleEditorUiConstants.INJECTED_BACKGROUND_COLOR;
    }
    else if (entity.getMetaData().contains(StdGradleEditorEntityMetaData.OUTGOING)) {
      backgroundColor = GradleEditorUiConstants.OUTGOING_BACKGROUND_COLOR;
    }
    else {
      backgroundColor = GradleEditorUiConstants.BACKGROUND_COLOR;
    }
    List<JPanel> panels = UIUtil.findComponentsOfType(this, JPanel.class);
    for (JPanel panel : panels) {
      panel.setBackground(backgroundColor);
    }
    return this;
  }

  @Override
  @Nullable
  public Object getValue(@NotNull Project project) {
    GradleEditorEntityTableModel model = myTable.getModel();
    if (myUis == null || myRow < 0 || myRow >= model.getRowCount()) {
      return null;
    }
    final GradleEditorEntity entity = (GradleEditorEntity)model.getValueAt(myRow, 0);
    GradleEditorNotificationListener publisher = project.getMessageBus().syncPublisher(GradleEditorNotificationListener.TOPIC);
    publisher.beforeChange();
    try {
      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @SuppressWarnings("unchecked")
        @Override
        public void run() {
          for (GradleEditorEntityUi ui : myUis) {
            ui.flush(entity);
          }
        }
      });
    }
    finally {
      publisher.afterChange();
    }
    return entity;
  }

  private void repaintMe() {
    int row = myTable.getRowByComponent(this);
    if (row >= 0) {
      myTable.repaintRows(row, row, false);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (myEditor) {
      return;
    }
    myToolBarPanel.setVisible(true);
    myTimer.stop();
    repaintMe();
  }

  @Nullable
  @Override
  public Rectangle onMouseMove(@NotNull MouseEvent event) {
    if (myEditor) {
      return null;
    }
    if (!myToolBarPanel.isVisible() && !myTimer.isRunning()) {
      onMouseEntered(event);
      return null;
    }
    int row = myTable.getRowByComponent(this);
    if (row < 0) {
      return null;
    }
    int topY = 0;
    for (int i = 0; i < row; i++) {
      topY += myTable.getRowHeight(i);
    }
    setBounds(0, 0, myTable.getWidth(), myTable.getRowHeight(row));
    Point pointInCurrentControl = new Point(event.getX(), event.getY() - topY);
    Component c = SwingUtilities.getDeepestComponentAt(this, pointInCurrentControl.x, pointInCurrentControl.y);
    boolean dirty = false;
    if (myLastComponentUnderMouse != null && myLastComponentUnderMouse != c) {
      Point p = SwingUtilities.convertPoint(this, pointInCurrentControl, myLastComponentUnderMouse);
      MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_EXITED, event.getWhen(), event.getModifiers(), p.x, p.y, event.getClickCount(),
                                    event.isPopupTrigger(), event.getButton());
      myLastComponentUnderMouse.dispatchEvent(e);
      dirty = true;
      Cursor cursor = myLastComponentUnderMouse.getCursor();
      if (cursor != null && cursor == myTable.getCursor()) {
        myTable.setCursor(null);
      }
    }
    if (c != myLastComponentUnderMouse) {
      Point p = SwingUtilities.convertPoint(this, pointInCurrentControl, c);
      MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_ENTERED, event.getWhen(), event.getModifiers(), p.x, p.y, event.getClickCount(),
                                    event.isPopupTrigger(), event.getButton());
      c.dispatchEvent(e);
      dirty = true;
      myTable.setCursor(c.getCursor());
    }
    myLastComponentUnderMouse = c;
    Point tableLocationOnScreen = myTable.getLocationOnScreen();
    if (!dirty) {
      return null;
    }
    if (myToolBarPanelBounds == null) {
      // Repaint the whole row.
      return new Rectangle(tableLocationOnScreen.x, tableLocationOnScreen.y + topY, myTable.getWidth(), myTable.getRowHeight(row));
    }
    else {
      return new Rectangle(tableLocationOnScreen.x + myToolBarPanelBounds.x, tableLocationOnScreen.y + topY + myToolBarPanelBounds.y,
                           myToolBarPanelBounds.width, myToolBarPanelBounds.height);
    }
  }

  @Override
  @Nullable
  public Rectangle onMouseEntered(@NotNull MouseEvent event) {
    if (!myEditor) {
      myTimer.restart();
    }
    return null;
  }

  @Override
  @Nullable
  public Rectangle onMouseExited() {
    if (!myEditor) {
      myTable.setCursor(null);
      myToolBarPanel.setVisible(false);
      myTimer.stop();
      repaintMe();
    }
    return null;
  }

  @Override
  public String toString() {
    return String.valueOf(System.identityHashCode(this));
  }
}