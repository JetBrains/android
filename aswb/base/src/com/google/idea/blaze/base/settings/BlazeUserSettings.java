/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.settings;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.LoggedSettingsProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.xmlb.XmlSerializerUtil;

/** Stores blaze view settings. */
@State(
    name = "BlazeUserSettings",
    storages = {
      @Storage("blaze.user.settings.xml"),
      @Storage(value = "blaze.view.xml", deprecated = true)
    })
public class BlazeUserSettings implements PersistentStateComponent<BlazeUserSettings> {

  /**
   * A setting to control whether the Blaze Console / Blaze Problems view is activated for a given
   * operation.
   */
  public enum FocusBehavior {
    ALWAYS("Always"),
    ON_ERROR("On error"),
    NEVER("Never");

    private final String uiName;

    FocusBehavior(String uiName) {
      this.uiName = uiName;
    }

    @Override
    public String toString() {
      return uiName;
    }
  }

  private static final String OLD_DEFAULT_BLAZE_PATH =
      SystemInfo.isMac ? "/usr/local/bin/blaze" : "/usr/bin/blaze";
  private static final String DEFAULT_BLAZE_PATH = "blaze";
  private static final String DEFAULT_BAZEL_PATH = "bazel";
  private static final String DEFAULT_BUILDIFIER_PATH = "buildifier";

  private FocusBehavior showBlazeConsoleOnSync = FocusBehavior.ALWAYS;
  private FocusBehavior showBlazeProblemsViewOnSync = FocusBehavior.ALWAYS;
  private FocusBehavior showBlazeConsoleOnRun = FocusBehavior.ALWAYS;
  private FocusBehavior showProblemsViewOnRun = FocusBehavior.NEVER;
  private boolean syncStatusPopupShown = false;
  private boolean expandSyncToWorkingSet = true;
  private boolean showPerformanceWarnings = false;
  private boolean collapseProjectView = true;

  private boolean selectNewestChildTask = false;
  private boolean formatBuildFilesOnSave = true;
  private boolean showAddFileToProjectNotification = true;
  private String blazeBinaryPath = DEFAULT_BLAZE_PATH;
  private String bazelBinaryPath = DEFAULT_BAZEL_PATH;
  private String buildifierBinaryPath = DEFAULT_BUILDIFIER_PATH;

  public static BlazeUserSettings getInstance() {
    return ApplicationManager.getApplication().getService(BlazeUserSettings.class);
  }

  @Override
  public BlazeUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(BlazeUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public FocusBehavior getShowBlazeConsoleOnSync() {
    return showBlazeConsoleOnSync;
  }

  public void setShowBlazeConsoleOnSync(FocusBehavior showBlazeConsoleOnSync) {
    this.showBlazeConsoleOnSync = showBlazeConsoleOnSync;
  }

  public FocusBehavior getShowProblemsViewOnSync() {
    return showBlazeProblemsViewOnSync;
  }

  public void setShowProblemsViewOnSync(FocusBehavior showProblemsViewOnSync) {
    this.showBlazeProblemsViewOnSync = showProblemsViewOnSync;
  }

  public FocusBehavior getShowBlazeConsoleOnRun() {
    return showBlazeConsoleOnRun;
  }

  public void setShowBlazeConsoleOnRun(FocusBehavior showBlazeConsoleOnRun) {
    this.showBlazeConsoleOnRun = showBlazeConsoleOnRun;
  }

  public FocusBehavior getShowProblemsViewOnRun() {
    return showProblemsViewOnRun;
  }

  public void setShowProblemsViewOnRun(FocusBehavior showProblemsViewForRunAction) {
    this.showProblemsViewOnRun = showProblemsViewForRunAction;
  }

  public boolean getSyncStatusPopupShown() {
    return syncStatusPopupShown;
  }

  public void setSyncStatusPopupShown(boolean syncStatusPopupShown) {
    this.syncStatusPopupShown = syncStatusPopupShown;
  }

  public boolean getExpandSyncToWorkingSet() {
    return expandSyncToWorkingSet;
  }

  public void setExpandSyncToWorkingSet(boolean expandSyncToWorkingSet) {
    this.expandSyncToWorkingSet = expandSyncToWorkingSet;
  }

  public boolean getShowPerformanceWarnings() {
    return showPerformanceWarnings;
  }

  public void setShowPerformanceWarnings(boolean showPerformanceWarnings) {
    this.showPerformanceWarnings = showPerformanceWarnings;
  }

  public String getBlazeBinaryPath() {
    return StringUtil.defaultIfEmpty(blazeBinaryPath, DEFAULT_BLAZE_PATH).trim();
  }

  public void setBlazeBinaryPath(String blazeBinaryPath) {
    this.blazeBinaryPath = StringUtil.defaultIfEmpty(blazeBinaryPath, DEFAULT_BLAZE_PATH).trim();
  }

  /** Resets the blaze binary path to its default value. */
  public void clearBlazeBinaryPath() {
    blazeBinaryPath = DEFAULT_BLAZE_PATH;
  }

  public boolean isDefaultBlazePath() {
    return DEFAULT_BLAZE_PATH.equals(getBlazeBinaryPath())
        || OLD_DEFAULT_BLAZE_PATH.equals(getBlazeBinaryPath());
  }

  public String getBuildifierBinaryPath() {
    return StringUtil.defaultIfEmpty(buildifierBinaryPath, DEFAULT_BUILDIFIER_PATH).trim();
  }

  public void setBuildifierBinaryPath(String buildifierBinaryPath) {
    this.buildifierBinaryPath =
        StringUtil.defaultIfEmpty(buildifierBinaryPath, DEFAULT_BUILDIFIER_PATH).trim();
  }

  public String getBazelBinaryPath() {
    return StringUtil.defaultIfEmpty(bazelBinaryPath, DEFAULT_BAZEL_PATH).trim();
  }

  public void setBazelBinaryPath(String bazelBinaryPath) {
    this.bazelBinaryPath = StringUtil.defaultIfEmpty(bazelBinaryPath, DEFAULT_BAZEL_PATH).trim();
  }

  public boolean getCollapseProjectView() {
    return collapseProjectView;
  }

  public void setCollapseProjectView(boolean collapseProjectView) {
    this.collapseProjectView = collapseProjectView;
  }

  public boolean getSelectNewestChildTask() {
    return selectNewestChildTask;
  }

  public void setSelectNewestChildTask(boolean selectNewestChildTask) {
    this.selectNewestChildTask = selectNewestChildTask;
  }

  public boolean getFormatBuildFilesOnSave() {
    return formatBuildFilesOnSave;
  }

  public void setFormatBuildFilesOnSave(boolean formatBuildFilesOnSave) {
    this.formatBuildFilesOnSave = formatBuildFilesOnSave;
  }

  public boolean getShowAddFileToProjectNotification() {
    return showAddFileToProjectNotification;
  }

  public void setShowAddFileToProjectNotification(boolean showAddFileToProjectNotification) {
    if (this.showAddFileToProjectNotification == showAddFileToProjectNotification) {
      return;
    }
    this.showAddFileToProjectNotification = showAddFileToProjectNotification;
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      EditorNotifications.getInstance(project).updateAllNotifications();
    }
  }

  static class SettingsLogger implements LoggedSettingsProvider {

    @Override
    public String getNamespace() {
      return "BlazeUserSettings";
    }

    @Override
    public ImmutableMap<String, String> getApplicationSettings() {
      BlazeUserSettings settings = BlazeUserSettings.getInstance();

      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      builder.put("showBlazeConsoleOnSync", settings.showBlazeConsoleOnSync.name());
      builder.put("showBlazeProblemsViewOnSync", settings.showBlazeProblemsViewOnSync.name());
      builder.put("showBlazeConsoleOnRun", settings.showBlazeConsoleOnRun.name());
      builder.put("showProblemsViewOnRun", settings.showProblemsViewOnRun.name());
      builder.put("expandSyncToWorkingSet", Boolean.toString(settings.expandSyncToWorkingSet));
      builder.put("formatBuildFilesOnSave", Boolean.toString(settings.formatBuildFilesOnSave));
      builder.put(
          "showAddFileToProjectNotification",
          Boolean.toString(settings.showAddFileToProjectNotification));
      builder.put("blazeBinaryPath", settings.blazeBinaryPath);
      builder.put("bazelBinaryPath", settings.bazelBinaryPath);
      builder.put("buildifierBinaryPath", settings.buildifierBinaryPath);
      return builder.build();
    }
  }
}
