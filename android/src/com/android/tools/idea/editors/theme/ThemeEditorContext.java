/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lots of objects in theme editor require access to instance of Configuration,
 * Module and other classes that provide a way to access Android-specific data.
 * However, the problem with passing instances of these classes directly is that
 * they have to be updated from time to time. To make it somewhat easier, an
 * instance of ThemeEditorContext should be used instead.
 *
 * ThemeEditorContext provides instances of several classes for accessing data
 * used in several classes of the theme editor. The difference between using
 * ThemeEditorContext and providing these instances directly is that when
 * Configuration has to be updated, it has to be done only in one place.
 * It's assumed that only one instance of ThemeEditorContext should be created
 * and all classes using its capabilities should retain this single instance.
 */
public class ThemeEditorContext implements Disposable {
  // Field is initialized in method called from constructor which checker doesn't see, warning could be ignored
  @SuppressWarnings("NullableProblems")
  private @NotNull Configuration myConfiguration;

  /**
   * Stores ThemeEditorStyle currently being edited
   */
  private @Nullable ConfiguredThemeEditorStyle myCurrentTheme;

  // Field is initialized in method called from constructor which checker doesn't see, warning could be ignored
  @SuppressWarnings("NullableProblems")
  private @NotNull ThemeResolver myThemeResolver;

  private final List<ChangeListener> myChangeListeners = new ArrayList<ChangeListener>();
  private final List<ConfigurationListener> myConfigurationListeners = new ArrayList<ConfigurationListener>();
  private boolean myEnabledListeners = true;
  private final ConfigurationListener myConfigurationListener = new ConfigurationListener() {
    @Override
    public boolean changed(int flags) {
      if (!myEnabledListeners) {
        return true;
      }

      boolean accepted = true;
      for (ConfigurationListener listener : myConfigurationListeners) {
        accepted &= listener.changed(flags);
      }
      return accepted;
    }
  };

  public ThemeEditorContext(@NotNull Configuration configuration) {
    setConfiguration(configuration);

    addConfigurationListener(new ConfigurationListener() {
      @Override
      public boolean changed(int flags) {
        if ((ConfigurationListener.MASK_FOLDERCONFIG & flags) != 0) {
          updateThemeResolver();
        }
        return true;
      }
    });
  }

  public void updateThemeResolver() {
    // Disable listeners since we are not interested in the setTheme(null) update
    myEnabledListeners = false;
    // setTheme(null) is required since the configuration could have a link to a non existent theme (if it was removed).
    // If the configuration is pointing to a theme that does not exist anymore, the local resource resolution breaks so ThemeResolver
    // fails to find the local themes.
    Configuration resolverConfiguration = myConfiguration.clone();
    resolverConfiguration.setTheme(null);
    myEnabledListeners = true;
    myThemeResolver = new ThemeResolver(resolverConfiguration);
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @NotNull
  public Module getCurrentContextModule() {
    return myConfiguration.getModule();
  }

  public void setCurrentTheme(@Nullable ConfiguredThemeEditorStyle currentTheme) {
    myCurrentTheme = currentTheme;
  }

  @Nullable
  public ConfiguredThemeEditorStyle getCurrentTheme() {
    return myCurrentTheme;
  }

  /**
   * Function for acquiring Module that should be used every time resource resolving
   * for possible values is needed.
   */
  @NotNull
  public Module getModuleForResources() {
    if (myCurrentTheme != null && myCurrentTheme.getSourceModule() != null) {
      // If we have a source module, we want to use it for resolving possible values
      return myCurrentTheme.getSourceModule();
    }
    // Otherwise, it should be a library theme or framework theme, in which case we will create
    // a new theme in the current rendering context, which we are returning here;
    return myConfiguration.getModule();
  }

  public void setCurrentContextModule(final @NotNull Module module) {
    setConfiguration(ThemeEditorUtils.getConfigurationForModule(module));
  }

  @Nullable("if there is no theme selected or the current selected module is not valid")
  public ResourceResolver getResourceResolver() {
    if (getCurrentContextModule().isDisposed()) {
      // In some cases we might be asked for a resolver when the module has been already been disposed.
      // This could happen when we are refreshing the attributes table and the old one is still visible.
      return null;
    }

    return myConfiguration.getResourceResolver();
  }

  @NotNull
  public Project getProject() {
    return myConfiguration.getModule().getProject();
  }

  public void addConfigurationListener(@NotNull ConfigurationListener configurationListener) {
    myConfigurationListeners.add(configurationListener);
  }

  @NotNull
  public ThemeResolver getThemeResolver() {
    return myThemeResolver;
  }

  public void setConfiguration(@NotNull Configuration configuration) {
    // myConfiguration can be null when called from the constructor
    //noinspection ConstantConditions
    if (myConfiguration != null) {
      myConfiguration.removeListener(myConfigurationListener);
    }

    myConfiguration = configuration;
    updateThemeResolver();

    myConfiguration.addListener(myConfigurationListener);

    fireNewConfiguration();
  }

  public void addChangeListener(ChangeListener listener) {
    myChangeListeners.add(listener);
  }

  private void fireNewConfiguration() {
    for (ChangeListener listener : myChangeListeners) {
      listener.onNewConfiguration(this);
    }
  }

  @Override
  public void dispose() {
    myConfiguration.removeListener(myConfigurationListener);
    myConfigurationListeners.clear();
  }

  public interface ChangeListener {
    void onNewConfiguration(ThemeEditorContext context);
  }
}
