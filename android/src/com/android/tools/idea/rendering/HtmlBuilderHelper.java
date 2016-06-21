/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.utils.HtmlBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Helper methods for using SDK common's {@link HtmlBuilder} in the IDE
 */
public class HtmlBuilderHelper {
  @Nullable
  private static String getIconPath(String relative) {
    // TODO: Find a way to do this more efficiently; not referencing assets but the corresponding
    // AllIcons constants, and loading them into HTML class loader contexts?
    URL resource = AllIcons.class.getClassLoader().getResource(relative);
    try {
      return (resource != null) ? resource.toURI().toURL().toExternalForm() : null;
    }
    catch (MalformedURLException e) {
      return null;
    }
    catch (URISyntaxException e) {
      return null;
    }
  }

  @Nullable
  public static String getCloseIconPath() {
    return getIconPath("/actions/closeNew.png");
  }

  @Nullable
  public static String getTipIconPath() {
    return getIconPath("/actions/createFromUsage.png");
  }

  @Nullable
  public static String getWarningIconPath() {
    return getIconPath("/general/warningDialog.png");
  }

  @Nullable
  public static String getErrorIconPath() {
    return getIconPath("/general/error.png");
  }

  @Nullable
  public static String getRefreshIconPath() {
    return getIconPath("/actions/refresh.png");
  }

  public static String getHeaderFontColor() {
    // See com.intellij.codeInspection.HtmlComposer.appendHeading
    // (which operates on StringBuffers)
    return UIUtil.isUnderDarcula() ? "#A5C25C" : "#005555";
  }

  /**
   * Adjust the font styles of the given text component, provided it's
   * an HTML styled document, to use fonts from the current IDE scheme.
   * <p>
   * Note: Calling setText() on a component will reset the document styles
   * so you will need to call this method repeatedly after each document
   * replace.
   *
   * @param component the component
   */
  public static void fixFontStyles(@NotNull JTextComponent component) {
    Document document = component.getDocument();
    if (!(document instanceof StyledDocument)) {
      return;
    }

    StyledDocument styledDocument = (StyledDocument)document;
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    Style style = styledDocument.addStyle("active", null);
    StyleConstants.setFontFamily(style, scheme.getEditorFontName());
    StyleConstants.setFontSize(style, scheme.getEditorFontSize());
    styledDocument.setCharacterAttributes(0, document.getLength(), style, false);
  }
}
