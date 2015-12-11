/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.logcat;

import com.android.ddmlib.Log;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.AndroidConfiguredLogFilters.FilterEntry;
import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A dialog which is shown to the user when they request to modify or add a new log filter.
 */
final class EditLogFilterDialog extends DialogWrapper {
  private static final String NEW_FILTER_NAME_PREFIX = "Unnamed-";
  @NonNls private static final String EDIT_FILTER_DIALOG_DIMENSIONS_KEY =
    "edit.logcat.filter.dialog.dimensions";
  static final int FILTER_HISTORY_SIZE = 5;
  @NonNls private static final String LOG_FILTER_MESSAGE_HISTORY = "LOG_FILTER_MESSAGE_HISTORY";
  @NonNls private static final String LOG_FILTER_TAG_HISTORY = "LOG_FILTER_TAG_HISTORY";
  @NonNls private static final String LOG_FILTER_PACKAGE_NAME_HISTORY = "LOG_FILTER_PACKAGE_NAME_HISTORY";

  private final Splitter mySplitter;
  private JPanel myContentPanel;
  private JPanel myLeftPanel;

  private EditorTextField myFilterNameField;
  private RegexFilterComponent myLogMessageField;
  private RegexFilterComponent myTagField;
  private TextFieldWithAutoCompletion myPidField;
  private RegexFilterComponent myPackageNameField;
  private JComboBox myLogLevelCombo;

  private JPanel myLogMessageFieldWrapper;
  private JPanel myTagFieldWrapper;
  private JPanel myPidFieldWrapper;
  private JPanel myPackageNameFieldWrapper;

  private JBLabel myNameFieldLabel;
  private JBLabel myLogTagLabel;
  private JBLabel myLogMessageLabel;
  private JBLabel myPidLabel;
  private JBLabel myPackageNameLabel;

  private JBList myFiltersList;
  private CollectionListModel<String> myFiltersListModel;

  @Nullable private FilterEntry mySelectedEntry;
  private final List<FilterEntry> myFilterEntries;

  private JPanel myFiltersToolbarPanel;

  private final AndroidLogcatView myView;
  private final Project myProject;

  private boolean myExistingMessagesParsed = false;

  private List<String> myUsedPids;

  EditLogFilterDialog(@NotNull final AndroidLogcatView view, @Nullable String selectedFilter) {
    super(view.getProject(), false);

    myView = view;
    myProject = view.getProject();

    mySplitter = new Splitter(false, 0.25f);
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myContentPanel);

    myFilterEntries = AndroidConfiguredLogFilters.getInstance(myProject).getFilterEntries();

    if (selectedFilter != null) {
      for (FilterEntry fe: myFilterEntries) {
        if (selectedFilter.equals(fe.getName())) {
          mySelectedEntry = fe;
        }
      }
    }

    if (mySelectedEntry == null) {
      mySelectedEntry = myFilterEntries.isEmpty() ? createNewFilterEntry() : myFilterEntries.get(0);
    }

    createEditorFields();
    initFiltersToolbar();
    initFiltersList();
    updateConfiguredFilters();

    init();
  }

  @Override
  @Nullable
  @NonNls
  protected String getDimensionServiceKey() {
    return EDIT_FILTER_DIALOG_DIMENSIONS_KEY;
  }

  private void createEditorFields() {
    myNameFieldLabel.setLabelFor(myFilterNameField);

    myLogMessageField = new RegexFilterComponent(LOG_FILTER_MESSAGE_HISTORY, FILTER_HISTORY_SIZE);
    myLogMessageFieldWrapper.add(myLogMessageField);
    myLogMessageLabel.setLabelFor(myLogMessageField);

    myTagField = new RegexFilterComponent(LOG_FILTER_TAG_HISTORY, FILTER_HISTORY_SIZE);
    myTagFieldWrapper.add(myTagField);
    myLogTagLabel.setLabelFor(myTagField);

    myPidField = new TextFieldWithAutoCompletion<String>(myProject, new TextFieldWithAutoCompletion.StringsCompletionProvider(null, null) {
      @NotNull
      @Override
      public Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
        parseExistingMessagesIfNecessary();
        setItems(myUsedPids);
        return super.getItems(prefix, cached, parameters);
      }

      @Override
      public int compare(String item1, String item2) {
        final int pid1 = Integer.parseInt(item1);
        final int pid2 = Integer.parseInt(item2);
        return Comparing.compare(pid1, pid2);
      }
    }, true, null);
    myPidFieldWrapper.add(myPidField);
    myPidLabel.setLabelFor(myPidField);

    myPackageNameField = new RegexFilterComponent(LOG_FILTER_PACKAGE_NAME_HISTORY, FILTER_HISTORY_SIZE);
    myPackageNameFieldWrapper.add(myPackageNameField);
    myPackageNameLabel.setLabelFor(myPackageNameField);

    myLogLevelCombo.setModel(new EnumComboBoxModel<Log.LogLevel>(Log.LogLevel.class));
    myLogLevelCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(StringUtil.capitalize(((Log.LogLevel)value).getStringValue().toLowerCase()));
        }
      }
    });
    myLogLevelCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySelectedEntry == null) {
          return;
        }

        Log.LogLevel selectedItem = (Log.LogLevel)myLogLevelCombo.getSelectedItem();
        mySelectedEntry.setLogLevel(selectedItem.getStringValue());
      }
    });


    final Key<JComponent> componentKey = new Key<JComponent>("myComponent");
    myFilterNameField.getDocument().putUserData(componentKey, myFilterNameField);
    myPidField.getDocument().putUserData(componentKey, myPidField);

    DocumentListener l = new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        if (mySelectedEntry == null) {
          return;
        }

        String text = e.getDocument().getText().trim();
        JComponent src = e.getDocument().getUserData(componentKey);

        if (src == myPidField) {
          mySelectedEntry.setPid(text);
        } else if (src == myFilterNameField) {
          int index = myFiltersList.getSelectedIndex();
          if (index != -1) {
            myFiltersListModel.setElementAt(text, index);
          }
          mySelectedEntry.setName(text);
        }
      }
    };

    myFilterNameField.getDocument().addDocumentListener(l);
    myPidField.getDocument().addDocumentListener(l);

    RegexFilterComponent.Listener rl = new RegexFilterComponent.Listener() {
      @Override
      public void filterChanged(RegexFilterComponent filter) {
        if (mySelectedEntry == null) {
          return;
        }

        if (filter == myTagField) {
          mySelectedEntry.setLogTagPattern(filter.getFilter());
          mySelectedEntry.setLogTagIsRegex(filter.isRegex());
        } else if (filter == myLogMessageField) {
          mySelectedEntry.setLogMessagePattern(filter.getFilter());
          mySelectedEntry.setLogMessageIsRegex(filter.isRegex());
        } else if (filter == myPackageNameField) {
          mySelectedEntry.setPackageNamePattern(filter.getFilter());
          mySelectedEntry.setPackageNameIsRegex(filter.isRegex());
        }
      }
    };
    myTagField.addRegexListener(rl);
    myLogMessageField.addRegexListener(rl);
    myPackageNameField.addRegexListener(rl);
  }

  private void initFiltersList() {
    myFiltersList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }

        int i = myFiltersList.getSelectedIndex();
        mySelectedEntry = (i == -1) ? null : myFilterEntries.get(i);
        resetFieldEditors();
      }
    });

    myFiltersListModel = new CollectionListModel<String>();
    for (FilterEntry entry : myFilterEntries) {
      myFiltersListModel.add(entry.getName());
    }
    myFiltersList.setModel(myFiltersListModel);
    myFiltersList.setEmptyText(AndroidBundle.message("android.logcat.edit.filter.dialog.no.filters"));
  }

  private void initFiltersToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyAddFilterAction());
    group.add(new MyRemoveFilterAction());
    final JComponent component =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    myFiltersToolbarPanel.add(component, BorderLayout.CENTER);
  }

  private void parseExistingMessagesIfNecessary() {
    if (myExistingMessagesParsed) {
      return;
    }
    myExistingMessagesParsed = true;

    final StringBuffer document = myView.getLogConsole().getOriginalDocument();
    if (document == null) {
      return;
    }

    final Set<String> pidSet = new HashSet<String>();

    final String[] lines = StringUtil.splitByLines(document.toString());
    for (String line : lines) {
      LogCatMessage message = AndroidLogcatFormatter.parseMessage(line);
      pidSet.add(Integer.toString(message.getPid()));
    }

    myUsedPids = Lists.newArrayList(pidSet);
  }

  private void resetFieldEditors() {
    boolean enabled = mySelectedEntry != null;
    myFilterNameField.setEnabled(enabled);
    myTagField.setEnabled(enabled);
    myLogMessageField.setEnabled(enabled);
    myPidField.setEnabled(enabled);
    myPackageNameField.setEnabled(enabled);
    myLogLevelCombo.setEnabled(enabled);

    String name = enabled ? mySelectedEntry.getName() : "";
    String tag = enabled ? mySelectedEntry.getLogTagPattern() : "";
    String msg = enabled ? mySelectedEntry.getLogMessagePattern() : "";
    String pid = enabled ? mySelectedEntry.getPid() : "";
    String pkg = enabled ? mySelectedEntry.getPackageNamePattern() : "";
    Log.LogLevel logLevel = enabled ? Log.LogLevel.getByString(mySelectedEntry.getLogLevel())
                               : Log.LogLevel.VERBOSE;

    myFilterNameField.setText(name != null ? name : "");
    myFilterNameField.selectAll();
    myTagField.setFilter(tag != null ? tag : "");
    myTagField.setIsRegex(mySelectedEntry == null || mySelectedEntry.getLogTagIsRegex());
    myLogMessageField.setFilter(msg != null ? msg : "");
    myLogMessageField.setIsRegex(mySelectedEntry == null || mySelectedEntry.getLogMessageIsRegex());
    myPidField.setText(pid != null ? pid : "");
    myPackageNameField.setFilter(pkg != null ? pkg : "");
    myPackageNameField.setIsRegex(mySelectedEntry == null || mySelectedEntry.getPackageNameIsRegex());
    myLogLevelCombo.setSelectedItem(logLevel != null ? logLevel : Log.LogLevel.VERBOSE);
  }

  @Override
  protected JComponent createCenterPanel() {
    return mySplitter;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFilterNameField.getText().startsWith(NEW_FILTER_NAME_PREFIX) ? myFilterNameField : myTagField;
  }

  @Override
  protected void doOKAction() {
    AndroidConfiguredLogFilters.getInstance(myProject).setFilterEntries(myFilterEntries);

    super.doOKAction();
  }

  @Override
  protected ValidationInfo doValidate() {
    if (!myFilterNameField.isEnabled()) {
      return null;
    }

    final String name = myFilterNameField.getText().trim();

    if (name.isEmpty()) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.not.specified.error"),
                                myFilterNameField);
    }

    if (name.equals(AndroidLogcatView.NO_FILTERS)
        || name.equals(AndroidLogcatView.SELECTED_APP_FILTER)
        || name.equals(AndroidLogcatView.EDIT_FILTER_CONFIGURATION)) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.busy.error", name));
    }

    for (FilterEntry entry : myFilterEntries) {
      if (entry != mySelectedEntry && name.equals(entry.getName())) {
        return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.busy.error", name));
      }
    }

    if (myTagField.getParseError() != null) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.log.tag.pattern.error") + '\n' + myTagField.getParseError());
    }

    if (myLogMessageField.getParseError() != null) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.message.pattern.error") + '\n' + myLogMessageField.getParseError());
    }

    if (myPackageNameField.getParseError() != null) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.application.name.pattern.error") + '\n' + myPackageNameField.getParseError());
    }

    boolean validPid = false;
    try {
      final String pidStr = myPidField.getText().trim();
      final Integer pid = pidStr.length() > 0 ? Integer.parseInt(pidStr) : null;

      if (pid == null || pid.intValue() >= 0) {
        validPid = true;
      }
    }
    catch (NumberFormatException ignored) {
    }
    if (!validPid) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.pid.error"));
    }

    return null;
  }

  @Nullable
  public FilterEntry getCustomLogFiltersEntry() {
    return mySelectedEntry;
  }

  private void updateConfiguredFilters() {
    myFiltersList.setEnabled(myFilterEntries.size() > 0);
    if (mySelectedEntry != null) {
      myFiltersList.setSelectedValue(mySelectedEntry.getName(), true);
    } else if (!myFilterEntries.isEmpty()) {
      myFiltersList.setSelectedIndex(0);
    }

    resetFieldEditors();
  }

  private FilterEntry createNewFilterEntry() {
    FilterEntry entry = new FilterEntry();
    myFilterEntries.add(entry);
    entry.setName(getUniqueName());
    return entry;
  }

  private String getUniqueName() {
    Set<String> names = new HashSet<String>(myFilterEntries.size());
    for (FilterEntry fe : myFilterEntries) {
      names.add(fe.getName());
    }

    for (int i = 0; ; i++){
      String n = NEW_FILTER_NAME_PREFIX + i;
      if (!names.contains(n)) {
        return n;
      }
    }
  }

  private final class MyAddFilterAction extends AnAction {
    private MyAddFilterAction() {
      super(CommonBundle.message("button.add"),
            AndroidBundle.message("android.logcat.add.logcat.filter.button"),
            IconUtil.getAddIcon());

      registerCustomShortcutSet(CommonShortcuts.INSERT, myFiltersList);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySelectedEntry = createNewFilterEntry();
      myFiltersListModel.add(mySelectedEntry.getName());
      updateConfiguredFilters();

      myFilterNameField.requestFocus();
    }
  }

  private final class MyRemoveFilterAction extends AnAction {
    private MyRemoveFilterAction() {
      super(CommonBundle.message("button.delete"),
            AndroidBundle.message("android.logcat.remove.logcat.filter.button"),
            IconUtil.getRemoveIcon());

      registerCustomShortcutSet(CommonShortcuts.getDelete(), myFiltersList);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(getSelectedFilterName() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int i = myFiltersList.getSelectedIndex();

      // remove the entry from the model and from the displayed list
      myFilterEntries.remove(i);
      myFiltersListModel.remove(i);

      // select another entry
      mySelectedEntry = null;
      if (myFilterEntries.size() > 0) {
        if (i >= myFilterEntries.size()) {
          i = myFilterEntries.size() - 1;
        }
        mySelectedEntry = myFilterEntries.get(i);
      }

      updateConfiguredFilters();
    }
  }

  private String getSelectedFilterName() {
    return (String)myFiltersList.getSelectedValue();
  }
}
