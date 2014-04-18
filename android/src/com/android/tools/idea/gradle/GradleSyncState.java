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
package com.android.tools.idea.gradle;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GradleSyncState {
  private static final Logger LOG = Logger.getInstance(GradleSyncState.class);

  public static final Topic<GradleSyncListener> GRADLE_SYNC_TOPIC =
    new Topic<GradleSyncListener>("Project sync with Gradle", GradleSyncListener.class);

  private static final Key<Long> PROJECT_LAST_SYNC_TIMESTAMP_KEY = Key.create("android.gradle.project.last.sync.timestamp");

  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;

  private volatile boolean mySyncInProgress;

  @NotNull
  public static GradleSyncState getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleSyncState.class);
  }

  public GradleSyncState(@NotNull Project project, @NotNull MessageBus messageBus) {
    myProject = project;
    myMessageBus = messageBus;
  }

  public void syncStarted(boolean notifyUser) {
    mySyncInProgress = true;
    if (notifyUser) {
      notifyUser();
    }
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncStarted(myProject);
      }
    });
  }

  public void syncFailed(@NotNull final String message) {
    syncFinished();
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncFailed(myProject, message);
      }
    });
  }

  public void syncEnded() {
    syncFinished();
    syncPublisher(new Runnable() {
      @Override
      public void run() {
        myMessageBus.syncPublisher(GRADLE_SYNC_TOPIC).syncEnded(myProject);
      }
    });
  }

  private void syncFinished() {
    mySyncInProgress = false;
    myProject.putUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY, System.currentTimeMillis());
    notifyUser();
  }

  private void syncPublisher(@NotNull final Runnable publishingTask) {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(publishingTask);
      }
    });
  }

  public void notifyUser() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, new Runnable() {
      @Override
      public void run() {
        EditorNotifications notifications = EditorNotifications.getInstance(myProject);
        VirtualFile[] files = FileEditorManager.getInstance(myProject).getOpenFiles();
        for (VirtualFile file : files) {
          try {
            notifications.updateNotifications(file);
          }
          catch (Throwable e) {
            String filePath = FileUtil.toSystemDependentName(file.getPath());
            String msg = String.format("Failed to update editor notifications for file '%1$s'", filePath);
            LOG.info(msg, e);
          }
        }

        notifications.updateAllNotifications();
        BuildVariantView.getInstance(myProject).updateContents();
      }
    });
  }

  public boolean isSyncInProgress() {
    return mySyncInProgress;
  }

  public long getLastGradleSyncTimestamp() {
    Long timestamp = myProject.getUserData(PROJECT_LAST_SYNC_TIMESTAMP_KEY);
    return timestamp != null ? timestamp.longValue() : -1L;
  }

  /**
   * Indicates whether a project sync with Gradle is needed. A Gradle sync is usually needed when a build.gradle or settings.gradle file has
   * been updated <b>after</b> the last project sync was performed.
   *
   * @return {@code YES} if a sync with Gradle is needed, {@code FALSE} otherwise, or {@code UNSURE} If the timestamp of the last Gradle
   * sync cannot be found.
   */
  @NotNull
  public ThreeState isSyncNeeded() {
    long lastSync = getLastGradleSyncTimestamp();
    if (lastSync < 0) {
      // Previous sync may have failed. We don't know if a sync is needed or not. Let client code decide.
      return ThreeState.UNSURE;
    }
    return isSyncNeeded(lastSync) ? ThreeState.YES : ThreeState.NO;
  }

  /**
   * Indicates whether a project sync with Gradle is needed if changes to build.gradle or settings.gradle files were made after the given
   * time.
   *
   * @param referenceTimeInMillis the given time, in milliseconds.
   * @return {@code true} if a sync with Gradle is needed, {@code false} otherwise.
   * @throws AssertionError if the given time is less than or equal to zero.
   */
  public boolean isSyncNeeded(long referenceTimeInMillis) {
    assert referenceTimeInMillis > 0;
    if (mySyncInProgress) {
      return false;
    }
    File settingsFilePath = new File(myProject.getBasePath(), SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsFilePath.exists() && settingsFilePath.lastModified() > referenceTimeInMillis) {
      return true;
    }
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      VirtualFile buildFile = GradleUtil.getGradleBuildFile(module);
      if (buildFile != null) {
        File buildFilePath = VfsUtilCore.virtualToIoFile(buildFile);
        if (buildFilePath.lastModified() > referenceTimeInMillis) {
          return true;
        }
      }
    }
    return false;
  }
}
