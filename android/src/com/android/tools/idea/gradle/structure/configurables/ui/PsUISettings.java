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
import com.intellij.openapi.components.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

@State(
  name = "PsdUISettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/android.gradle.psd.xml")
)
public class PsUISettings implements PersistentStateComponent<PsUISettings> {
  public boolean DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
  public boolean RESOLVED_DEPENDENCIES_GROUP_VARIANTS = true;
  public boolean TARGET_ARTIFACTS_MINIMIZE = true;
  public boolean MODULES_LIST_MINIMIZE;

  @NotNull private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public static PsUISettings getInstance() {
    return ServiceManager.getService(PsUISettings.class);
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
  public void loadState(PsUISettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public interface ChangeListener extends EventListener {
    void settingsChanged(@NotNull PsUISettings settings);
  }
}
