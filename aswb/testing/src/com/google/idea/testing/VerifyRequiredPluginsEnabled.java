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
package com.google.idea.testing;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

/** Check that plugins flagged by the test runner as required are actually loaded. */
public class VerifyRequiredPluginsEnabled {

  /**
   * Checks that the specified plugins are installed and enabled. Throws a {@link RuntimeException}
   * if any are missing.
   */
  public static void runCheck(String[] requiredPlugins) {
    for (String pluginId : requiredPlugins) {
      if (pluginEnabled(pluginId)) {
        continue;
      }
      String msg =
          String.format(
              "Required plugin '%s' is not %s",
              pluginId, pluginInstalled(pluginId) ? "enabled" : "available");
      throw new RuntimeException(msg);
    }
  }

  private static boolean pluginEnabled(String pluginId) {
    IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId(pluginId));
    return descriptor != null && descriptor.isEnabled();
  }

  private static boolean pluginInstalled(String pluginId) {
    return PluginManager.getPlugin(PluginId.getId(pluginId)) != null;
  }
}
