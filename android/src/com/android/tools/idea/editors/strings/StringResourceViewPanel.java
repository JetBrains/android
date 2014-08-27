/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.table.*;
import com.android.tools.idea.rendering.Locale;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StringResourceViewPanel {
  private final AndroidFacet myFacet;
  private JPanel myContainer;
  private JBTable myTable;

  private JTextField myKey;
  private TextFieldWithBrowseButton myDefaultValueWithBrowseBtn;
  private final JTextField myDefaultValue;
  private TextFieldWithBrowseButton myTranslationWithBrowseBtn;
  private JPanel myToolbarPanel;
  private JPanel myWarningPanel;
  private JBLabel myWarningLabel;
  private final JTextField myTranslation;
  private StringResourceDataController myController;

  public StringResourceViewPanel(AndroidFacet facet) {
    myFacet = facet;

    Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.NOTIFICATION_BACKGROUND);
    if (color == null) {
      color = UIUtil.getToolTipBackground();
    }
    myWarningLabel.setOpaque(false);
    myWarningPanel.setBackground(color);

    ActionToolbar toolbar = createToolbar();
    myToolbarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
    myToolbarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));

    myDefaultValueWithBrowseBtn.setButtonIcon(AllIcons.Actions.ShowViewer);
    myTranslationWithBrowseBtn.setButtonIcon(AllIcons.Actions.ShowViewer);

    myDefaultValue = myDefaultValueWithBrowseBtn.getTextField();
    myTranslation = myTranslationWithBrowseBtn.getTextField();

    myDefaultValueWithBrowseBtn.getButton().setVisible(false);
    myTranslationWithBrowseBtn.getButton().setVisible(false);

    initEditPanel();
    initTable();
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);

    final AnAction addKeyAction = new AnAction("Add Key", "", AllIcons.ToolbarDecorator.Add) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myController != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        NewStringKeyDialog dialog = new NewStringKeyDialog(myFacet);
        if (dialog.showAndGet()) {
          StringsWriteUtils
            .createItem(myFacet.getModule().getProject(), dialog.getResFolder(), null, dialog.getKey(), dialog.getDefaultValue(), true);
          ((StringResourceTableModel)myTable.getModel()).getController().updateData();
        }
      }
    };
    group.add(addKeyAction);

    final AnAction addLocaleAction = new AnAction("Add Locale", "", AndroidIcons.Globe) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myController != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        List<Locale> currentLocales = myController.getData().getLocales();
        List<Locale> missingLocales = LocaleMenuAction.getAllLocales();
        missingLocales.removeAll(currentLocales);
        Collections.sort(missingLocales, Locale.LANGUAGE_NAME_COMPARATOR);

        final JBList list = new JBList(missingLocales);
        list.setFixedCellHeight(20);
        list.setCellRenderer(new ColoredListCellRenderer<Locale>() {
          @Override
          protected void customizeCellRenderer(JList list, Locale value, int index, boolean selected, boolean hasFocus) {
            append(LocaleMenuAction.getLocaleLabel(value, false));
            setIcon(value.getFlagImage());
          }
        });
        new ListSpeedSearch(list) {
          @Override
          protected String getElementText(Object element) {
            if (element instanceof Locale) {
              return LocaleMenuAction.getLocaleLabel((Locale)element, false);
            }
            return super.getElementText(element);
          }
        };

        JBPopupFactory.getInstance().createListPopupBuilder(list).setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            // pick some value to add to this locale
            Map<String, ResourceItem> defaultValues = myController.getData().getDefaultValues();
            String key = "app_name";
            ResourceItem defaultValue = defaultValues.get(key);

            if (defaultValue == null) {
              Map.Entry<String, ResourceItem> firstEntry = defaultValues.entrySet().iterator().next();
              key = firstEntry.getKey();
              defaultValue = firstEntry.getValue();
            }

            Locale l = (Locale)list.getSelectedValue();
            StringsWriteUtils.createItem(myFacet.getModule().getProject(), myFacet.getPrimaryResourceDir(), l, key,
                                         StringResourceData.resourceToString(defaultValue), true);
            ((StringResourceTableModel)myTable.getModel()).getController().updateData();
          }
        }).createPopup().show(JBPopupFactory.getInstance().guessBestPopupLocation(toolbar.getToolbarDataContext()));
      }
    };
    group.addAction(addLocaleAction);

    return toolbar;
  }

  private void initEditPanel() {
    FocusListener editFocusListener = new EditFocusListener(myTable, myKey, myDefaultValue, myTranslation);
    myKey.addFocusListener(editFocusListener);
    myDefaultValue.addFocusListener(editFocusListener);
    myTranslation.addFocusListener(editFocusListener);
  }

  private void initTable() {
    myTable.setCellSelectionEnabled(true);
    myTable.getTableHeader().setReorderingAllowed(false);

    MouseAdapter headerListener = new HeaderCellSelectionListener(myTable);
    myTable.getTableHeader().addMouseListener(headerListener);
    myTable.getTableHeader().addMouseMotionListener(headerListener);

    CellSelectionListener selectionListener = new CellSelectionListener(myTable, myKey, myDefaultValue, myTranslation);
    myTable.getSelectionModel().addListSelectionListener(selectionListener);
    myTable.getColumnModel().getSelectionModel().addListSelectionListener(selectionListener);

    myTable.setDefaultEditor(String.class, new MultilineCellEditor());
    myTable.getParent().addComponentListener(new ResizeListener(myTable));
    new TableSpeedSearch(myTable) {
      @Override
      public int getElementCount() {
        // TableSpeedSearch computes the element count from the underlying model, which is problematic when not all cells are visible
        return myComponent.getRowCount() * myComponent.getColumnCount();
      }
    };
  }

  public void initDataController(@NotNull StringResourceDataController controller) {
    myController = controller;
    myTable.setModel(new StringResourceTableModel(controller));
    ColumnUtil.setColumns(myTable);
  }

  public void onDataUpdated() {
    StringResourceTableModel model = (StringResourceTableModel) myTable.getModel();
    model.fireTableStructureChanged();
    ColumnUtil.setColumns(myTable);
  }

  @NotNull
  public JPanel getComponent() {
    return myContainer;
  }

  @NotNull
  public JBTable getPreferredFocusedComponent() {
    return myTable;
  }
}
