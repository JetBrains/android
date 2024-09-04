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
package com.google.idea.blaze.base.lang.projectview.formatting;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import javax.annotation.Nullable;

/** Allows blazeproject-specific code style settings */
public class ProjectViewCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {

  @Override
  public Language getLanguage() {
    return ProjectViewLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(SettingsType settingsType) {
    return "";
  }

  @Nullable
  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings =
        new CommonCodeStyleSettings(ProjectViewLanguage.INSTANCE);
    defaultSettings.LINE_COMMENT_AT_FIRST_COLUMN = false;
    defaultSettings.LINE_COMMENT_ADD_SPACE = true;
    CommonCodeStyleSettings.IndentOptions indentOptions = defaultSettings.initIndentOptions();
    indentOptions.TAB_SIZE = 2;
    indentOptions.INDENT_SIZE = 2;
    indentOptions.CONTINUATION_INDENT_SIZE = 2;
    return defaultSettings;
  }
}
