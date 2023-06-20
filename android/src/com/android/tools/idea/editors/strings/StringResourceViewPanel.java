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

import com.android.ide.common.resources.Locale;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.editors.strings.action.AddKeyAction;
import com.android.tools.idea.editors.strings.action.AddLocaleAction;
import com.android.tools.idea.editors.strings.action.FilterKeysAction;
import com.android.tools.idea.editors.strings.action.FilterLocalesAction;
import com.android.tools.idea.editors.strings.action.ReloadStringResourcesAction;
import com.android.tools.idea.editors.strings.action.RemoveKeysAction;
import com.android.tools.idea.editors.strings.action.TranslationsEditorPasteAction;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.editors.strings.table.FrozenColumnTableEvent;
import com.android.tools.idea.editors.strings.table.FrozenColumnTableListener;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
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
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceViewPanel implements Disposable {
  private final AndroidFacet myFacet;

  private StringResourceTable myTable;
  private @Nullable Component myToolbar;
  private @Nullable Component myScrollPane;
  private final @NotNull JComponent myXmlLabel;
  @VisibleForTesting final JTextComponent myXmlTextField;
  private final @NotNull Component myKeyLabel;
  private JTextComponent myKeyTextField;
  private final @NotNull Component myDefaultValueLabel;

  @VisibleForTesting
  TextFieldWithBrowseButton myDefaultValueTextField;

  private final @NotNull Component myTranslationLabel;
  private TextFieldWithBrowseButton myTranslationTextField;
  private @Nullable Container myPanel;
  private final @NotNull JBLoadingPanel myLoadingPanel;

  private GoToDeclarationAction myGoToAction;
  private DeleteStringAction myDeleteAction;

  StringResourceViewPanel(AndroidFacet facet, Disposable parentDisposable) {
    myFacet = facet;
    Disposer.register(parentDisposable, this);

    initTable();
    initToolbar();

    myXmlLabel = new JBLabel("XML:", SwingConstants.RIGHT);
    myXmlTextField = new JBTextField();
    myXmlTextField.setEnabled(false);
    myXmlTextField.setName("xmlTextField");

    myKeyLabel = new JBLabel("Key:", SwingConstants.RIGHT);
    initKeyTextField();
    myDefaultValueLabel = new JBLabel("Default value:", SwingConstants.RIGHT);
    initDefaultValueTextField();
    myTranslationLabel = new JBLabel("Translation:", SwingConstants.RIGHT);
    initTranslationTextField();
    initPanel();

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), this, 200);
    myLoadingPanel.setLoadingText("Loading string resource data");
    myLoadingPanel.setName("translationsEditor");
    myLoadingPanel.add(myPanel);
    myLoadingPanel.startLoading();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new ResourceLoadingTask(this).queue();
    }
  }

  @Override
  public void dispose() {
  }

  private void initTable() {
    myDeleteAction = new DeleteStringAction(this);
    myGoToAction = new GoToDeclarationAction(myFacet.getModule().getProject());

    myTable = new StringResourceTable();

    myTable.putInActionMap("delete", myDeleteAction);
    myTable.addFrozenColumnTableListener(new CellSelectionListener());
    myTable.addFrozenColumnTableListener(new RemoveLocaleMouseListener(this));

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

    myScrollPane = myTable.getScrollPane();
  }

  private void initToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddKeyAction());
    group.add(new RemoveKeysAction());
    group.add(new AddLocaleAction());
    group.add(new FilterKeysAction());
    group.add(new FilterLocalesAction());
    group.add(new ReloadStringResourcesAction());
    group.add(new BrowserHelpAction("Translations editor", "https://developer.android.com/r/studio-ui/translations-editor.html"));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TranslationsEditorToolbar", group, true);

    myToolbar = toolbar.getComponent();
    myToolbar.setName("toolbar");
  }

  private void initKeyTextField() {
    myKeyTextField = new TranslationsEditorTextField(myTable, () -> StringResourceTableModel.KEY_COLUMN);

    myKeyTextField.setEnabled(false);
    myKeyTextField.setName("keyTextField");
  }

  private void initDefaultValueTextField() {
    JTextField textField = new TranslationsEditorTextField(myTable, () -> StringResourceTableModel.DEFAULT_VALUE_COLUMN);
    new TranslationsEditorPasteAction().registerCustomShortcutSet(textField, this);

    myDefaultValueTextField = new TextFieldWithBrowseButton(textField, new ShowMultilineActionListener(), this);

    myDefaultValueTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
    myDefaultValueTextField.setName("defaultValueTextField");
  }

  private void initTranslationTextField() {
    JTextField textField = new TranslationsEditorTextField(myTable, myTable::getSelectedModelColumnIndex);
    new TranslationsEditorPasteAction().registerCustomShortcutSet(textField, this);

    myTranslationTextField = new TextFieldWithBrowseButton(textField, new ShowMultilineActionListener(), this);

    myTranslationTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
    myTranslationTextField.setEnabled(false);
    myTranslationTextField.setName("translationTextField");
  }

  private void initPanel() {
    myPanel = new JBPanel<>();
    GroupLayout layout = new GroupLayout(myPanel);

    layout.linkSize(SwingConstants.HORIZONTAL, myXmlLabel, myKeyLabel, myDefaultValueLabel, myTranslationLabel);
    layout.linkSize(SwingConstants.VERTICAL, myXmlLabel, myXmlTextField);
    layout.linkSize(SwingConstants.VERTICAL, myKeyLabel, myKeyTextField);
    layout.linkSize(SwingConstants.VERTICAL, myDefaultValueLabel, myDefaultValueTextField);
    layout.linkSize(SwingConstants.VERTICAL, myTranslationLabel, myTranslationTextField);

    Group horizontalGroup = layout.createParallelGroup()
      .addComponent(myToolbar)
      .addComponent(myScrollPane)
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myXmlLabel)
                  .addComponent(myXmlTextField))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myKeyLabel)
                  .addComponent(myKeyTextField))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myDefaultValueLabel)
                  .addComponent(myDefaultValueTextField))
      .addGroup(layout.createSequentialGroup()
                  .addComponent(myTranslationLabel)
                  .addComponent(myTranslationTextField));

    Group verticalGroup = layout.createSequentialGroup()
      .addComponent(myToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
      .addComponent(myScrollPane)
      .addGroup(layout.createParallelGroup()
                  .addComponent(myXmlLabel)
                  .addComponent(myXmlTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addGroup(layout.createParallelGroup()
                  .addComponent(myKeyLabel)
                  .addComponent(myKeyTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addGroup(layout.createParallelGroup()
                  .addComponent(myDefaultValueLabel)
                  .addComponent(myDefaultValueTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
      .addGroup(layout.createParallelGroup()
                  .addComponent(myTranslationLabel)
                  .addComponent(myTranslationTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myPanel.setLayout(layout);
  }

  public void reloadData() {
    myLoadingPanel.setLoadingText("Updating string resource data");
    myLoadingPanel.startLoading();

    new ResourceLoadingTask(this).queue();
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
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
      if (!myTable.hasSelectedCell()) {
        setTextAndEditable(myXmlTextField, "", false);
        setTextAndEditable(myKeyTextField, "", false);
        setTextAndEditable(myDefaultValueTextField.getTextField(), "", false);
        setTextAndEditable(myTranslationTextField.getTextField(), "", false);
        myDefaultValueTextField.getButton().setEnabled(false);
        myTranslationTextField.getButton().setEnabled(false);
        return;
      }

      myXmlTextField.setEnabled(true);
      myKeyTextField.setEnabled(true);
      myDefaultValueTextField.setEnabled(true);
      myTranslationTextField.setEnabled(true);
      StringResourceTableModel model = myTable.getModel();

      int row = myTable.getSelectedModelRowIndex();
      int column = myTable.getSelectedModelColumnIndex();
      Locale locale = model.getLocale(column);

      StringResourceKey key = model.getKey(row);
      StringResourceData data = model.getData();
      if (data == null) {
        setTextAndEditable(myXmlTextField, "", false);
      }
      else {
        setTextAndEditable(myXmlTextField, data.getStringResource(key).getTagText(locale), false);
      }
      // TODO: Keys are not editable; we want them to be refactor operations
      setTextAndEditable(myKeyTextField, key.getName(), false);

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

    component.setFont(StringResourceEditor.getFont(component.getFont()));
  }

  private class ShowMultilineActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (!myTable.hasSelectedCell()) {
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
