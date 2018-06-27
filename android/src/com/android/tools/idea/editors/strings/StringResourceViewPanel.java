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

import com.android.tools.adtui.font.FontUtil;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.editors.strings.table.*;
import com.android.tools.idea.rendering.Locale;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusListener;

public final class StringResourceViewPanel implements Disposable {
  private final JBLoadingPanel myLoadingPanel;
  private JPanel myContainer;
  private StringResourceTable myTable;
  private JTextComponent myKeyTextField;
  @VisibleForTesting TextFieldWithBrowseButton myDefaultValueTextField;
  private TextFieldWithBrowseButton myTranslationTextField;
  private JPanel myToolbarPanel;

  private final AndroidFacet myFacet;

  private GoToDeclarationAction myGoToAction;
  private DeleteStringAction myDeleteAction;
  private RemoveKeysAction myRemoveKeysAction;

  StringResourceViewPanel(AndroidFacet facet, Disposable parentDisposable) {
    myFacet = facet;
    Disposer.register(parentDisposable, this);

    myToolbarPanel.add(createToolbar().getComponent());

    GridConstraints constraints = new GridConstraints();
    constraints.setFill(GridConstraints.FILL_BOTH);
    constraints.setRow(1);

    myContainer.add(myTable.getScrollPane(), constraints);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this, 200);
    myLoadingPanel.setLoadingText("Loading string resource data");
    myLoadingPanel.setName("translationsEditor");
    myLoadingPanel.add(myContainer);
    myLoadingPanel.startLoading();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new ResourceLoadingTask(this).queue();
    }
  }

  @Override
  public void dispose() {
  }

  public void removeSelectedKeys() {
    myRemoveKeysAction.actionPerformed(null);
  }

  void addDocumentListener(@NotNull DocumentListener listener) {
    StringTableCellEditor editor = (StringTableCellEditor)myTable.getDefaultEditor(String.class);
    assert editor != null;

    editor.getComponent().getDocument().addDocumentListener(listener);

    myDefaultValueTextField.getTextField().getDocument().addDocumentListener(listener);
    myTranslationTextField.getTextField().getDocument().addDocumentListener(listener);
  }

  void addFocusListener(@NotNull FocusListener listener) {
    DefaultCellEditor editor = (DefaultCellEditor)myTable.getDefaultEditor(String.class);
    assert editor != null;

    editor.getComponent().addFocusListener(listener);

    myDefaultValueTextField.getTextField().addFocusListener(listener);
    myTranslationTextField.getTextField().addFocusListener(listener);
  }

  private void createUIComponents() {
    createTable();
    createTablePopupMenu();

    myKeyTextField = new TranslationsEditorTextField(myTable, StringResourceTableModel.KEY_COLUMN);

    createDefaultValueTextField();
    createTranslationTextField();
  }

  private void createTable() {
    myRemoveKeysAction = new RemoveKeysAction(this);
    myDeleteAction = new DeleteStringAction(this);
    myGoToAction = new GoToDeclarationAction(this);

    myTable = new StringResourceTable();

    myTable.putInActionMap("delete", myDeleteAction);
    myTable.addFrozenColumnTableListener(new CellSelectionListener());
    myTable.addFrozenColumnTableListener(new RemoveLocaleMouseListener(this));
  }

  private void createTablePopupMenu() {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem goTo = menu.add(myGoToAction);
    JMenuItem delete = menu.add(myDeleteAction);

    myTable.addFrozenColumnTableListener(new FrozenColumnTableListener() {
      @Override
      public void cellPopupTriggered(@NotNull FrozenColumnTableEvent event) {
        myGoToAction.update(goTo, event);
        myDeleteAction.update(delete, event);

        if (goTo.isVisible() || delete.isVisible()) {
          Point point = event.getPoint();
          menu.show(event.getSubcomponent(), point.x, point.y);
        }
      }
    });
  }

  private void createDefaultValueTextField() {
    JTextField textField = new TranslationsEditorTextField(myTable, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
    new TranslationsEditorPasteAction().registerCustomShortcutSet(textField, this);

    myDefaultValueTextField = new TextFieldWithBrowseButton(textField, new ShowMultilineActionListener(), this);
    myDefaultValueTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
  }

  private void createTranslationTextField() {
    JTextField textField = new TranslationsEditorTextField(myTable, myTable::getSelectedModelColumnIndex);
    new TranslationsEditorPasteAction().registerCustomShortcutSet(textField, this);

    myTranslationTextField = new TextFieldWithBrowseButton(textField, new ShowMultilineActionListener(), this);
    myTranslationTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  void reloadData() {
    myLoadingPanel.setLoadingText("Updating string resource data");
    myLoadingPanel.startLoading();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new ResourceLoadingTask(this).queue();
    }
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TranslationsEditorToolbar", group, true);

    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setName("toolbar");

    group.add(new AddKeyAction(this));
    group.add(myRemoveKeysAction);
    group.add(new AddLocaleAction(this));
    group.add(new FilterKeysAction(myTable));
    group.add(new FilterLocalesAction(myTable));
    group.add(new ReloadStringResourcesAction(this));
    group.add(new BrowserHelpAction("Translations editor", "https://developer.android.com/r/studio-ui/translations-editor.html"));

    return toolbar;
  }

  @NotNull
  JBLoadingPanel getLoadingPanel() {
    return myLoadingPanel;
  }

  @NotNull
  public StringResourceTable getTable() {
    return myTable;
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return myTable.getScrollableTable();
  }

  private final class CellSelectionListener implements FrozenColumnTableListener {
    @Override
    public void selectedCellChanged() {
      if (myTable.getSelectedColumnCount() != 1 || myTable.getSelectedRowCount() != 1) {
        setTextAndEditable(myKeyTextField, "", false);
        setTextAndEditable(myDefaultValueTextField.getTextField(), "", false);
        setTextAndEditable(myTranslationTextField.getTextField(), "", false);
        myDefaultValueTextField.getButton().setEnabled(false);
        myTranslationTextField.getButton().setEnabled(false);
        return;
      }

      myKeyTextField.setEnabled(true);
      myDefaultValueTextField.setEnabled(true);
      myTranslationTextField.setEnabled(true);
      StringResourceTableModel model = myTable.getModel();

      int row = myTable.getSelectedModelRowIndex();
      int column = myTable.getSelectedModelColumnIndex();
      Object locale = model.getLocale(column);

      // TODO: Keys are not editable; we want them to be refactor operations
      setTextAndEditable(myKeyTextField, model.getKey(row).getName(), false);

      String defaultValue = (String)model.getValueAt(row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
      boolean defaultValueEditable = isValueEditableInline(defaultValue); // don't allow editing multiline chars in a text field
      setTextAndEditable(myDefaultValueTextField.getTextField(), defaultValue, defaultValueEditable);
      myDefaultValueTextField.getButton().setEnabled(true);

      boolean translationEditable = false;
      String translation = "";
      if (locale != null) {
        translation = (String)model.getValueAt(row, column);
        translationEditable = isValueEditableInline(translation); // don't allow editing multiline chars in a text field
      }
      setTextAndEditable(myTranslationTextField.getTextField(), translation, translationEditable);
      myTranslationTextField.getButton().setEnabled(locale != null);
    }
  }

  /**
   * Check if the provided value can be edited inline or has to be edited using the multiline text field.
   *
   * <p>A Value can be edited inline if it contains no "\n" character.
   *
   * @param value The value to check
   * @return true is the value can be edited inline, false if it has to be edited with the multiline text field
   */
  private static boolean isValueEditableInline(@NotNull String value) {
    return !StringUtil.containsChar(value, '\n');
  }

  private static void setTextAndEditable(@NotNull JTextComponent component, @NotNull String text, boolean editable) {
    component.setText(text);
    component.setCaretPosition(0);
    component.setEditable(editable);
    // If a text component is not editable when it gains focus and becomes editable while still focused,
    // the caret does not appear, so we need to set the caret visibility manually
    component.getCaret().setVisible(editable && component.hasFocus());

    component.setFont(FontUtil.getFontAbleToDisplay(text, component.getFont()));
  }

  private class ShowMultilineActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myTable.getSelectedRowCount() != 1 || myTable.getSelectedColumnCount() != 1) {
        return;
      }

      int row = myTable.getSelectedModelRowIndex();
      int column = myTable.getSelectedModelColumnIndex();

      StringResourceTableModel model = myTable.getModel();
      String value = (String)model.getValueAt(row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

      Locale locale = model.getLocale(column);
      String translation = locale == null ? null : (String)model.getValueAt(row, column);

      MultilineStringEditorDialog d = new MultilineStringEditorDialog(myFacet, model.getKey(row).getName(), value, locale, translation);
      if (d.showAndGet()) {
        if (!StringUtil.equals(value, d.getDefaultValue())) {
          model.setValueAt(d.getDefaultValue(), row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
          setTextAndEditable(myDefaultValueTextField.getTextField(), d.getDefaultValue(), isValueEditableInline(d.getDefaultValue()));
        }

        if (locale != null && !StringUtil.equals(translation, d.getTranslation())) {
          model.setValueAt(d.getTranslation(), row, column);
          setTextAndEditable(myTranslationTextField.getTextField(), d.getTranslation(), isValueEditableInline(d.getTranslation()));
        }
      }
    }
  }
}
