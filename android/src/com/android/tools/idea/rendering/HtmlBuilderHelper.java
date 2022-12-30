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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.StartupUiUtil;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.jetbrains.annotations.Nullable;

/**
 * Helper methods for using SDK common's {@link HtmlBuilder} in the IDE
 */
public final class HtmlBuilderHelper {
  @Nullable
  private static String getIconPath(String relative) {
    // TODO: Find a way to do this more efficiently; not referencing assets but the corresponding
    // AllIcons constants, and loading them into HTML class loader contexts?
    URL resource = AllIcons.class.getClassLoader().getResource(StringUtil.trimStart(relative, "/"));
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
  public static String getTipIconPath() {
    return getIconPath("general/informationDialog.png");
  }

  @Nullable
  public static String getErrorIconPath() {
    return getIconPath("general/error.png");
  }

  @Nullable
  public static String getRefreshIconPath() {
    return getIconPath("actions/refresh.png");
  }

  public static String getHeaderFontColor() {
    // See com.intellij.codeInspection.HtmlComposer.appendHeading
    // (which operates on StringBuffers)
    return StartupUiUtil.isUnderDarcula() ? "#A5C25C" : "#005555";
  }
}
