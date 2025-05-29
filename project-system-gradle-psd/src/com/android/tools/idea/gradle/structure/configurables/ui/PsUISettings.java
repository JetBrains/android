/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.EventListener;
import org.jetbrains.annotations.NotNull;

@State(
  name = "PsdUISettings",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class PsUISettings implements PersistentStateComponent<PsUISettings> {
  public boolean DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
  public boolean RESOLVED_DEPENDENCIES_GROUP_VARIANTS;
  public boolean RESOLVED_DEPENDENCIES_MINIMIZE = false;
  public boolean TARGET_MODULES_MINIMIZE = false;
  public boolean MODULES_LIST_MINIMIZE;
  // Lazily synced items (No change notification).
  public String MODULE_TAB;
  public String BUILD_VARIANTS_TAB;
  public String LAST_EDITED_SIGNING_CONFIG;
  public String LAST_EDITED_BUILD_TYPE;
  public String LAST_EDITED_FLAVOR_OR_DIMENSION;

  @NotNull private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public static PsUISettings getInstance(Project project) {
    return project.getService(PsUISettings.class);
  }

  public void addListener(@NotNull ChangeListener listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  public void fireUISettingsChanged() {
    myEventDispatcher.getMulticaster().settingsChanged(this);
  }

  @Override
  @NotNull
  public PsUISettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PsUISettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public interface ChangeListener extends EventListener {
    void settingsChanged(@NotNull PsUISettings settings);
  }
}
