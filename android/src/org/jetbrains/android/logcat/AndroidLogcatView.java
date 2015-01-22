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
package org.jetbrains.android.logcat;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DeviceContext;
import com.intellij.diagnostic.logging.LogConsoleBase;
import com.intellij.diagnostic.logging.LogConsoleListener;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import static javax.swing.BoxLayout.X_AXIS;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLogcatView implements Disposable {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.logcat.AndroidLogcatView");

  public static final Key<AndroidLogcatView> ANDROID_LOGCAT_VIEW_KEY =
    Key.create("ANDROID_LOGCAT_VIEW_KEY");
  public static final String NO_FILTERS = AndroidBundle.message("android.logcat.filters.none");
  public static final String EDIT_FILTER_CONFIGURATION = AndroidBundle.message("android.logcat.filters.edit");

  /**
   * The {@link #FILTER_LOGCAT_WHEN_SELECTION_CHANGES} property controls whether selecting a VM in the device panel automatically
   * filters logcat to only show messages from that client only.
   */
  @NonNls
  private static final String FILTER_LOGCAT_WHEN_SELECTION_CHANGES = "android.logcat.filter.apply.if.client.selection.changes";

  private final Project myProject;
  private final DeviceContext myDeviceContext;

  private JPanel myPanel;

  private DefaultComboBoxModel myFilterComboBoxModel;
  private String myCurrentFilterName;

  private volatile IDevice myDevice;
  private final Object myLock = new Object();
  private final LogConsoleBase myLogConsole;
  private final AndroidLogFilterModel myLogFilterModel;

  private volatile Reader myCurrentReader;
  private volatile Writer myCurrentWriter;

  private final IDevice myPreselectedDevice;

  private void updateInUIThread() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (myProject.isDisposed()) {
          return;
        }

        updateLogConsole();
      }
    });
  }

  Project getProject() {
    return myProject;
  }

  @NotNull
  public LogConsoleBase getLogConsole() {
    return myLogConsole;
  }

  public void clearLogcat(@Nullable IDevice device) {
    if (device == null) {
      return;
    }

    AndroidLogcatUtil.clearLogcat(myProject, device);

    // In theory, we only need to clear the console. However, due to issues in the platform, clearing logcat via "logcat -c" could
    // end up blocking the current logcat readers. As a result, we need to issue a restart of the logging to work around the platform bug.
    // See https://code.google.com/p/android/issues/detail?id=81164 and https://android-review.googlesource.com/#/c/119673
    if (device.equals(getSelectedDevice())) {
      restartLogging();
    }
  }

  private class MyLoggingReader extends AndroidLoggingReader {
    @Override
    @NotNull
    protected Object getLock() {
      return myLock;
    }

    @Override
    protected Reader getReader() {
      return myCurrentReader;
    }
  }

  /**
   * Logcat view for provided device
   */
  public AndroidLogcatView(@NotNull final Project project, @NotNull IDevice preselectedDevice) {
    this(project, preselectedDevice, null);
  }

  /**
   * Logcat view with device obtained from {@link DeviceContext}
   */
  public AndroidLogcatView(@NotNull final Project project, @NotNull DeviceContext deviceContext) {
    this(project, null, deviceContext);
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private AndroidLogcatView(final Project project, @Nullable IDevice preselectedDevice,
                            @Nullable DeviceContext deviceContext) {
    myDeviceContext = deviceContext;
    myProject = project;
    myPreselectedDevice = preselectedDevice;

    Disposer.register(myProject, this);

    myLogFilterModel =
      new AndroidLogFilterModel() {
        @Nullable private ConfiguredFilter myConfiguredFilter;

        @Override
        protected void setCustomFilter(String filter) {
          AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER = filter;
        }

        @Override
        protected void saveLogLevel(String logLevelName) {
          AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL = logLevelName;
        }

        @Override
        public String getSelectedLogLevelName() {
          return AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_LOG_LEVEL;
        }

        @Override
        public String getCustomFilter() {
          return AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CUSTOM_FILTER;
        }

        @Override
        protected void setConfiguredFilter(@Nullable ConfiguredFilter filter) {
          AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CONFIGURED_FILTER = filter != null ? filter.getName() : "";
          myConfiguredFilter = filter;
        }

        @Nullable
        @Override
        protected ConfiguredFilter getConfiguredFilter() {
          if (myConfiguredFilter == null) {
            final String name = AndroidLogcatFiltersPreferences.getInstance(project).TOOL_WINDOW_CONFIGURED_FILTER;
            myConfiguredFilter = compileConfiguredFilter(name);
          }
          return myConfiguredFilter;
        }
      };
    myLogConsole = new AndroidLogConsole(project, myLogFilterModel);
    myLogConsole.addListener(new LogConsoleListener() {
      @Override
      public void loggingWillBeStopped() {
        if (myCurrentWriter != null) {
          try {
            myCurrentWriter.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    });

    if (preselectedDevice == null && deviceContext != null) {
      DeviceContext.DeviceSelectionListener deviceSelectionListener =
        new DeviceContext.DeviceSelectionListener() {
          @Override
          public void deviceSelected(@Nullable IDevice device) {
            updateInUIThread();
          }

          @Override
          public void deviceChanged(@NotNull IDevice device, int changeMask) {
            if (device == myDevice && ((changeMask & IDevice.CHANGE_STATE) == IDevice.CHANGE_STATE)) {
              myDevice = null;
              updateInUIThread();
            }
          }

          @Override
          public void clientSelected(@Nullable final Client c) {
            if (PropertiesComponent.getInstance().getBoolean(FILTER_LOGCAT_WHEN_SELECTION_CHANGES, true)) {
              if (ApplicationManager.getApplication().isDispatchThread()) {
                createAndSelectFilterForClient(c);
              }
              else {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    createAndSelectFilterForClient(c);
                  }
                });
              }
            }
          }
        };
      deviceContext.addListener(deviceSelectionListener, this);
    }

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

    selectFilter(AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER);
  }

  @NotNull
  public ActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new MyFilterSelectedClientAction());
    return group;
  }

  @NotNull
  public JPanel createSearchComponent(final Project project) {
    final JPanel panel = new JPanel();
    final JComboBox editFiltersCombo = new JComboBox();
    myFilterComboBoxModel = new DefaultComboBoxModel();
    editFiltersCombo.setModel(myFilterComboBoxModel);
    final String configuredFilter = AndroidLogcatFiltersPreferences.
      getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER;
    updateConfiguredFilters(configuredFilter != null && configuredFilter.length() > 0
                            ? configuredFilter
                            : NO_FILTERS);
    // note: the listener is added after the initial call to populate the combo
    // boxes in the above call to updateConfiguredFilters
    editFiltersCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String prev = myCurrentFilterName;
        myCurrentFilterName = (String)editFiltersCombo.getSelectedItem();
        if (myCurrentFilterName == null || myCurrentFilterName.equals(prev)) {
          return;
        }

        if (EDIT_FILTER_CONFIGURATION.equals(myCurrentFilterName)) {
          final EditLogFilterDialog dialog = new EditLogFilterDialog(AndroidLogcatView.this, prev);
          dialog.setTitle(AndroidBundle.message("android.logcat.new.filter.dialog.title"));
          dialog.show();
          if (dialog.isOK()) {
            final AndroidConfiguredLogFilters.MyFilterEntry newEntry =
              dialog.getCustomLogFiltersEntry();
            updateConfiguredFilters(newEntry != null ? newEntry.getName() : NO_FILTERS);
          }
          else {
            myCurrentFilterName = prev;
            editFiltersCombo.setSelectedItem(myCurrentFilterName);
          }
        }
        else {
          selectFilter(myCurrentFilterName);
        }

        // If users explicitly select a filter they want to use, then disable auto filter
        PropertiesComponent.getInstance().setValue(FILTER_LOGCAT_WHEN_SELECTION_CHANGES, Boolean.FALSE.toString());
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

  public void activate() {
    if (isActive()) {
      updateLogConsole();
      selectFilter(AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER);
    }
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = getSelectedDevice();
    if (myDevice != device) {
      synchronized (myLock) {
        myDevice = device;
        if (myCurrentWriter != null) {
          try {
            myCurrentWriter.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        if (myCurrentReader != null) {
          try {
            myCurrentReader.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
        if (device != null) {
          final ConsoleView console = myLogConsole.getConsole();
          if (console != null) {
            console.clear();
          }
          final Pair<Reader, Writer> pair = AndroidLogcatUtil.startLoggingThread(myProject, device, false, myLogConsole);
          if (pair != null) {
            myCurrentReader = pair.first;
            myCurrentWriter = pair.second;
          }
          else {
            myCurrentReader = null;
            myCurrentWriter = null;
          }
        }
      }
    }
  }

  @Nullable
  public IDevice getSelectedDevice() {
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

  @Nullable
  private ConfiguredFilter compileConfiguredFilter(@NotNull String name) {
    if (NO_FILTERS.equals(name)) {
      return null;
    }

    final AndroidConfiguredLogFilters.MyFilterEntry entry =
      AndroidConfiguredLogFilters.getInstance(myProject).findFilterEntryByName(name);
    return ConfiguredFilter.compile(entry, name);
  }

  private void selectFilter(@NotNull final String filterName) {
    final ConfiguredFilter filter = compileConfiguredFilter(filterName);
    selectFilter(filter, filterName);
  }

  private void selectFilter(@Nullable final ConfiguredFilter filter, @NotNull final String filterName) {
    ProgressManager.getInstance().run(
      new Task.Backgroundable(myProject, LogConsoleBase.APPLYING_FILTER_TITLE) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myLogFilterModel.updateConfiguredFilter(filter);
          myCurrentFilterName = filterName;
        }
      });
  }

  private void createAndSelectFilterForClient(@Nullable Client client) {
    ConfiguredFilter filter;
    String name;
    if (client != null) {
      AndroidConfiguredLogFilters.MyFilterEntry f =
        AndroidConfiguredLogFilters.getInstance(myProject).createFilterForProcess(client.getClientData().getPid());
      name = f.getName();
      filter = ConfiguredFilter.compile(f, name);
    }
    else {
      filter = null;
      name = NO_FILTERS;
    }

    selectFilter(filter, name);
    myFilterComboBoxModel.setSelectedItem(NO_FILTERS.equals(name) ? NO_FILTERS : null);
  }

  /** Returns true if there are any filters applied currently on logcat. */
  public boolean isFiltered() {
    return StringUtil.isNotEmpty(myCurrentFilterName) && !NO_FILTERS.equals(myCurrentFilterName);
  }

  public void createAndSelectFilterByPackage(@NotNull String packageName) {
    AndroidConfiguredLogFilters.MyFilterEntry f = AndroidConfiguredLogFilters.getInstance(myProject).getFilterForPackage(packageName, true);
    updateConfiguredFilters(f.getName());
  }

  private void updateConfiguredFilters(@NotNull String defaultSelection) {
    final AndroidConfiguredLogFilters filters = AndroidConfiguredLogFilters.getInstance(myProject);
    final List<AndroidConfiguredLogFilters.MyFilterEntry> entries = filters.getFilterEntries();

    myFilterComboBoxModel.removeAllElements();
    myFilterComboBoxModel.addElement(NO_FILTERS);
    myFilterComboBoxModel.addElement(EDIT_FILTER_CONFIGURATION);

    for (AndroidConfiguredLogFilters.MyFilterEntry entry : entries) {
      final String name = entry.getName();

      myFilterComboBoxModel.addElement(name);
      if (name.equals(defaultSelection)) {
        myFilterComboBoxModel.setSelectedItem(name);
      }
    }

    selectFilter(defaultSelection);
  }

  public JPanel getContentPanel() {
    return myPanel;
  }

  @Override
  public void dispose() {
  }

  private class MyRestartAction extends AnAction {
    public MyRestartAction() {
      super(AndroidBundle.message("android.restart.logcat.action.text"), AndroidBundle.message("android.restart.logcat.action.description"),
            AllIcons.Actions.Restart);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      restartLogging();
    }
  }

  private void restartLogging() {
    myDevice = null;
    updateLogConsole();
  }

  public class AndroidLogConsole extends LogConsoleBase {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    public AndroidLogConsole(Project project, AndroidLogFilterModel logFilterModel) {
      super(project, new MyLoggingReader(), "", false, logFilterModel);
      ConsoleView console = getConsole();
      if (console instanceof ConsoleViewImpl) {
        ConsoleViewImpl c = ((ConsoleViewImpl)console);
        c.addCustomConsoleAction(new Separator());
        c.addCustomConsoleAction(new MyRestartAction());
      }
    }

    @Override
    public boolean isActive() {
      return AndroidLogcatView.this.isActive();
    }

    public void clearLogcat() {
      AndroidLogcatView.this.clearLogcat(getSelectedDevice());
    }
  }

  private class MyFilterSelectedClientAction extends ToggleAction {
    public MyFilterSelectedClientAction() {
      super("Only Show Logcat From Selected Process",
            "",
            AndroidIcons.Ddms.LogcatAutoFilterSelectedPid);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return PropertiesComponent.getInstance().getBoolean(FILTER_LOGCAT_WHEN_SELECTION_CHANGES, true);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      // update state so that future changes to selected client don't trigger a filter action
      PropertiesComponent.getInstance().setValue(FILTER_LOGCAT_WHEN_SELECTION_CHANGES, Boolean.valueOf(state).toString());

      if (myDeviceContext != null) {
        // reset or apply filter depending on current state
        createAndSelectFilterForClient(state ? myDeviceContext.getSelectedClient() : null);
      }
    }
  }
}
