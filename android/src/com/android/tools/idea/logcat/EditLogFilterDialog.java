// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.logcat;

import static com.android.ddmlib.Log.LogLevel.INFO;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.logcat.ExpressionFilterManager.ExpressionException;
import com.android.tools.idea.logcat.PersistentAndroidLogFilters.FilterData;
import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.IconUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A dialog which is shown to the user when they request to modify or add a new log filter.
 */
final class EditLogFilterDialog extends DialogWrapper {
  static final int FILTER_HISTORY_SIZE = 5;
  private static final String NEW_FILTER_NAME_PREFIX = "Unnamed-";
  @NonNls private static final String EDIT_FILTER_DIALOG_DIMENSIONS_KEY =
    "edit.logcat.filter.dialog.dimensions";
  @NonNls private static final String LOG_FILTER_MESSAGE_HISTORY = "LOG_FILTER_MESSAGE_HISTORY";
  @NonNls private static final String LOG_FILTER_TAG_HISTORY = "LOG_FILTER_TAG_HISTORY";
  @NonNls private static final String LOG_FILTER_PACKAGE_NAME_HISTORY = "LOG_FILTER_PACKAGE_NAME_HISTORY";

  private final Project myProject;
  private final List<FilterData> myFilters;
  private final AndroidLogcatView myView;
  private final Splitter mySplitter;

  private JPanel myContentPanel;
  private JPanel myLeftPanel;
  private EditorTextField myFilterNameField;
  private RegexFilterComponent myLogMessageField;
  private RegexFilterComponent myTagField;
  private TextFieldWithAutoCompletion<String> myPidField;
  private RegexFilterComponent myPackageNameField;
  private JComboBox<LogLevel> myLogLevelCombo;
  private JPanel myLogMessageFieldWrapper;
  private JPanel myTagFieldWrapper;
  private JPanel myPidFieldWrapper;
  private JPanel myPackageNameFieldWrapper;
  private JBLabel myNameFieldLabel;
  private JBLabel myLogTagLabel;
  private JBLabel myLogMessageLabel;
  private JBLabel myPidLabel;
  private JBLabel myPackageNameLabel;
  private JBList<String> myFiltersList;
  private CollectionListModel<String> myFiltersListModel;
  @Nullable private FilterData myActiveFilter;
  private JPanel myFiltersToolbarPanel;
  private JLabel myExpressionLabel;
  private JPanel myExpressionWrapper;
  private JLabel myExpressionHelpIcon;
  private EditorTextField myExpressionField;
  private boolean myExistingMessagesParsed = false;
  private final ExpressionFilterManager myExpressionFilterManager = new ExpressionFilterManager();

  private List<String> myUsedPids;

  EditLogFilterDialog(@NotNull AndroidLogcatView view, @Nullable String selectedFilter) {
    super(view.getProject(), false);

    myProject = view.getProject();
    myFilters = PersistentAndroidLogFilters.getInstance(myProject).getFilters();
    myView = view;

    mySplitter = new Splitter(false, 0.25f);
    mySplitter.setFirstComponent(myLeftPanel);
    mySplitter.setSecondComponent(myContentPanel);

    if (selectedFilter != null) {
      for (FilterData filter : myFilters) {
        if (selectedFilter.equals(filter.getName())) {
          myActiveFilter = filter;
        }
      }
    }

    if (myActiveFilter == null) {
      myActiveFilter = myFilters.isEmpty() ? createNewFilter() : myFilters.get(0);
    }

    createEditorFields();
    initFiltersToolbar();
    initFiltersList();
    updateFilters();

    init();
  }

  @NotNull
  @Override
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

    if (myExpressionFilterManager.isSupported()) {
      Collection<String> expressionKeywords = myExpressionFilterManager.getBindingKeys();
      myExpressionField = new TextFieldWithAutoCompletion<>(myProject, new StringsCompletionProvider(expressionKeywords, null), true, "");
      myExpressionWrapper.add(myExpressionField);
      myExpressionLabel.setLabelFor(myExpressionField);
      myExpressionHelpIcon.setIcon(StudioIcons.Common.HELP);
      String expressionDoc = AndroidBundle.message(
        "android.logcat.expression.filter.doc",
        myExpressionFilterManager.getLanguageName(),
        expressionKeywords.stream().sorted().collect(Collectors.joining(", ")));
      new HelpTooltip().setDescription(expressionDoc).installOn(myExpressionHelpIcon);
    } else {
      myExpressionLabel.setVisible(false);
      myExpressionWrapper.setVisible(false);
      myExpressionHelpIcon.setVisible(false);
      // Create a placeholder editor so we don't have to null check elsewhere.
      myExpressionField = new EditorTextField();
    }

    myPidField = new TextFieldWithAutoCompletion<>(myProject, new StringsCompletionProvider(null, null) {
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
        return Integer.compare(pid1, pid2);
      }
    }, true, null);
    myPidFieldWrapper.add(myPidField);
    myPidLabel.setLabelFor(myPidField);

    myPackageNameField = new RegexFilterComponent(LOG_FILTER_PACKAGE_NAME_HISTORY, FILTER_HISTORY_SIZE);
    myPackageNameFieldWrapper.add(myPackageNameField);
    myPackageNameLabel.setLabelFor(myPackageNameField);

    myLogLevelCombo.setModel(new EnumComboBoxModel<>(LogLevel.class));
    myLogLevelCombo.setRenderer(SimpleListCellRenderer.create(
      "", value -> StringUtil.capitalize(StringUtil.toLowerCase(value.getStringValue()))));
    myLogLevelCombo.addActionListener(e -> {
      if (myActiveFilter == null) {
        return;
      }

      LogLevel selectedItem = (LogLevel)myLogLevelCombo.getSelectedItem();
      assert selectedItem != null;

      myActiveFilter.setLogLevel(selectedItem.getStringValue());
    });


    final Key<JComponent> componentKey = new Key<>("myComponent");
    myFilterNameField.getDocument().putUserData(componentKey, myFilterNameField);
    myExpressionField.getDocument().putUserData(componentKey, myExpressionField);
    myPidField.getDocument().putUserData(componentKey, myPidField);

    DocumentListener l = new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        if (myActiveFilter == null) {
          return;
        }

        String text = e.getDocument().getText().trim();
        JComponent src = e.getDocument().getUserData(componentKey);

        if (src == myFilterNameField) {
          int index = myFiltersList.getSelectedIndex();
          if (index != -1) {
            myFiltersListModel.setElementAt(text, index);
          }
          myActiveFilter.setName(text);
        }
        else if (src == myExpressionField) {
          myActiveFilter.setExpression(text);
        }
        else if (src == myPidField) {
          myActiveFilter.setPid(text);
        }
      }
    };

    myFilterNameField.getDocument().addDocumentListener(l);
    myExpressionField.getDocument().addDocumentListener(l);
    myPidField.getDocument().addDocumentListener(l);

    RegexFilterComponent.Listener rl = filter -> {
      if (myActiveFilter == null) {
        return;
      }

      if (filter == myTagField) {
        myActiveFilter.setLogTagPattern(filter.getFilter());
        myActiveFilter.setLogTagIsRegex(filter.isRegex());
      }
      else if (filter == myLogMessageField) {
        myActiveFilter.setLogMessagePattern(filter.getFilter());
        myActiveFilter.setLogMessageIsRegex(filter.isRegex());
      }
      else if (filter == myPackageNameField) {
        myActiveFilter.setPackageNamePattern(filter.getFilter());
        myActiveFilter.setPackageNameIsRegex(filter.isRegex());
      }
    };
    myTagField.addRegexListener(rl);
    myLogMessageField.addRegexListener(rl);
    myPackageNameField.addRegexListener(rl);
  }

  private void initFiltersList() {
    myFiltersList.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) {
        return;
      }

      int i = myFiltersList.getSelectedIndex();
      myActiveFilter = (i == -1) ? null : myFilters.get(i);
      resetFieldEditors();
    });

    myFiltersListModel = new CollectionListModel<>();
    for (FilterData filter : myFilters) {
      myFiltersListModel.add(filter.getName());
    }
    myFiltersList.setModel(myFiltersListModel);
    myFiltersList.setEmptyText(AndroidBundle.message("android.logcat.edit.filter.dialog.no.filters"));
  }

  private void initFiltersToolbar() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyAddFilterAction());
    group.add(new MyRemoveFilterAction());
    final JComponent component =
      ActionManager.getInstance().createActionToolbar("AndroidEditLogFilter", group, true).getComponent();
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

    final Set<String> pidSet = new HashSet<>();

    final String[] lines = StringUtil.splitByLines(document.toString());
    for (String line : lines) {
      pidSet.add(Integer.toString(LogcatJson.fromJson(line).getHeader().getPid()));
    }

    myUsedPids = Lists.newArrayList(pidSet);
  }

  private void resetFieldEditors() {
    boolean enabled = myActiveFilter != null;
    myFilterNameField.setEnabled(enabled);
    myExpressionField.setEnabled(enabled);
    myTagField.setEnabled(enabled);
    myLogMessageField.setEnabled(enabled);
    myPidField.setEnabled(enabled);
    myPackageNameField.setEnabled(enabled);
    myLogLevelCombo.setEnabled(enabled);

    String name = enabled ? myActiveFilter.getName() : "";
    String expression = enabled ? myActiveFilter.getExpression() : "";
    String tag = enabled ? myActiveFilter.getLogTagPattern() : "";
    String msg = enabled ? myActiveFilter.getLogMessagePattern() : "";
    String pid = enabled ? myActiveFilter.getPid() : "";
    String pkg = enabled ? myActiveFilter.getPackageNamePattern() : "";
    LogLevel logLevel = enabled ? LogLevel.getByString(myActiveFilter.getLogLevel())
                                : LogLevel.VERBOSE;

    myFilterNameField.setText(name != null ? name : "");
    myFilterNameField.selectAll();
    myExpressionField.setText(expression != null ? expression : "");
    myTagField.setFilter(tag != null ? tag : "");
    myTagField.setIsRegex(myActiveFilter == null || myActiveFilter.getLogTagIsRegex());
    myLogMessageField.setFilter(msg != null ? msg : "");
    myLogMessageField.setIsRegex(myActiveFilter == null || myActiveFilter.getLogMessageIsRegex());
    myPidField.setText(pid != null ? pid : "");
    myPackageNameField.setFilter(pkg != null ? pkg : "");
    myPackageNameField.setIsRegex(myActiveFilter == null || myActiveFilter.getPackageNameIsRegex());
    myLogLevelCombo.setSelectedItem(logLevel != null ? logLevel : LogLevel.VERBOSE);
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
    PersistentAndroidLogFilters.getInstance(myProject).setFilters(myFilters);

    super.doOKAction();
  }

  @Override
  protected ValidationInfo doValidate() {
    if (!myFilterNameField.isEnabled()) {
      return null;
    }

    final String name = myFilterNameField.getText().trim();

    if (name.isEmpty()) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.not.specified.error"), myFilterNameField);
    }

    if (name.equals(AndroidLogcatView.getNoFilters())
        || name.equals(AndroidLogcatView.getSelectedAppFilter())
        || name.equals(AndroidLogcatView.getEditFilterConfiguration())) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.busy.error", name), myFilterNameField);
    }

    for (FilterData filter : myFilters) {
      if (filter != myActiveFilter && name.equals(filter.getName())) {
        return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.name.busy.error", name), myFilterNameField);
      }
    }

    String expression = myExpressionField.getText();
    if (!expression.isEmpty()) {
      try {
        @SuppressWarnings("unused")
        boolean value = myExpressionFilterManager.eval(
          expression, new LogCatMessage(new LogCatHeader(
            /* logLevel= */ INFO,
            /* pid= */ 0,
            /* tid= */ 0,
            /* appName= */ "",
            /* tag= */ "",
            /* timestamp= */ Instant.EPOCH),
            /* message= */ ""
          ));
      }
      catch (ExpressionException e) {
        return new ValidationInfo(e.getMessage(), myExpressionField);
      }
    }
    if (myTagField.getParseError() != null) {
      return new ValidationInfo(
        AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.log.tag.pattern.error", myTagField.getParseError()),
        myTagField);
    }

    if (myLogMessageField.getParseError() != null) {
      return new ValidationInfo(
        AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.message.pattern.error", myLogMessageField.getParseError()),
        myLogMessageField);
    }

    if (myPackageNameField.getParseError() != null) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.application.name.pattern.error",
                                                      myPackageNameField.getParseError()),
                                myPackageNameField);
    }

    boolean validPid = false;
    try {
      final String pidStr = myPidField.getText().trim();
      final Integer pid = !pidStr.isEmpty() ? Integer.parseInt(pidStr) : null;

      if (pid == null || pid >= 0) {
        validPid = true;
      }
    }
    catch (NumberFormatException ignored) {
    }
    if (!validPid) {
      return new ValidationInfo(AndroidBundle.message("android.logcat.new.filter.dialog.incorrect.pid.error"), myPidField);
    }

    return null;
  }

  @Nullable
  public FilterData getActiveFilter() {
    return myActiveFilter;
  }

  private void updateFilters() {
    myFiltersList.setEnabled(!myFilters.isEmpty());
    if (myActiveFilter != null) {
      myFiltersList.setSelectedValue(myActiveFilter.getName(), true);
    }
    else if (!myFilters.isEmpty()) {
      myFiltersList.setSelectedIndex(0);
    }

    resetFieldEditors();
  }

  private FilterData createNewFilter() {
    FilterData filter = new FilterData();
    myFilters.add(filter);
    filter.setName(getUniqueName());
    return filter;
  }

  private String getUniqueName() {
    Set<String> names = new HashSet<>(myFilters.size());
    for (FilterData filter : myFilters) {
      names.add(filter.getName());
    }

    for (int i = 0; ; i++) {
      String n = NEW_FILTER_NAME_PREFIX + i;
      if (!names.contains(n)) {
        return n;
      }
    }
  }

  private String getSelectedFilterName() {
    return myFiltersList.getSelectedValue();
  }

  private final class MyAddFilterAction extends AnAction {
    private MyAddFilterAction() {
      super(CommonBundle.message("button.add"),
            AndroidBundle.message("android.logcat.add.logcat.filter.button"),
            IconUtil.getAddIcon());

      registerCustomShortcutSet(CommonShortcuts.INSERT, myFiltersList);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myActiveFilter = createNewFilter();
      myFiltersListModel.add(myActiveFilter.getName());
      updateFilters();

      IdeFocusManager manager = IdeFocusManager.getGlobalInstance();
      manager.doWhenFocusSettlesDown(() -> manager.requestFocus(myFilterNameField, true));
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
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getSelectedFilterName() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int i = myFiltersList.getSelectedIndex();

      // remove the filter from the model and from the displayed list
      myFilters.remove(i);
      myFiltersListModel.remove(i);

      // select another filter
      myActiveFilter = null;
      if (!myFilters.isEmpty()) {
        if (i >= myFilters.size()) {
          i = myFilters.size() - 1;
        }
        myActiveFilter = myFilters.get(i);
      }

      updateFilters();
    }
  }
}
