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
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
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
import org.jetbrains.android.util.AndroidBundle;
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

  /** Logcat view for provided device */
  public AndroidLogcatView(@NotNull final Project project, @NotNull IDevice preselectedDevice) {
    this(project, preselectedDevice, null);
  }

  /** Logcat view with device obtained from {@link DeviceContext} */
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
    myLogConsole = new MyLogConsole(project, myLogFilterModel);
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
          public void clientSelected(@Nullable Client c) {
          }
        };
      deviceContext.addListener(deviceSelectionListener, this);
    }

    JComponent consoleComponent = myLogConsole.getComponent();

    final DefaultActionGroup group1 = new DefaultActionGroup();
    ActionGroup toolbarActions = myLogConsole.getToolbarActions();
    if (toolbarActions != null) {
      group1.addAll(toolbarActions);
    }
    group1.add(new MyRestartAction());
    final JComponent tbComp1 =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group1, false).getComponent();
    myPanel.add(tbComp1, BorderLayout.EAST);

    myPanel.add(consoleComponent, BorderLayout.CENTER);
    Disposer.register(this, myLogConsole);

    updateLogConsole();

    selectFilter(AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER);
  }

  @NotNull
  public JPanel createSearchComponent(final Project project) {
    final JButton clearLogButton = new JButton(AndroidBundle.message("android.logcat.clear.log.button.title"));
    clearLogButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        IDevice device = getSelectedDevice();
        if (device != null) {
          AndroidLogcatUtil.clearLogcat(project, device);
          myLogConsole.clear();
        }
      }
    });

    final JPanel panel = new JPanel();
    final JComboBox editFiltersCombo = new JComboBox();
    myFilterComboBoxModel = new DefaultComboBoxModel();
    editFiltersCombo.setModel(myFilterComboBoxModel);
    editFiltersCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String prev = myCurrentFilterName;
        myCurrentFilterName = (String)editFiltersCombo.getSelectedItem();
        if (myCurrentFilterName.equals(prev)) {
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
          } else {
            myCurrentFilterName = prev;
            editFiltersCombo.setSelectedItem(myCurrentFilterName);
          }
        } else {
          selectFilter(myCurrentFilterName);
        }
      }
    });
    updateConfiguredFilters(NO_FILTERS);

    panel.add(editFiltersCombo);
    panel.add(clearLogButton);

    final JPanel searchComponent = new JPanel();
    searchComponent.setLayout(new BoxLayout(searchComponent, X_AXIS));
    searchComponent.add(myLogConsole.getSearchComponent());
    searchComponent.add(panel);

    return searchComponent;
  }

  protected abstract boolean isActive();
  
  @Nullable
  private ConfiguredFilter compileConfiguredFilter(@NotNull String name) {
    if (NO_FILTERS.equals(name)) {
      return null;
    }

    final AndroidConfiguredLogFilters.MyFilterEntry entry = 
      AndroidConfiguredLogFilters.getInstance(myProject).findFilterEntryByName(name);
    return ConfiguredFilter.compile(entry, name);
  }

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
    } else if (myDeviceContext != null) {
      return myDeviceContext.getSelectedDevice();
    } else {
      return null;
    }
  }

  private void selectFilter(@NotNull final String filterName) {
    final ConfiguredFilter filter = compileConfiguredFilter(filterName);
    ProgressManager.getInstance().run(
      new Task.Backgroundable(myProject, LogConsoleBase.APPLYING_FILTER_TITLE) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myLogFilterModel.updateConfiguredFilter(filter);
          myCurrentFilterName = filterName;
        }
      });
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
      myDevice = null;
      updateLogConsole();
    }
  }

  class MyLogConsole extends LogConsoleBase {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
    public MyLogConsole(Project project, AndroidLogFilterModel logFilterModel) {
      super(project, new MyLoggingReader(), "", false, logFilterModel);
    }

    @Override
    public boolean isActive() {
      return AndroidLogcatView.this.isActive();
    }
  }
}
