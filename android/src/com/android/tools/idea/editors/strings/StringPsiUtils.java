/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.SdkConstants;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.tools.idea.rendering.Locale;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StringPsiUtils {
  private StringPsiUtils() {
  }

  @Nullable
  static XmlFile getDefaultStringResourceFile(@NotNull Project project, @NotNull StringResourceKey key) {
    VirtualFile directory = key.getDirectory();
    return directory == null ? null : StringsWriteUtils.getStringResourceFile(project, directory, null);
  }

  @Nullable
  static XmlFile getStringResourceFile(@NotNull Project project, @NotNull StringResourceKey key, @NotNull Locale locale) {
    VirtualFile directory = key.getDirectory();
    return directory == null ? null : StringsWriteUtils.getStringResourceFile(project, directory, locale);
  }

  static void addString(@NotNull XmlFile file, @NotNull StringResourceKey key, @NotNull String value) {
    addString(file, key, true, value);
  }

  static void addString(@NotNull XmlFile file, @NotNull StringResourceKey key, boolean translatable, @NotNull String value) {
    XmlTag resources = file.getRootTag();

    if (resources == null) {
      return;
    }

    XmlTag string = resources.createChildTag(SdkConstants.TAG_STRING, resources.getNamespace(), escape(value), false);
    string.setAttribute(SdkConstants.ATTR_NAME, key.getName());

    if (!translatable) {
      string.setAttribute(SdkConstants.ATTR_TRANSLATABLE, Boolean.FALSE.toString());
    }

    resources.addSubTag(string, false);
  }

  @NotNull
  private static String escape(@NotNull String value) {
    try {
      return ValueXmlHelper.escapeResourceStringAsXml(value);
    }
    catch (IllegalArgumentException exception) {
      // The invalid XML will be underlined in the editor
      return value;
    }
  }
}
