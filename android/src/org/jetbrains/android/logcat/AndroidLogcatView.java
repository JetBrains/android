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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import static javax.swing.BoxLayout.X_AXIS;

/**
 * A UI panel which wraps a console that prints output from Android's logging system.
 */
public abstract class AndroidLogcatView implements Disposable {
  private static final Logger LOG = Logger.getInstance(AndroidLogcatView.class);

  public static final Key<AndroidLogcatView> ANDROID_LOGCAT_VIEW_KEY = Key.create("ANDROID_LOGCAT_VIEW_KEY");

  static final String SELECTED_APP_FILTER = AndroidBundle.message("android.logcat.filters.selected");
  static final String NO_FILTERS = AndroidBundle.message("android.logcat.filters.none");
  static final String EDIT_FILTER_CONFIGURATION = AndroidBundle.message("android.logcat.filters.edit");

  private final Project myProject;
  private final DeviceContext myDeviceContext;

  private JPanel myPanel;

  private DefaultComboBoxModel myFilterComboBoxModel;

  private volatile IDevice myDevice;
  private final LogConsoleBase myLogConsole;
  private final AndroidLogFilterModel myLogFilterModel;

  private final Object myReaderWriterLock = new Object();
  @GuardedBy("myReaderWriterLock")
  private Reader myCurrentReader;
  @GuardedBy("myReaderWriterLock")
  private Writer myCurrentWriter;

  private final IDevice myPreselectedDevice;

  @NotNull
  private ConfiguredFilter mySelectedAppFilter;

  @NotNull
  private ConfiguredFilter myNoFilter;

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

    AndroidLogcatUtils.clearLogcat(myProject, device);

    // In theory, we only need to clear the console. However, due to issues in the platform, clearing logcat via "logcat -c" could
    // end up blocking the current logcat readers. As a result, we need to issue a restart of the logging to work around the platform bug.
    // See https://code.google.com/p/android/issues/detail?id=81164 and https://android-review.googlesource.com/#/c/119673
    if (device.equals(getSelectedDevice())) {
      restartLogging();
    }
  }

  private final class MyLoggingReader extends Reader {
    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
      synchronized (myReaderWriterLock) {
        return myCurrentReader != null ? myCurrentReader.read(cbuf, off, len) : -1;
      }
    }

    @Override
    public boolean ready() throws IOException {
      synchronized (myReaderWriterLock) {
        return myCurrentReader != null && myCurrentReader.ready();
      }
    }

    @Override
    public void close() throws IOException {
      synchronized (myReaderWriterLock) {
        if (myCurrentReader != null) {
          myCurrentReader.close();
        }
      }
    }
  }

  /**
   * Logcat view with device obtained from {@link DeviceContext}
   */
  public AndroidLogcatView(@NotNull final Project project, @NotNull DeviceContext deviceContext) {
    this(project, null, deviceContext);
  }

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
          return myConfiguredFilter;
        }
      };
    myLogConsole = new AndroidLogConsole(project, myLogFilterModel);
    myLogConsole.addListener(new LogConsoleListener() {
      @Override
      public void loggingWillBeStopped() {
        synchronized (myReaderWriterLock) {
          if (myCurrentWriter != null) {
            try {
              myCurrentWriter.close();
            }
            catch (IOException e) {
              LOG.error(e);
            }
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
            boolean reselect = myFilterComboBoxModel.getSelectedItem() == mySelectedAppFilter;
            AndroidConfiguredLogFilters.FilterEntry f;
            if (c != null) {
              f = AndroidConfiguredLogFilters.getInstance(myProject).createFilterForProcess(c.getClientData().getPid());
            }
            else {
              f = new AndroidConfiguredLogFilters.FilterEntry();
            }
            // Replace mySelectedAppFilter
            int index = myFilterComboBoxModel.getIndexOf(mySelectedAppFilter);
            if (index >= 0) {
              myFilterComboBoxModel.removeElementAt(index);
              mySelectedAppFilter = ConfiguredFilter.compile(f, SELECTED_APP_FILTER);
              myFilterComboBoxModel.insertElementAt(mySelectedAppFilter, index);
            }
            if (reselect) {
              myFilterComboBoxModel.setSelectedItem(mySelectedAppFilter);
            }
          }
        };
      deviceContext.addListener(deviceSelectionListener, this);
    }

    mySelectedAppFilter = ConfiguredFilter.compile(new AndroidConfiguredLogFilters.FilterEntry(), SELECTED_APP_FILTER);
    myNoFilter = ConfiguredFilter.compile(new AndroidConfiguredLogFilters.FilterEntry(), NO_FILTERS);

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
    editFiltersCombo.setModel(myFilterComboBoxModel);
    String def = AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER;
    if (StringUtil.isEmpty(def)) {
      def = myDeviceContext != null ? SELECTED_APP_FILTER : NO_FILTERS;
    }
    updateFilterCombobox(def);
    applySelectedFilter();
    // note: the listener is added after the initial call to populate the combo
    // boxes in the above call to updateConfiguredFilters
    editFiltersCombo.addItemListener(new ItemListener() {
      @Nullable private ConfiguredFilter myLastSelected;

      @Override
      public void itemStateChanged(ItemEvent e) {
        Object item = e.getItem();
        if (e.getStateChange() == ItemEvent.DESELECTED) {
          if (item instanceof ConfiguredFilter) {
            myLastSelected = (ConfiguredFilter)item;
          }
        }
        else if (e.getStateChange() == ItemEvent.SELECTED) {

          if (item instanceof ConfiguredFilter) {
            applySelectedFilter();
          }
          else {
            assert EDIT_FILTER_CONFIGURATION.equals(item);
            final EditLogFilterDialog dialog =
              new EditLogFilterDialog(AndroidLogcatView.this, myLastSelected == null ? null : myLastSelected.getName());
            dialog.setTitle(AndroidBundle.message("android.logcat.new.filter.dialog.title"));
            if (dialog.showAndGet()) {
              final AndroidConfiguredLogFilters.FilterEntry newEntry = dialog.getCustomLogFiltersEntry();
              updateFilterCombobox(newEntry != null ? newEntry.getName() : null);
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
        if (value instanceof ConfiguredFilter) {
          setBorder(null);
          append(((ConfiguredFilter)value).getName());
        } else {
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
      // TODO this is here so if some changes happened in the other logcat view, things get refreshed.
      // This is because they share AndroidLogcatFiltersPreferences, but needs to be fixed properly.
      updateFilterCombobox(AndroidLogcatFiltersPreferences.getInstance(myProject).TOOL_WINDOW_CONFIGURED_FILTER);
    }
    if (myLogConsole != null) {
      myLogConsole.activate();
    }
  }

  private void updateLogConsole() {
    IDevice device = getSelectedDevice();
    if (myDevice != device) {
      synchronized (myReaderWriterLock) {
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
          final Pair<Reader, Writer> pair = AndroidLogcatUtils.startLoggingThread(myProject, device, false, myLogConsole);
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
    if (filter instanceof ConfiguredFilter) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, LogConsoleBase.APPLYING_FILTER_TITLE) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          myLogFilterModel.updateConfiguredFilter((ConfiguredFilter)filter);
        }
      });
    }
  }

  private void updateFilterCombobox(String select) {
    final AndroidConfiguredLogFilters filters = AndroidConfiguredLogFilters.getInstance(myProject);
    final List<AndroidConfiguredLogFilters.FilterEntry> entries = filters.getFilterEntries();

    myFilterComboBoxModel.removeAllElements();
    if (myDeviceContext != null) {
      myFilterComboBoxModel.addElement(mySelectedAppFilter);
    }
    myFilterComboBoxModel.addElement(myNoFilter);
    myFilterComboBoxModel.addElement(EDIT_FILTER_CONFIGURATION);

    for (AndroidConfiguredLogFilters.FilterEntry entry : entries) {
      final String name = entry.getName();

      ConfiguredFilter filter = ConfiguredFilter.compile(entry, entry.getName());
      myFilterComboBoxModel.addElement(filter);
      if (name.equals(select)) {
        myFilterComboBoxModel.setSelectedItem(filter);
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
      restartLogging();
    }
  }

  private void restartLogging() {
    myDevice = null;
    updateLogConsole();
  }

  public final class AndroidLogConsole extends LogConsoleBase {
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
}
