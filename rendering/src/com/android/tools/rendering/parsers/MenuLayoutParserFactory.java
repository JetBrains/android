/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.rendering.parsers;

import static com.android.AndroidXConstants.NAVIGATION_VIEW;
import static com.android.tools.rendering.parsers.LayoutPullParsers.createEmptyParser;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.tools.module.ModuleDependencies;
import com.android.tools.rendering.LayoutlibCallbackImpl;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;

/**
 * Renderer which creates a preview of menus and renders them into a layout XML element hierarchy.
 * This creates an empty FrameLayout to render and adds the menu to {@link ActionBarHandler}
 * <p>
 * See
 * http://developer.android.com/guide/topics/ui/menus.html
 * http://developer.android.com/guide/topics/resources/menu-resource.html
 */
class MenuLayoutParserFactory {
  @NotNull
  private static final String FRAME_LAYOUT_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<FrameLayout\n" +
      "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\" />\n";

  private MenuLayoutParserFactory() {
  }


  @NotNull
  public static ILayoutPullParser create(@NotNull RenderXmlFile psiFile, @NotNull LayoutlibCallbackImpl layoutlibCallback) {
    Document frameLayoutDocument = XmlUtils.parseDocumentSilently(FRAME_LAYOUT_XML, true);
    if (frameLayoutDocument == null) {
      return createEmptyParser();
    }
    layoutlibCallback.setMenuResource(psiFile.getName());
    return DomPullParser.createFromDocument(frameLayoutDocument, false);
  }

  @NotNull
  public static ILayoutPullParser createInNavigationView(@NotNull RenderXmlFile file, @NotNull ModuleDependencies dependencies) {
    String navViewTag = dependencies.dependsOnAndroidx() ? NAVIGATION_VIEW.newName() : NAVIGATION_VIEW.oldName();
    @Language("XML")
    String xml = "<" + navViewTag + " xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"match_parent\"\n" +
                 "    app:menu=\"@menu/" + SdkUtils.fileNameToResourceName(file.getName()) + "\" />\n";

    Document document = XmlUtils.parseDocumentSilently(xml, true);
    return document == null ? createEmptyParser() : DomPullParser.createFromDocument(document);
  }
}
