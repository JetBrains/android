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
package com.google.idea.blaze.android.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/** Android-specific user settings. */
@State(name = "BlazeAndroidUserSettings", storages = @Storage("blaze.android.user.settings.xml"))
public class BlazeAndroidUserSettings
    implements PersistentStateComponent<BlazeAndroidUserSettings> {

  private boolean useLayoutEditor = false;

  public static BlazeAndroidUserSettings getInstance() {
    return ApplicationManager.getApplication().getService(BlazeAndroidUserSettings.class);
  }

  @Override
  @NotNull
  public BlazeAndroidUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(BlazeAndroidUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public boolean getUseLayoutEditor() {
    return useLayoutEditor;
  }

  public void setUseLayoutEditor(boolean useLayoutEditor) {
    this.useLayoutEditor = useLayoutEditor;
  }
}
