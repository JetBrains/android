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
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class StringResourceViewPanel {
  private final AndroidFacet myFacet;
  private JPanel myContainer;
  private JBLoadingPanel myLoadingPanel;
  private JBTable myTable;

  private JTextField myKey;
  private TextFieldWithBrowseButton myDefaultValueWithBrowseBtn;
  private final JTextField myDefaultValue;
  private TextFieldWithBrowseButton myTranslationWithBrowseBtn;
  private final JTextField myTranslation;

  private JPanel myToolbarPanel;

  private JPanel myWarningPanel;
  private JBLabel myWarningLabel;


  private LocalResourceRepository myResourceRepository;
  private long myModificationCount;

  private StringResourceData myData;
  private final StringResourceTableModel myTableModel;

  public StringResourceViewPanel(AndroidFacet facet, Disposable parentDisposable) {
    myFacet = facet;

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), parentDisposable, 200);
    myLoadingPanel.add(myContainer);

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

    initEditPanel();
    initTable();

    myTableModel = new StringResourceTableModel(this, myFacet, null);
    myTable.setModel(myTableModel);
    myLoadingPanel.setLoadingText("Loading string resource data");
    myLoadingPanel.startLoading();
    new ParseTask("Loading string resource data").queue();
  }

  public void reloadData() {
    myLoadingPanel.setLoadingText("Updating string resource data");
    myLoadingPanel.startLoading();
    new ParseTask("Updating string resource data").queue();
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);

    final AnAction addKeyAction = new AnAction("Add Key", "", AllIcons.ToolbarDecorator.Add) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myData != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        NewStringKeyDialog dialog = new NewStringKeyDialog(myFacet);
        if (dialog.showAndGet()) {
          StringsWriteUtils
            .createItem(myFacet.getModule().getProject(), dialog.getResFolder(), null, dialog.getKey(), dialog.getDefaultValue(), true);
          reloadData();
        }
      }
    };
    group.add(addKeyAction);

    final AnAction addLocaleAction = new AnAction("Add Locale", "", AndroidIcons.Globe) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myData != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        List<Locale> currentLocales = myData.getLocales();
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
            Map<String, ResourceItem> defaultValues = myData.getDefaultValues();
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
            reloadData();
          }
        }).createPopup().show(JBPopupFactory.getInstance().guessBestPopupLocation(toolbar.getToolbarDataContext()));
      }
    };
    group.addAction(addLocaleAction);

    return toolbar;
  }

  public boolean dataIsCurrent() {
    return myResourceRepository != null && myModificationCount >= myResourceRepository.getModificationCount();
  }

  private void initEditPanel() {
    KeyListener keyListener = new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        JTextComponent component = (JTextComponent)e.getComponent();
        onTextFieldUpdate(component);
      }
    };
    myKey.addKeyListener(keyListener);
    myDefaultValue.addKeyListener(keyListener);
    myTranslation.addKeyListener(keyListener);
  }

  private void onTextFieldUpdate(JTextComponent component) {
    StringResourceTableModel model = (StringResourceTableModel)myTable.getModel();
    if (myTable.getSelectedColumnCount() != 1 || myTable.getSelectedRowCount() != 1) {
      return;
    }

    int row = myTable.getSelectedRow();
    int column = myTable.getSelectedColumn();

    String value = component.getText();
    model.setValueAt(value, row, column);
  }

  private void initTable() {
    myTable.setCellSelectionEnabled(true);
    myTable.getTableHeader().setReorderingAllowed(false);

    MouseAdapter headerListener = new HeaderCellSelectionListener(myTable);
    myTable.getTableHeader().addMouseListener(headerListener);
    myTable.getTableHeader().addMouseMotionListener(headerListener);

    myTable.getSelectionModel().addListSelectionListener(new CellSelectionListener());

    myTable.setDefaultEditor(String.class, new StringsCellEditor());
    myTable.getParent().addComponentListener(new ResizeListener(myTable));
    new TableSpeedSearch(myTable);
  }

  @NotNull
  public JPanel getComponent() {
    return myLoadingPanel;
  }

  @NotNull
  public JBTable getPreferredFocusedComponent() {
    return myTable;
  }

  private void createUIComponents() {
    myTable = new JBTable();
  }

  private class ParseTask extends Task.Backgroundable {
    private AtomicReference<LocalResourceRepository> myResourceRepositoryRef = new AtomicReference<LocalResourceRepository>(null);
    private AtomicReference<StringResourceData> myResourceDataRef = new AtomicReference<StringResourceData>(null);

    public ParseTask(String description) {
      super(myFacet.getModule().getProject(), description, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      LocalResourceRepository moduleResources = myFacet.getModuleResources(true);
      myResourceRepositoryRef.set(moduleResources);
      myResourceDataRef.set(StringResourceParser.parse(myFacet, moduleResources));
    }

    @Override
    public void onSuccess() {
      myLoadingPanel.stopLoading();

      myData = myResourceDataRef.get();
      myResourceRepository = myResourceRepositoryRef.get();
      myModificationCount = myResourceRepository.getModificationCount();

      myTableModel.setData(myData);
      myTableModel.fireTableStructureChanged();
      ColumnUtil.setColumns(myTable);
    }

    @Override
    public void onCancel() {
      myLoadingPanel.stopLoading();
    }
  }

  private class CellSelectionListener implements ListSelectionListener {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (e.getValueIsAdjusting()) {
        return;
      }

      String key = "";
      String defaultValue = "";
      String translation = "";

      boolean keyEditable = false;
      boolean defaultValueEditable = false;
      boolean translationEditable = false;

      StringResourceTableModel model = (StringResourceTableModel) myTable.getModel();
      if (myTable.getSelectedRowCount() == 1 && myTable.getSelectedColumnCount() == 1) {
        int row = myTable.getSelectedRow();
        int column = myTable.getSelectedColumn();

        key = String.valueOf(model.getValue(row, ConstantColumn.KEY.ordinal()));
        defaultValue = String.valueOf(model.getValue(row, ConstantColumn.DEFAULT_VALUE.ordinal()));
        keyEditable = true;
        defaultValueEditable = true;

        if (column >= ConstantColumn.COUNT) {
          translation = String.valueOf(model.getValue(row, column));
          translationEditable = true;
        }
      }

      setTextAndEditable(myKey, key, keyEditable);
      setTextAndEditable(myDefaultValue, defaultValue, defaultValueEditable);
      setTextAndEditable(myTranslation, translation, translationEditable);
    }

    private void setTextAndEditable(@NotNull JTextComponent component, @NotNull String text, boolean editable) {
      component.setText(text);
      component.setCaretPosition(0);
      component.setEditable(editable);
      // If a text component is not editable when it gains focus and becomes editable while still focused,
      // the caret does not appear, so we need to set the caret visibility manually
      component.getCaret().setVisible(editable && component.hasFocus());

      component.setFont(CellRenderer.getFontAbleToDisplay(text, component.getFont()));
    }
  }
}
