/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.navigator.packageview;

/**
 * {@link AndroidPackageViewSettings} contains a few settings that could in the future be exposed as a setting in the navigator view
 * dropdown menu. Currently IDEA doesn't allow those settings ({@link com.intellij.ide.projectView.ViewSettings}) to be extended by plugins.
 */
public class AndroidPackageViewSettings {
  /**
   * Flag indicating whether only the sources for the currently active variant should be displayed. Currently, this is set to true to
   * match the default IntelliJ package view behavior.
   */
  public static final boolean SHOW_CURRENT_VARIANT_ONLY = true;

  /**
   * Flag indicating whether the package view should also display files from other libraries/modules into the display tree. By default,
   * the package view is meant to only show package structure, and ignores the actual locations from which the sources come.
   */
  public static final boolean INCLUDE_SOURCES_FROM_LIBRARIES = true;
}
