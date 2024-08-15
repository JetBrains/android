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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.rules.ExternalResource;

/**
 * Test rule to disable a specified list of plugins during a test class.
 *
 * <p>Disabled plugins only take effect when initializing the {@link Application}, which can only
 * happen once per integration test target.
 *
 * <p>As such, all users of this test rule must be in their own `java_test` target.
 */
public class DisablePluginsTestRule extends ExternalResource {

  private final ImmutableList<String> disabledPluginIds;

  public DisablePluginsTestRule(ImmutableList<String> disabledPluginIds) {
    this.disabledPluginIds = Preconditions.checkNotNull(disabledPluginIds);
  }

  @Override
  protected void before() throws Throwable {
    if (ApplicationManager.getApplication() != null) {
      // We may be able to relax this constraint if we check that the desired
      // disabledPluginIds matches the existing value of ourDisabledPlugins.
      throw new RuntimeException("Cannot disable plugins; they've already been loaded.");
    }
    disabledPluginIds.forEach(PluginManagerCore::disablePlugin);
  }

  @Override
  protected void after() {
    // no point resetting the list of disabled plugins to its prior value -- subsequent tests can't
    // reinitialize the {@link Application} anyway.
  }
}
