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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
      }
      return null;
    }
  }

  @NotNull private String packageName = "";
  private JPanel myRootPanel;
  private JButton myRetentionDebugButton;
  private boolean retentionInProgress = false;
  private Lock retentionLoadLock = new ReentrantLock();

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

  private void createUIComponents() {
    myRootPanel = new RetentionPanel();
    myRetentionDebugButton = new JButton();
    myRetentionDebugButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        retentionLoadLock.lock();
        boolean inProgress = retentionInProgress;
        retentionInProgress = true;
        myRetentionDebugButton.setEnabled(false);
        retentionLoadLock.unlock();
        if (inProgress) {
          return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          ApplicationManager.getApplication().runReadAction(
            () -> {
              try {
                DataContext dataContext = DataManager.getInstance().getDataContext(myRootPanel);
                ActionManager.getInstance().getAction(RetentionConstantsKt.LOAD_RETENTION_ACTION_ID).actionPerformed(
                  AnActionEvent.createFromDataContext("", null, dataContext));
              } finally {
                retentionLoadLock.lock();
                myRetentionDebugButton.setEnabled(true);
                retentionInProgress = false;
                retentionLoadLock.unlock();
              }
            }
          );
        });
      }
    });
  }
}
