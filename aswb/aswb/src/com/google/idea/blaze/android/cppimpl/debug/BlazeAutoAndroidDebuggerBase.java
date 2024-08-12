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
package com.google.idea.blaze.android.cppimpl.debug;

import com.android.tools.ndk.run.editor.AutoAndroidDebugger;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/** Attached either java or native debugger depending on if native debugging is enabled. */
public abstract class BlazeAutoAndroidDebuggerBase extends AutoAndroidDebugger {
  public static final String ID = Blaze.defaultBuildSystemName();
  protected static final Logger log = Logger.getInstance(BlazeAutoAndroidDebugger.class);
  protected final BlazeNativeAndroidDebugger nativeDebugger = new BlazeNativeAndroidDebugger();

  @Override
  protected boolean isNativeProject(Project project) {
    return isNativeCodeInProject(project);
  }

  public static boolean isNativeCodeInProject(Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return "Detect Automatically";
  }

  @Override
  public boolean supportsProject(Project project) {
    return Blaze.isBlazeProject(project);
  }

  @Override
  public boolean shouldBeDefault() {
    // TODO b/134190522 Set as default again when blaze native debugger works.
    return false;
  }
}
