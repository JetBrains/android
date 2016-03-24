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
package com.android.tools.idea.editors.theme;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STYLE;

public class ThemeEditorProvider implements FileEditorProvider, DumbAware {
  public final static boolean THEME_EDITOR_ENABLE = true;

  private final static String THEME_NAME = "theme-name";
  private final static String STYLE_NAME = "style-name";
  private final static String MODULE_NAME = "module-name";
  private final static String PROPORTION = "proportion";

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    if (!THEME_EDITOR_ENABLE) {
      return false;
    }

    return file instanceof ThemeEditorVirtualFile;
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_THEME_EDITOR, UsageTracker.ACTION_THEME_EDITOR_OPEN, null, null);
    return new ThemeEditor(project, file);
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    String themeName = sourceElement.getAttributeValue(THEME_NAME);
    String styleName = sourceElement.getAttributeValue(STYLE_NAME);
    String moduleName = sourceElement.getAttributeValue(MODULE_NAME);

    Float proportion = null;
    try {
      String proportionString = sourceElement.getAttributeValue(PROPORTION);
      if (proportionString != null) {
        proportion = Float.parseFloat(proportionString);
      }
    } catch (NumberFormatException e) {
      // ignore
    }

    return new ThemeEditorState(themeName, styleName, proportion, moduleName);
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    if (!(state instanceof ThemeEditorState)) {
      return;
    }

    ThemeEditorState editorState = (ThemeEditorState) state;

    setAttributeIfNotNull(targetElement, THEME_NAME, editorState.getThemeName());
    setAttributeIfNotNull(targetElement, STYLE_NAME, editorState.getSubStyleName());
    setAttributeIfNotNull(targetElement, MODULE_NAME, editorState.getModuleName());

    targetElement.setAttribute(PROPORTION, Float.toString(editorState.getProportion()));
  }

  private static void setAttributeIfNotNull(@NotNull Element targetElement, String paramName, String paramValue) {
    if (paramValue != null) {
      targetElement.setAttribute(paramName, paramValue);
    }
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "themeEditor";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  public static boolean isAndroidTheme(@Nullable PsiFile file) {
    if (!THEME_EDITOR_ENABLE) {
      return false;
    }

    if (ResourceHelper.getFolderType(file) != ResourceFolderType.VALUES || !(file instanceof XmlFile)) {
      return false;
    }

    final String name = file.getName();
    if (name.equals("strings.xml")) {
      // Optimization: strings.xml can contain a lot of tags and has a separate editor,
      // so we just skip all checks and return false
      return false;
    } else if (name.equals("styles.xml")) {
      // styles.xml is a conventional name for XML resource file with style definitions.
      // So, we are going to show notification for styles.xml even if there are no style
      // definitions in that file yet
      return true;
    }

    XmlTag rootTag = ((XmlFile)file).getRootTag();
    if (rootTag == null || !rootTag.getName().equals(TAG_RESOURCES)) {
      return false;
    }

    for (XmlTag child : rootTag.getSubTags()) {
      if (child.getName().equals(TAG_STYLE)) {
        return true;
      }
    }

    return false;
  }
}
