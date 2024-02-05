/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.Transient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The settings which are shared by all projects for visualization (a.k.a. Layout Validation) tool.
 */
@Service
@State(name = "VisualizationTool", storages = @Storage(value = "visualizationTool.xml", roamingType = RoamingType.DISABLED))
public final class VisualizationToolSettings implements PersistentStateComponent<VisualizationToolSettings.MyState> {
  private GlobalState myGlobalState = new GlobalState();

  public static VisualizationToolSettings getInstance() {
    return ApplicationManager.getApplication().getService(VisualizationToolSettings.class);
  }

  @NotNull
  public GlobalState getGlobalState() {
    return myGlobalState;
  }

  @Override
  public MyState getState() {
    final MyState state = new MyState();
    state.setState(myGlobalState);
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myGlobalState = state.getState();
  }

  public static class MyState {
    private GlobalState myGlobalState = new GlobalState();

    public GlobalState getState() {
      return myGlobalState;
    }

    public void setState(GlobalState state) {
      myGlobalState = state;
    }
  }

  public static class GlobalState {
    private boolean myFirstTimeOpen = true;
    private boolean myVisible = false;
    private boolean myShowDecoration = false;
    @NotNull private String myConfigurationSetId = ConfigurationSetProvider.defaultSet.getId();
    @NotNull private Map<String, CustomConfigurationSet> myCustomConfigurationSets = new LinkedHashMap<>();

    public boolean isFirstTimeOpen() {
      return myFirstTimeOpen;
    }

    public void setFirstTimeOpen(boolean firstTimeOpen) {
      myFirstTimeOpen = firstTimeOpen;
    }

    public boolean isVisible() {
      return myVisible;
    }

    public void setVisible(boolean visible) {
      myVisible = visible;
    }

    public boolean getShowDecoration() {
      return myShowDecoration;
    }

    public void setShowDecoration(boolean showDecoration) {
      myShowDecoration = showDecoration;
    }

    /**
     * Get the name of {@link ConfigurationSet}. This function is public just because it is part of JavaBean.
     * Do not use this function; For getting {@link ConfigurationSet}, use {@link #getLastSelectedConfigurationSet} instead.
     *
     * Because {@link ConfigurationSet} is an enum class, once the saved {@link ConfigurationSet} is renamed or deleted, the fatal error
     * may happen due to parsing persistent state failed. Thus, use {@link #getLastSelectedConfigurationSet} instead which handles the exception cases.
     */
    @SuppressWarnings("unused") // Used by JavaBeans
    @NotNull
    public String getConfigurationSetId() {
      return myConfigurationSetId;
    }

    /**
     * Set the name of {@link ConfigurationSet}. This function is public just because it is part of JavaBean.
     * Do not use this function; For setting {@link ConfigurationSet}, use {@link #setLastSelectedConfigurationSet(ConfigurationSet)} instead.
     */
    @SuppressWarnings("unused") // Used by JavaBeans
    public void setConfigurationSetId(@NotNull String configurationSetId) {
      myConfigurationSetId = configurationSetId;
    }

    @NotNull
    public Map<String, CustomConfigurationSet> getCustomConfigurationSets() {
      return myCustomConfigurationSets;
    }

    @SuppressWarnings("unused") // Used by JavaBeans
    public void setCustomConfigurationSets(@NotNull Map<String, CustomConfigurationSet> customConfigurationSets) {
      myCustomConfigurationSets = customConfigurationSets;
    }

    /**
     * Helper function to get the selected {@link ConfigurationSet}. This function handles the illegal name case which happens when saved
     * {@link ConfigurationSet} is renamed or deleted.
     */
    @Transient
    @NotNull
    public ConfigurationSet getLastSelectedConfigurationSet() {
      ConfigurationSet set = ConfigurationSetProvider.getConfigurationById(myConfigurationSetId);
      if (set == null || !set.getVisible()) {
        // The saved configuration set may be renamed or deleted, use default one instead.
        set = ConfigurationSetProvider.defaultSet;
        myConfigurationSetId = ConfigurationSetProvider.defaultSet.getId();
      }
      return set;
    }

    /**
     * Helper function to set {@link ConfigurationSet}.
     */
    @Transient
    public void setLastSelectedConfigurationSet(@NotNull ConfigurationSet configurationSet) {
      myConfigurationSetId = configurationSet.getId();
    }
  }
}
