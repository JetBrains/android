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

import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.res.MultiResourceRepository;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;

final class StringResourceViewPanel implements Disposable, HyperlinkListener {
  private static final boolean HIDE_TRANSLATION_ORDER_LINK = Boolean.getBoolean("hide.order.translations");

  private final JBLoadingPanel myLoadingPanel;
  private JPanel myContainer;
  private StringResourceTable myTable;
  private JTextComponent myKeyTextField;
  @VisibleForTesting TextFieldWithBrowseButton myDefaultValueTextField;
  private TextFieldWithBrowseButton myTranslationTextField;
  private JPanel myToolbarPanel;

  private final AndroidFacet myFacet;
  private StringResourceRepository myResourceRepository;

  private GoToDeclarationAction myGoToAction;
  private DeleteStringAction myDeleteAction;
  private RemoveKeysAction myRemoveKeysAction;

  StringResourceViewPanel(AndroidFacet facet, Disposable parentDisposable) {
    myFacet = facet;

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), parentDisposable, 200);
    myLoadingPanel.add(myContainer);

    ActionToolbar toolbar = createToolbar();
    myToolbarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);

    if (!HIDE_TRANSLATION_ORDER_LINK) {
      HyperlinkLabel hyperlinkLabel = new HyperlinkLabel("Order a translation...");
      myToolbarPanel.add(hyperlinkLabel, BorderLayout.EAST);
      hyperlinkLabel.addHyperlinkListener(this);
      myToolbarPanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    }

    initTable();
    Disposer.register(parentDisposable, this);

    myLoadingPanel.setLoadingText("Loading string resource data");
    myLoadingPanel.startLoading();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new ParseTask("Loading string resource data").queue();
    }
  }

  public void removeSelectedKeys() {
    myRemoveKeysAction.actionPerformed(null);
  }

  void addDocumentListener(@NotNull DocumentListener listener) {
    ((StringTableCellEditor)myTable.getDefaultEditor(String.class)).getComponent().getDocument().addDocumentListener(listener);
    myDefaultValueTextField.getTextField().getDocument().addDocumentListener(listener);
    myTranslationTextField.getTextField().getDocument().addDocumentListener(listener);
  }

  void addFocusListener(@NotNull FocusListener listener) {
    ((StringTableCellEditor)myTable.getDefaultEditor(String.class)).getComponent().addFocusListener(listener);
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
    myTable = new StringResourceTable();

    myRemoveKeysAction = new RemoveKeysAction(this);
    myDeleteAction = new DeleteStringAction(this);
    myGoToAction = new GoToDeclarationAction(this);

    ActionMap actionMap = myTable.getActionMap();
    actionMap.put("delete", myDeleteAction);
  }

  private void createTablePopupMenu() {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem goTo = menu.add(myGoToAction);
    JMenuItem delete = menu.add(myDeleteAction);

    myTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        openPopup(e);
      }

      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        openPopup(e);
      }

      private void openPopup(@NotNull MouseEvent e) {
        if (!e.isPopupTrigger()) {
          return;
        }
        myGoToAction.update(goTo, e);
        myDeleteAction.update(delete, e);
        if (goTo.isVisible() || delete.isVisible()) {
          menu.show(myTable, e.getX(), e.getY());
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
    JTextField textField = new TranslationsEditorTextField(myTable, myTable::getSelectedColumnModelIndex);
    new TranslationsEditorPasteAction().registerCustomShortcutSet(textField, this);

    myTranslationTextField = new TextFieldWithBrowseButton(textField, new ShowMultilineActionListener(), this);
    myTranslationTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  // TODO Delete this method
  @Override
  public void dispose() {
  }

  void reloadData() {
    myLoadingPanel.setLoadingText("Updating string resource data");
    myLoadingPanel.startLoading();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new ParseTask("Updating string resource data").queue();
    }
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);

    JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setName("toolbar");

    group.add(new AddKeyAction(this));
    group.add(myRemoveKeysAction);
    group.add(new AddLocaleAction(this));
    group.add(new FilterKeysAction(myTable));
    group.add(new FilterLocalesAction(myTable));
    group.add(new BrowserHelpAction("Translations editor", "https://developer.android.com/r/studio-ui/translations-editor.html"));

    return toolbar;
  }

  private void initTable() {
    ListSelectionListener listener = new CellSelectionListener();

    myTable.getColumnModel().getSelectionModel().addListSelectionListener(listener);
    myTable.getSelectionModel().addListSelectionListener(listener);

    myTable.getTableHeader().addMouseListener(new RemoveLocaleMouseListener(this));
  }

  @NotNull
  public JPanel getComponent() {
    return myLoadingPanel;
  }

  @NotNull
  public JBTable getPreferredFocusedComponent() {
    return myTable;
  }

  StringResourceTable getTable() {
    return myTable;
  }

  @NotNull
  StringResourceRepository getRepository() {
    return myResourceRepository;
  }

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    StringBuilder sb = new StringBuilder("https://translate.google.com/manager/android_studio/");

    // Application Version
    sb.append("?asVer=");
    ApplicationInfo ideInfo = ApplicationInfo.getInstance();

    // @formatter:off
    sb.append(ideInfo.getMajorVersion()).append('.')
      .append(ideInfo.getMinorVersion()).append('.')
      .append(ideInfo.getMicroVersion()).append('.')
      .append(ideInfo.getPatchVersion());
    // @formatter:on

    // Package name
    MergedManifest manifest = MergedManifest.get(myFacet);
    String pkg = manifest.getPackage();
    if (pkg != null) {
      sb.append("&pkgName=");
      sb.append(pkg.replace('.', '_'));
    }

    // Application ID
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(myFacet);
    String appId = moduleInfo.getPackage();
    if (appId != null) {
      sb.append("&appId=");
      sb.append(appId.replace('.', '_'));
    }

    // Version code
    Integer versionCode = manifest.getVersionCode();
    if (versionCode != null) {
      sb.append("&apkVer=");
      sb.append(versionCode.toString());
    }

    // If we support additional IDE languages, we can send the language used in the IDE here
    //sb.append("&lang=en");

    BrowserUtil.browse(sb.toString());
  }

  private class ParseTask extends Task.Backgroundable {
    public ParseTask(String description) {
      super(myFacet.getModule().getProject(), description, false);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      ModuleResourceRepository.getOrCreateInstance(myFacet);
    }

    @Override
    public void onSuccess() {
      myResourceRepository = new StringResourceRepository((MultiResourceRepository)ModuleResourceRepository.getOrCreateInstance(myFacet));
      myTable.setModel(new StringResourceTableModel(myResourceRepository.getData(myFacet)));

      myLoadingPanel.stopLoading();
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

      int row = myTable.getSelectedRowModelIndex();
      int column = myTable.getSelectedColumnModelIndex();
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
   * A Value can be edited inline if it contains no "\n" character.
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

      int row = myTable.getSelectedRowModelIndex();
      int column = myTable.getSelectedColumnModelIndex();

      StringResourceTableModel model = myTable.getModel();
      String value = (String)model.getValueAt(row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

      Locale locale = model.getLocale(column);
      String translation = locale == null ? null : (String)model.getValueAt(row, column);

      MultilineStringEditorDialog d = new MultilineStringEditorDialog(myFacet, model.getKey(row).getName(), value, locale, translation);
      if (d.showAndGet()) {
        if (!StringUtil.equals(value, d.getDefaultValue())) {
          model.setValueAt(d.getDefaultValue(), row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
          setTextAndEditable(myDefaultValueTextField.getTextField(), d.getDefaultValue(), isValueEditableInline(d.getDefaultValue()));
          myTable.refilter();
        }

        if (locale != null && !StringUtil.equals(translation, d.getTranslation())) {
          model.setValueAt(d.getTranslation(), row, column);
          setTextAndEditable(myTranslationTextField.getTextField(), d.getTranslation(), isValueEditableInline(d.getTranslation()));
          myTable.refilter();
        }
      }
    }
  }
}
