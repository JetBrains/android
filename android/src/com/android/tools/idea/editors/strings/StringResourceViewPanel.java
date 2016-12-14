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
import com.android.resources.ResourceType;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceNotificationManager.Reason;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

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
  private LocalResourceRepository myResourceRepository;
  private long myModificationCount;
  private ResourceChangeListener myResourceChangeListener;

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
    myKeyTextField.addFocusListener(new SetTableValueAtFocusListener(StringResourceTableModel.KEY_COLUMN));

    addResourceChangeListener();

    Disposer.register(parentDisposable, this);

    myLoadingPanel.setLoadingText("Loading string resource data");
    myLoadingPanel.startLoading();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      new ParseTask("Loading string resource data").queue();
    }
  }

  private void createUIComponents() {
    myTable = new StringResourceTable();

    createDefaultValueTextField();
    createTranslationTextField();
  }

  private void createDefaultValueTextField() {
    myDefaultValueTextField = new TextFieldWithBrowseButton(new TranslationsEditorTextField());

    myDefaultValueTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
    myDefaultValueTextField.addActionListener(new ShowMultilineActionListener());

    FocusListener listener = new SetTableValueAtFocusListener(StringResourceTableModel.DEFAULT_VALUE_COLUMN);
    myDefaultValueTextField.getTextField().addFocusListener(listener);
  }

  private void createTranslationTextField() {
    myTranslationTextField = new TextFieldWithBrowseButton(new TranslationsEditorTextField());

    myTranslationTextField.setButtonIcon(AllIcons.Actions.ShowViewer);
    myTranslationTextField.addActionListener(new ShowMultilineActionListener());
    myTranslationTextField.getTextField().addFocusListener(new SetTableValueAtFocusListener(myTable::getSelectedColumnModelIndex));
  }

  private static final class TranslationsEditorTextField extends JBTextField {
    private TranslationsEditorTextField() {
      super(10);
    }

    @Override
    public void paste() {
      super.paste();
      setFont(FontUtil.getFontAbleToDisplay(getText(), getFont()));
    }
  }

  private final class SetTableValueAtFocusListener extends FocusAdapter {
    private final IntSupplier myColumn;

    private SetTableValueAtFocusListener(int column) {
      myColumn = () -> column;
    }

    private SetTableValueAtFocusListener(@NotNull IntSupplier column) {
      myColumn = column;
    }

    @Override
    public void focusLost(@NotNull FocusEvent event) {
      if (myTable.getSelectedRowCount() != 1 || myTable.getSelectedColumnCount() != 1) {
        return;
      }

      JTextComponent component = (JTextComponent)event.getComponent();

      if (!component.isEditable()) {
        return;
      }

      myTable.getModel().setValueAt(component.getText(), myTable.getSelectedRowModelIndex(), myColumn.getAsInt());
      myTable.refilter();
    }
  }

  @Override
  public void dispose() {
    ResourceNotificationManager.getInstance(myFacet.getModule().getProject()).removeListener(myResourceChangeListener, myFacet, null, null);
  }

  public void reloadData() {
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

    final AnAction addKeyAction = new AnAction("Add Key", "", AllIcons.ToolbarDecorator.Add) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myTable.getData() != null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        StringResourceData data = myTable.getData();
        assert data != null;

        NewStringKeyDialog dialog = new NewStringKeyDialog(myFacet, ImmutableSet.copyOf(data.getKeys()));
        if (dialog.showAndGet()) {
          StringsWriteUtils.createItem(myFacet, dialog.getResFolder(), null, dialog.getKey(), dialog.getDefaultValue(), true);
        }
      }
    };

    group.add(addKeyAction);
    group.add(new RemoveKeysAction());
    group.add(new AddLocaleAction(toolbarComponent));
    group.add(new FilterKeysAction(myTable));
    group.add(new BrowserHelpAction("Translations editor", "https://developer.android.com/r/studio-ui/translations-editor.html"));

    return toolbar;
  }

  private final class RemoveKeysAction extends AnAction {
    private RemoveKeysAction() {
      super("Remove Keys", "", AllIcons.ToolbarDecorator.Remove);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getPresentation().setEnabled(myTable.getSelectedRowCount() != 0);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      Project project = event.getProject();
      assert project != null;

      PsiElement[] elements = Arrays.stream(myTable.getSelectedRowModelIndices())
        .mapToObj(index -> ((StringResourceTableModel)myTable.getModel()).getStringResourceAt(index).getKey())
        .flatMap(this::getResourceItemStream)
        .map(item -> LocalResourceRepository.getItemTag(project, item))
        .toArray(PsiElement[]::new);

      SafeDeleteHandler.invoke(project, elements, LangDataKeys.MODULE.getData(event.getDataContext()), true, null);
    }

    @NotNull
    private Stream<ResourceItem> getResourceItemStream(@NotNull String key) {
      Collection<ResourceItem> items = myResourceRepository.getResourceItem(ResourceType.STRING, key);
      return items == null ? Stream.empty() : items.stream();
    }
  }

  private final class AddLocaleAction extends AnAction {
    private final JComponent myComponent;

    private AddLocaleAction(@NotNull JComponent component) {
      super("Add Locale", "", AndroidIcons.Globe);
      myComponent = component;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      StringResourceData data = myTable.getData();
      event.getPresentation().setEnabled(data != null && !data.getResources().isEmpty());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      StringResourceData data = myTable.getData();
      assert data != null;

      List<Locale> missingLocales = LocaleMenuAction.getAllLocales();
      missingLocales.removeAll(data.getLocales());
      missingLocales.sort(Locale.LANGUAGE_NAME_COMPARATOR);

      final JBList list = new JBList(missingLocales);
      list.setFixedCellHeight(20);
      list.setCellRenderer(new ColoredListCellRenderer<Locale>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList list, Locale value, int index, boolean selected, boolean hasFocus) {
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

      showPopupUnderneathOf(list);
    }

    private void showPopupUnderneathOf(@NotNull JList list) {
      Runnable runnable = () -> {
        // TODO Ask the user to pick a source set instead of defaulting to the primary resource directory
        VirtualFile primaryResourceDir = myFacet.getPrimaryResourceDir();
        assert primaryResourceDir != null;

        StringResourceData data = myTable.getData();
        assert data != null;

        // Pick a value to add to this locale
        String key = "app_name";
        StringResource resource = data.containsKey(key) ? data.getStringResource(key) : data.getResources().iterator().next();
        String string = resource.getDefaultValueAsString();

        StringsWriteUtils.createItem(myFacet, primaryResourceDir, (Locale)list.getSelectedValue(), resource.getKey(), string, true);
      };

      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setItemChoosenCallback(runnable)
        .createPopup();

      popup.showUnderneathOf(myComponent);
    }
  }

  public boolean dataIsCurrent() {
    return myResourceRepository != null && myModificationCount >= myResourceRepository.getModificationCount();
  }

  private void initTable() {
    ListSelectionListener listener = new CellSelectionListener();

    myTable.getColumnModel().getSelectionModel().addListSelectionListener(listener);
    myTable.getSelectionModel().addListSelectionListener(listener);
  }

  private void addResourceChangeListener() {
    myResourceChangeListener = reasons -> {
      if (reasons.contains(Reason.RESOURCE_EDIT)) {
        reloadData();
      }
    };

    ResourceNotificationManager.getInstance(myFacet.getModule().getProject()).addListener(myResourceChangeListener, myFacet, null, null);
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
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(myFacet);
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
    private final AtomicReference<LocalResourceRepository> myResourceRepositoryRef = new AtomicReference<>(null);
    private final AtomicReference<StringResourceData> myResourceDataRef = new AtomicReference<>(null);

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
      parse(myResourceRepositoryRef.get(), myResourceDataRef.get());
    }

    @Override
    public void onCancel() {
      myLoadingPanel.stopLoading();
    }
  }

  @VisibleForTesting
  void parse(@NotNull LocalResourceRepository resourceRepository) {
    parse(resourceRepository, StringResourceParser.parse(myFacet, resourceRepository));
  }

  private void parse(@NotNull LocalResourceRepository resourceRepository, @NotNull StringResourceData data) {
    myResourceRepository = resourceRepository;
    myModificationCount = resourceRepository.getModificationCount();

    myTable.setModel(new StringResourceTableModel(data));
    myLoadingPanel.stopLoading();
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

      StringResourceTableModel model = (StringResourceTableModel)myTable.getModel();

      int row = myTable.getSelectedRowModelIndex();
      int column = myTable.getSelectedColumnModelIndex();
      Object locale = model.getLocale(column);

      setTextAndEditable(myKeyTextField, model.getKey(row), false); // TODO: keys are not editable, we want them to be refactor operations

      String defaultValue = (String)model.getValueAt(row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
      boolean defaultValueEditable = !StringUtil.containsChar(defaultValue, '\n'); // don't allow editing multiline chars in a text field
      setTextAndEditable(myDefaultValueTextField.getTextField(), defaultValue, defaultValueEditable);
      myDefaultValueTextField.getButton().setEnabled(true);

      boolean translationEditable = false;
      String translation = "";
      if (locale != null) {
        translation = (String)model.getValueAt(row, column);
        translationEditable = !StringUtil.containsChar(translation, '\n'); // don't allow editing multiline chars in a text field
      }
      setTextAndEditable(myTranslationTextField.getTextField(), translation, translationEditable);
      myTranslationTextField.getButton().setEnabled(locale != null);
    }

    private void setTextAndEditable(@NotNull JTextComponent component, @NotNull String text, boolean editable) {
      component.setText(text);
      component.setCaretPosition(0);
      component.setEditable(editable);
      // If a text component is not editable when it gains focus and becomes editable while still focused,
      // the caret does not appear, so we need to set the caret visibility manually
      component.getCaret().setVisible(editable && component.hasFocus());

      component.setFont(FontUtil.getFontAbleToDisplay(text, component.getFont()));
    }
  }

  private class ShowMultilineActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (myTable.getSelectedRowCount() != 1 || myTable.getSelectedColumnCount() != 1) {
        return;
      }

      int row = myTable.getSelectedRowModelIndex();
      int column = myTable.getSelectedColumnModelIndex();

      StringResourceTableModel model = (StringResourceTableModel)myTable.getModel();
      String value = (String)model.getValueAt(row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

      Locale locale = model.getLocale(column);
      String translation = locale == null ? null : (String)model.getValueAt(row, column);

      MultilineStringEditorDialog d = new MultilineStringEditorDialog(myFacet, model.getKey(row), value, locale, translation);
      if (d.showAndGet()) {
        if (!StringUtil.equals(value, d.getDefaultValue())) {
          model.setValueAt(d.getDefaultValue(), row, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
          myTable.refilter();
        }

        if (locale != null && !StringUtil.equals(translation, d.getTranslation())) {
          model.setValueAt(d.getTranslation(), row, column);
          myTable.refilter();
        }
      }
    }
  }
}
