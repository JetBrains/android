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
package com.google.idea.blaze.java.settings;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.logging.LoggedSettingsProvider;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.common.util.MorePlatformUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.xmlb.XmlSerializerUtil;

/** Java-specific user settings. */
@State(name = "BlazeJavaUserSettings", storages = @Storage("blaze.java.user.settings.xml"))
public class BlazeJavaUserSettings implements PersistentStateComponent<BlazeJavaUserSettings> {
  private boolean useJarCache = getDefaultJarCacheValue();

  public static BlazeJavaUserSettings getInstance() {
    return ApplicationManager.getApplication().getService(BlazeJavaUserSettings.class);
  }

  private static boolean getDefaultJarCacheValue() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem() == BuildSystemName.Blaze;
  }

  @Override
  public BlazeJavaUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(BlazeJavaUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean getUseJarCache() {
    return allowJarCache() && useJarCache;
  }

  public void setUseJarCache(boolean useJarCache) {
    this.useJarCache = useJarCache;
  }

  static class SettingsLogger implements LoggedSettingsProvider {
    @Override
    public String getNamespace() {
      return "BlazeJavaUserSettings";
    }

    @Override
    public ImmutableMap<String, String> getApplicationSettings() {
      BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();

      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      builder.put("useJarCache", Boolean.toString(settings.useJarCache));
      return builder.build();
    }
  }

  static boolean allowJarCache() {
    return !SystemInfo.isMac
        || BuildSystemProvider.defaultBuildSystem().buildSystem() == BuildSystemName.Bazel
        || MorePlatformUtils.isAndroidStudio();
  }
}
