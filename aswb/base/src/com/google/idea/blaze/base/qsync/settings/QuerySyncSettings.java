/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.settings;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.LoggedSettingsProvider;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/** The settings for query sync to be stored per user. */
@State(
    name = "QuerySyncSettings",
    storages = {@Storage("query.sync.user.settings.xml")})
public class QuerySyncSettings implements PersistentStateComponent<QuerySyncSettings.State> {
  static class State {

    /** Default query sync enabled settings, used when we are out of beta. */
    public UseQuerySyncSection.UseQuerySync enabled = UseQuerySyncSection.UseQuerySync.TRUE;

    public boolean showDetailedInformationInEditor = true;

    public boolean buildWorkingSet = false;

    public boolean syncOnFileChanges = false;
  }

  private QuerySyncSettings.State state = new QuerySyncSettings.State();

  public static QuerySyncSettings getInstance() {
    return ApplicationManager.getApplication().getService(QuerySyncSettings.class);
  }

  public void enableUseQuerySync(boolean enabled) {
    state.enabled = enabled ? UseQuerySyncSection.UseQuerySync.TRUE : UseQuerySyncSection.UseQuerySync.FALSE;
  }

  public boolean useQuerySync() {
    return state.enabled != UseQuerySyncSection.UseQuerySync.FALSE;
  }

  public void enableShowDetailedInformationInEditor(boolean showDetailedInformationInEditor) {
    state.showDetailedInformationInEditor = showDetailedInformationInEditor;
  }

  public boolean showDetailedInformationInEditor() {
    return state.showDetailedInformationInEditor;
  }

  public void enableBuildWorkingSet(boolean buildWorkingSet) {
    state.buildWorkingSet = buildWorkingSet;
  }

  public boolean buildWorkingSet() {
    // Force to disable it as it's not a stable feature
    return false;
  }

  public void enableSyncOnFileChanges(boolean syncOnFileChanges) {
    state.syncOnFileChanges = syncOnFileChanges;
  }

  public boolean syncOnFileChanges() {
    // Force to disable it as it's not a stable feature
    return false;
  }

  @Override
  public QuerySyncSettings.State getState() {
    return state;
  }

  @Override
  public void loadState(QuerySyncSettings.State state) {
    this.state = state;
  }

  /**
   * Implementation of {@link LoggedSettingsProvider} which provides query sync related settings to
   * logger.
   */
  public static class SettingsLogger implements LoggedSettingsProvider {

    public SettingsLogger() {}

    @Override
    public String getNamespace() {
      return "QuerySyncSettings";
    }

    @Override
    public ImmutableMap<String, String> getApplicationSettings() {
      QuerySyncSettings settings = QuerySyncSettings.getInstance();

      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      builder.put("enabled", Boolean.toString(settings.useQuerySync()));
      builder.put(
          "showDetailedInformationInEditor",
          Boolean.toString(settings.showDetailedInformationInEditor()));
      builder.put("buildWorkingSet", Boolean.toString(settings.buildWorkingSet()));
      builder.put("syncOnFileChanges", Boolean.toString(settings.syncOnFileChanges()));
      return builder.buildOrThrow();
    }
  }
}
