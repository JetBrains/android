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

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatHeader;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogFormatter;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import static javax.swing.BoxLayout.X_AXIS;

/**
 * A UI panel which wraps a console that prints output from Android's logging system.
 */
public abstract class AndroidLogcatView implements Disposable {
  public static final Key<AndroidLogcatView> ANDROID_LOGCAT_VIEW_KEY = Key.create("ANDROID_LOGCAT_VIEW_KEY");

  static final String SELECTED_APP_FILTER = AndroidBundle.message("android.logcat.filters.selected");
  static final String NO_FILTERS = AndroidBundle.message("android.logcat.filters.none");
  static final String EDIT_FILTER_CONFIGURATION = AndroidBundle.message("android.logcat.filters.edit");

  private final Project myProject;
  private final DeviceContext myDeviceContext;

  private JPanel myPanel;
  private DefaultComboBoxModel myFilterComboBoxModel;

  private volatile IDevice myDevice;
  private final AndroidLogConsole myLogConsole;
  private final FormattedLogLineReceiver myFormattedLogLineReceiver;
  private final AndroidLogFilterModel myLogFilterModel;

  private final IDevice myPreselectedDevice;

  /**
   * A default filter which will always let everything through.
   */
  @NotNull
  private final AndroidLogcatFilter myNoFilter;

  /**
   * Called internally when the device may have changed, or been significantly altered.
   * @param forceReconnect Forces the logcat connection to restart even if the device has not changed.
   */
  private void notifyDeviceUpdated(final boolean forceReconnect) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }
        if (forceReconnect) {
          if (myDevice != null) {
            AndroidLogcatService.getInstance().removeListener(myDevice, myFormattedLogLineReceiver);
          }
          myDevice = null;
        }
        updateLogConsole();
      }
    });
  }

  @NotNull
  public final Project getProject() {
    return myProject;
  }

  @NotNull
  public final LogConsoleBase getLogConsole() {
    return myLogConsole;
  }

  public final void clearLogcat(@Nullable IDevice device) {
    if (device == null) {
      return;
    }

    myLogFilterModel.beginRejectingOldMessages();
    AndroidLogcatUtils.clearLogcat(myProject, device);

    // In theory, we only need to clear the console. However, due to issues in the platform, clearing logcat via "logcat -c" could
    // end up blocking the current logcat readers. As a result, we need to issue a restart of the logging to work around the platform bug.
    // See https://code.google.com/p/android/issues/detail?id=81164 and https://android-review.googlesource.com/#/c/119673
    // NOTE: We can avoid this and just clear the console if we ever decide to stop issuing a "logcat -c" to the device or if we are
    // confident that https://android-review.googlesource.com/#/c/119673 doesn't happen anymore.
    if (device.equals(getSelectedDevice())) {
      notifyDeviceUpdated(true);
    }
  }

  /**
   * Logcat view with device obtained from {@link DeviceContext}
   */
  public AndroidLogcatView(@NotNull final Project project, @NotNull DeviceContext deviceContext) {
    this(project, null, deviceContext);
  }

  private AndroidLogcatView(final Project project, @Nullable IDevice preselectedDevice, @Nullable DeviceContext deviceContext) {
    myDeviceContext = deviceContext;
    myProject = project;
    myPreselectedDevice = preselectedDevice;

    Disposer.register(myProject, this);

    myLogFilterModel =
      new AndroidLogFilterModel() {

        private AndroidLogcatPreferences getPreferences() {
          return AndroidLogcatPreferences.getInstance(project);
        }

        @Override
        protected void saveLogLevel(String logLevelName) {
          getPreferences().TOOL_WINDOW_LOG_LEVEL = logLevelName;
        }

        @Override
        public String getSelectedLogLevelName() {
          return getPreferences().TOOL_WINDOW_LOG_LEVEL;
        }

        @Override
        protected void saveConfiguredFilterName(String filterName) {
          getPreferences().TOOL_WINDOW_CONFIGURED_FILTER = filterName;
        }
      };

    AndroidLogcatFormatter logFormatter = new AndroidLogcatFormatter(AndroidLogcatPreferences.getInstance(project));
    myLogConsole = new AndroidLogConsole(project, myLogFilterModel, logFormatter);
    myFormattedLogLineReceiver = new FormattedLogLineReceiver() {
      @Override
      protected void receiveFormattedLogLine(@NotNull String line) {
        myLogConsole.addLogLine(line);
      }
    };

    if (preselectedDevice == null && deviceContext != null) {
      DeviceContext.DeviceSelectionListener deviceSelectionListener =
        new DeviceContext.DeviceSelectionListener() {
          @Override
          public void deviceSelected(@Nullable IDevice device) {
            notifyDeviceUpdated(false);
          }

          @Override
          public void deviceChanged(@NotNull IDevice device, int changeMask) {
            if (device == myDevice && ((changeMask & IDevice.CHANGE_STATE) == IDevice.CHANGE_STATE)) {
              notifyDeviceUpdated(true);
            }
          }

          @Override
          public void clientSelected(@Nullable final Client c) {
            AndroidLogcatFilter selected = (AndroidLogcatFilter)myFilterComboBoxModel.getSelectedItem();
            updateDefaultFilters(c != null ? c.getClientData() : null);

            // Attempt to preserve selection as best we can. Often we don't have to do anything,
            // but it's possible an old filter was replaced with an updated version - so, new
            // instance, but the same name.
            if (selected != null && myFilterComboBoxModel.getSelectedItem() != selected) {
              selectFilterByName(selected.getName());
            }
          }
        };
      deviceContext.addListener(deviceSelectionListener, this);
    }

    myNoFilter = new DefaultAndroidLogcatFilter.Builder(NO_FILTERS).build();

    JComponent consoleComponent = myLogConsole.getComponent();

    final ConsoleView console = myLogConsole.getConsole();
    if (console != null) {
      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                                                    myLogConsole.getOrCreateActions(), false);
      toolbar.setTargetComponent(console.getComponent());
      final JComponent tbComp1 = toolbar.getComponent();
      myPanel.add(tbComp1, BorderLayout.WEST);
    }

    myPanel.add(consoleComponent, BorderLayout.CENTER);
    Disposer.register(this, myLogConsole);

    updateLogConsole();
  }

  @NotNull
  public final JPanel createSearchComponent() {
    final JPanel panel = new JPanel();
    final ComboBox editFiltersCombo = new ComboBox();
    myFilterComboBoxModel = new DefaultComboBoxModel();
    myFilterComboBoxModel.addElement(myNoFilter);
    myFilterComboBoxModel.addElement(EDIT_FILTER_CONFIGURATION);

    updateDefaultFilters(null);
    updateUserFilters();
    String selectName = AndroidLogcatPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER;
    if (StringUtil.isEmpty(selectName)) {
      selectName = myDeviceContext != null ? SELECTED_APP_FILTER : NO_FILTERS;
    }
    selectFilterByName(selectName);

    editFiltersCombo.setModel(myFilterComboBoxModel);
    applySelectedFilter();
    // note: the listener is added after the initial call to populate the combo
    // boxes in the above call to updateConfiguredFilters
    editFiltersCombo.addItemListener(new ItemListener() {
      @Nullable private AndroidLogcatFilter myLastSelected;

      @Override
      public void itemStateChanged(ItemEvent e) {
        Object item = e.getItem();
        if (e.getStateChange() == ItemEvent.DESELECTED) {
          if (item instanceof AndroidLogcatFilter) {
            myLastSelected = (AndroidLogcatFilter)item;
          }
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {

          if (item instanceof AndroidLogcatFilter) {
            applySelectedFilter();
          }
          else {
            assert EDIT_FILTER_CONFIGURATION.equals(item);
            final EditLogFilterDialog dialog =
              new EditLogFilterDialog(AndroidLogcatView.this, myLastSelected == null ? null : myLastSelected.getName());
            dialog.setTitle(AndroidBundle.message("android.logcat.new.filter.dialog.title"));
            if (dialog.showAndGet()) {
              final PersistentAndroidLogFilters.FilterData filterData = dialog.getActiveFilter();
              updateUserFilters();
              if (filterData != null) {
                selectFilterByName(filterData.getName());
              }
            }
            else {
              editFiltersCombo.setSelectedItem(myLastSelected);
            }
          }
        }
      }
    });

    editFiltersCombo.setRenderer(new ColoredListCellRenderer<Object>() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof AndroidLogcatFilter) {
          setBorder(null);
          append(((AndroidLogcatFilter)value).getName());
        }
        else {
          setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
          append(value.toString());
        }
      }
    });
    panel.add(editFiltersCombo);

    final JPanel searchComponent = new JPanel();
    searchComponent.setLayout(new BoxLayout(searchComponent, X_AXIS));
    searchComponent.add(myLogConsole.getSearchComponent());
    searchComponent.add(panel);

    return searchComponent;
  }

  protected abstract boolean isActive();

  public final void activate() {
    if (isActive()) {
      updateLogConsole();
    }
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = getSelectedDevice();
    if (myDevice != device) {
      AndroidLogcatService androidLogcatService = AndroidLogcatService.getInstance();
      if (myDevice != null) {
        androidLogcatService.removeListener(myDevice, myFormattedLogLineReceiver);
      }
      ConsoleView console = myLogConsole.getConsole();
      if (console != null) {
        console.clear();
      }
      myDevice = device;
      androidLogcatService.addListener(myDevice, myFormattedLogLineReceiver, true);
    }
  }

  @Nullable
  public final IDevice getSelectedDevice() {
    if (myPreselectedDevice != null) {
      return myPreselectedDevice;
    }
    else if (myDeviceContext != null) {
      return myDeviceContext.getSelectedDevice();
    }
    else {
      return null;
    }
  }


  private void applySelectedFilter() {
    final Object filter = myFilterComboBoxModel.getSelectedItem();
    if (filter instanceof AndroidLogcatFilter) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, LogConsoleBase.APPLYING_FILTER_TITLE) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myLogFilterModel.updateLogcatFilter((AndroidLogcatFilter)filter);
        }
      });
    }
  }

  /**
   * Update the list of filters which are provided by default (selected app and filters provided
   * by plugins). These show up in the top half of the filter pulldown.
   */
  private void updateDefaultFilters(@Nullable ClientData client) {
    int noFilterIndex = myFilterComboBoxModel.getIndexOf(myNoFilter);
    for (int i = 0; i < noFilterIndex; i++) {
      myFilterComboBoxModel.removeElementAt(0);
    }

    int insertIndex = 0;

    DefaultAndroidLogcatFilter.Builder selectedAppFilterBuilder = new DefaultAndroidLogcatFilter.Builder(SELECTED_APP_FILTER);
    if (client != null) {
      selectedAppFilterBuilder.setPid(client.getPid());
    }
    // Even if "client" is null, create a dummy "Selected app" filter as a placeholder which will
    // be replaced when a client is eventually created.
    myFilterComboBoxModel.insertElementAt(selectedAppFilterBuilder.build(), insertIndex++);

    for (LogcatFilterProvider filterProvider : LogcatFilterProvider.EP_NAME.getExtensions()) {
      AndroidLogcatFilter filter = filterProvider.getFilter(client);
      myFilterComboBoxModel.insertElementAt(filter, insertIndex++);
    }
  }


  /**
   * Update the list of filters which have been created by the user. These show up in the bottom
   * half of the filter pulldown.
   */
  private void updateUserFilters() {

    assert myFilterComboBoxModel.getIndexOf(EDIT_FILTER_CONFIGURATION) >= 0;

    int userFiltersStartIndex = myFilterComboBoxModel.getIndexOf(EDIT_FILTER_CONFIGURATION) + 1;
    while (myFilterComboBoxModel.getSize() > userFiltersStartIndex) {
      myFilterComboBoxModel.removeElementAt(userFiltersStartIndex);
    }

    final List<PersistentAndroidLogFilters.FilterData> filters = PersistentAndroidLogFilters.getInstance(myProject).getFilters();
    for (PersistentAndroidLogFilters.FilterData filter : filters) {
      final String name = filter.getName();
      assert name != null; // The UI that creates filters should ensure a name was created

      AndroidLogcatFilter compiled = DefaultAndroidLogcatFilter.compile(filter, name);
      myFilterComboBoxModel.addElement(compiled);
    }
  }

  private void selectFilterByName(String name) {
    for (int i = 0; i < myFilterComboBoxModel.getSize(); i++) {
      Object element = myFilterComboBoxModel.getElementAt(i);
      if (element instanceof AndroidLogcatFilter) {
        if (((AndroidLogcatFilter)element).getName().equals(name)) {
          myFilterComboBoxModel.setSelectedItem(element);
          break;
        }
      }
    }
  }

  @NotNull
  public final JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public final void dispose() {
  }

  private final class MyRestartAction extends AnAction {
    public MyRestartAction() {
      super(AndroidBundle.message("android.restart.logcat.action.text"), AndroidBundle.message("android.restart.logcat.action.description"),
            AllIcons.Actions.Restart);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      notifyDeviceUpdated(true);
    }
  }

  private final class MyConfigureLogcatHeaderAction extends AnAction {
    public MyConfigureLogcatHeaderAction() {
      super(AndroidBundle.message("android.configure.logcat.header.text"),
            AndroidBundle.message("android.configure.logcat.header.description"), AllIcons.General.GearPlain);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ConfigureLogcatFormatDialog dialog = new ConfigureLogcatFormatDialog(myProject);
      if (dialog.showAndGet()) {
        myLogConsole.refresh();
      }
    }
  }

  final class AndroidLogConsole extends LogConsoleBase{
    private final RegexFilterComponent myRegexFilterComponent = new RegexFilterComponent("LOG_FILTER_HISTORY", 5);
    private final AndroidLogcatPreferences myPreferences;

    public AndroidLogConsole(Project project, AndroidLogFilterModel logFilterModel, LogFormatter logFormatter) {
      super(project, null, "", false, logFilterModel, GlobalSearchScope.allScope(project), logFormatter);
      ConsoleView console = getConsole();
      if (console instanceof ConsoleViewImpl) {
        ConsoleViewImpl c = ((ConsoleViewImpl)console);
        c.addCustomConsoleAction(new Separator());
        c.addCustomConsoleAction(new MyRestartAction());
        c.addCustomConsoleAction(new MyConfigureLogcatHeaderAction());
        c.addCustomConsoleAction(new Separator());
        c.addCustomConsoleAction(new BrowserHelpAction("logcat", "http://developer.android.com/r/studio-ui/am-logcat.html"));
      }
      myPreferences = AndroidLogcatPreferences.getInstance(project);
      myRegexFilterComponent.setFilter(myPreferences.TOOL_WINDOW_CUSTOM_FILTER);
      myRegexFilterComponent.setIsRegex(myPreferences.TOOL_WINDOW_REGEXP_FILTER);
      myRegexFilterComponent.addRegexListener(new RegexFilterComponent.Listener() {
        @Override
        public void filterChanged(RegexFilterComponent filter) {
          myPreferences.TOOL_WINDOW_CUSTOM_FILTER = filter.getFilter();
          myPreferences.TOOL_WINDOW_REGEXP_FILTER = filter.isRegex();
          myLogFilterModel.updateCustomPattern(filter.getPattern());
        }
      });
    }

    @Override
    public boolean isActive() {
      return AndroidLogcatView.this.isActive();
    }

    public void clearLogcat() {
      AndroidLogcatView.this.clearLogcat(getSelectedDevice());
    }

    @NotNull
    @Override
    protected Component getTextFilterComponent() {
      return myRegexFilterComponent;
    }

    public void addLogLine(@NotNull String line) {
      super.addMessage(line);
    }

    /**
     * Clear the current logs and replay all old messages. This is useful to do if the display
     * format of the logs have changed, for example.
     */
    public void refresh() {
      // Even if we haven't changed any filter, calling this method quickly refreshes the log as a
      // side effect.
      onTextFilterChange();
    }
  }
}
