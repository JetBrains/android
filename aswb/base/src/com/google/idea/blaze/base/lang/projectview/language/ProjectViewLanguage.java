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
package com.google.idea.blaze.base.lang.projectview.language;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.lang.Language;

/** Blaze project file language */
public class ProjectViewLanguage extends Language {

  public static final ProjectViewLanguage INSTANCE = new ProjectViewLanguage();

  private ProjectViewLanguage() {
    super("projectview");
  }

  @Override
  public String getDisplayName() {
    return Blaze.defaultBuildSystemName() + " project view";
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
  }
}
