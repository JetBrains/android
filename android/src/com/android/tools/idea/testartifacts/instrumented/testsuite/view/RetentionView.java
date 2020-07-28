/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view;

import com.android.tools.idea.testartifacts.instrumented.RetentionConstantsKt;
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO(yahan@) rework this view when we have the UI mock

/**
 * Shows the Android Test Retention artifacts
 */
public class RetentionView {
  private class RetentionPanel extends JPanel implements DataProvider {
    private static final String SNAPSHOT_ID = "test_failure_snapshot";
    private File snapshotFile = null;
    private AndroidDevice device = null;
    public void setAndroidDevice(AndroidDevice device) {
      this.device = device;
    }

    public void setSnapshotFile(File snapshotFile) {
      this.snapshotFile = snapshotFile;
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (dataId == RetentionConstantsKt.EMULATOR_SNAPSHOT_ID_KEY.getName()) {
        return SNAPSHOT_ID;
      } else if (dataId == RetentionConstantsKt.EMULATOR_SNAPSHOT_FILE_KEY.getName()) {
        return snapshotFile;
      } else if (dataId == RetentionConstantsKt.PACKAGE_NAME_KEY.getName()) {
        return packageName;
      } else if (dataId == RetentionConstantsKt.RETENTION_AUTO_CONNECT_DEBUGGER_KEY.getName()) {
        return true;
      } else if (dataId == RetentionConstantsKt.RETENTION_ON_FINISH_KEY.getName()) {
        return (Runnable)() -> myRetentionDebugButton.setEnabled(true);
      } else if (dataId == RetentionConstantsKt.DEVICE_NAME_KEY.getName()) {
        return device.getDeviceName();
      }
      return null;
    }
  }

  @NotNull private String packageName = "";
  private JPanel myRootPanel;
  private JButton myRetentionDebugButton;

  /**
   * Returns the root panel.
   */
  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public void setPackageName(@NotNull String packageName) {
    this.packageName = packageName;
  }

  public void setSnapshotFile(File snapshotFile) {
    ((RetentionPanel)myRootPanel).setSnapshotFile(snapshotFile);
  }

  public void setAndroidDevice(AndroidDevice device) { ((RetentionPanel)myRootPanel).setAndroidDevice(device); }

  private void createUIComponents() {
    myRootPanel = new RetentionPanel();
    myRetentionDebugButton = new JButton();
    myRetentionDebugButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRetentionDebugButton.setEnabled(false);
        DataContext dataContext = DataManager.getInstance().getDataContext(myRootPanel);
        ActionManager.getInstance().getAction(RetentionConstantsKt.LOAD_RETENTION_ACTION_ID).actionPerformed(
          AnActionEvent.createFromDataContext("", null, dataContext));
      }
    });
  }
}
