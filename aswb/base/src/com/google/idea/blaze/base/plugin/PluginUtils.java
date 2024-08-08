/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.plugin;

import com.google.common.collect.ImmutableSet;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import java.util.HashSet;
import java.util.Set;

/** Utility methods for querying / manipulating other plugins. */
public final class PluginUtils {

  private PluginUtils() {}

  /** Install and/or enable the given plugin. Does nothing for plugins already enabled. */
  public static void installOrEnablePlugin(String pluginId) {
    installOrEnablePlugins(ImmutableSet.of(pluginId));
  }

  /** Install and/or enable the given plugins. Does nothing for plugins already enabled. */
  public static void installOrEnablePlugins(Set<String> pluginIds) {
    Set<String> toInstall = new HashSet<>();
    for (String id : pluginIds) {
      if (isPluginEnabled(id)) {
        continue;
      }
      if (isPluginInstalled(id)) {
        if (!PluginManager.enablePlugin(id)) {
          notifyPluginEnableFailed(id);
        }
      } else {
        toInstall.add(id);
      }
    }
    if (!toInstall.isEmpty()) {
      PluginsAdvertiser.installAndEnablePlugins(toInstall, EmptyRunnable.INSTANCE);
    }
  }

  /** Returns a {@link Navigatable} which will install (if necessary) and enable the given plugin */
  public static Navigatable installOrEnablePluginNavigable(String pluginId) {
    return new NavigatableAdapter() {
      @Override
      public void navigate(boolean requestFocus) {
        installOrEnablePlugin(pluginId);
      }
    };
  }

  public static boolean isPluginInstalled(String pluginId) {
    return getPluginDescriptor(pluginId) != null;
  }

  public static boolean isPluginEnabled(String pluginId) {
    IdeaPluginDescriptor descriptor = getPluginDescriptor(pluginId);
    return descriptor != null && descriptor.isEnabled();
  }

  private static IdeaPluginDescriptor getPluginDescriptor(String pluginId) {
    return PluginManager.getPlugin(PluginId.getId(pluginId));
  }

  private static void notifyPluginEnableFailed(String pluginId) {
    String msg =
        String.format(
            "Failed to enable plugin '%s'. Check for errors in the Event Log, or run 'Help > Check "
                + "for Updates' to check if the plugin needs updating.",
            pluginId);
    Messages.showErrorDialog(msg, "Failed to enable plugin");
  }
}
