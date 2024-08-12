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
package com.google.idea.blaze.android.sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.intellij.openapi.project.Project;
import java.util.stream.Collectors;

/**
 * Returns an error during sync (with quick-fix) if NDK support is requested, but the required
 * plugin dependencies aren't enabled.
 */
public final class BlazeNdkDependencySyncPlugin implements BlazeSyncPlugin {

  private static class PluginNameAndId {
    final String name;
    final String id;

    PluginNameAndId(String pluginName, String pluginId) {
      this.name = pluginName;
      this.id = pluginId;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static final ImmutableSet<PluginNameAndId> REQUIRED_PLUGINS =
      ImmutableSet.of(new PluginNameAndId("Android NDK Support", "com.android.tools.ndk"));

  /** Returns the IDs of the plugins required for NDK support. */
  @VisibleForTesting
  public static ImmutableSet<String> getPluginsRequiredForNdkSupport() {
    return ImmutableSet.copyOf(
        REQUIRED_PLUGINS.stream().map(plugin -> plugin.id).collect(Collectors.toSet()));
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    if (!blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C)) {
      return true;
    }
    boolean missingPlugin = false;
    for (PluginNameAndId plugin : REQUIRED_PLUGINS) {
      if (!PluginUtils.isPluginEnabled(plugin.id)) {
        missingPlugin = true;
        notifyMissingPlugin(context, plugin);
      }
    }
    return !missingPlugin;
  }

  private static void notifyMissingPlugin(BlazeContext context, PluginNameAndId plugin) {
    String msg =
        String.format(
            "Plugin '%s' required for NDK support isn't enabled.\n"
                + "Click here to install/enable it, then restart the IDE",
            plugin.name);
    IssueOutput.error(msg)
        .navigatable(PluginUtils.installOrEnablePluginNavigable(plugin.id))
        .submit(context);
  }
}
