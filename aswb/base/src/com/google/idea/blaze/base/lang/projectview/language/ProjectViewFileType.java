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
import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.BlazeIcons;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Blaze project view file type */
public class ProjectViewFileType extends LanguageFileType {

  public static final ProjectViewFileType INSTANCE = new ProjectViewFileType();

  private ProjectViewFileType() {
    super(ProjectViewLanguage.INSTANCE);
  }

  @Override
  public String getName() {
    // Warning: this is conflated with Language.myID in several places...
    // They must be identical.
    return ProjectViewLanguage.INSTANCE.getID();
  }

  @Override
  public String getDescription() {
    return Blaze.defaultBuildSystemName() + " project view files";
  }

  @Override
  public String getDefaultExtension() {
    // Ideally we'd return a build-system specific extension here, but that would require
    // a hack to guess the current project, or choosing either the blaze or bazel
    // extension. Instead don't specify a default extension.
    return "";
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }
}
