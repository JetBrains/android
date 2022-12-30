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
package com.android.tools.idea.rendering.parsers;

import static com.android.AndroidXConstants.NAVIGATION_VIEW;
import static com.android.tools.idea.rendering.parsers.LayoutPullParsers.createEmptyParser;
import static com.android.tools.idea.util.DependencyManagementUtil.mapAndroidxName;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ActionBarHandler;
import com.android.tools.idea.rendering.LayoutlibCallbackImpl;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import java.util.Collections;
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
final class MenuLayoutParserFactory {
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
  public static ILayoutPullParser create(@NotNull PsiFile psiFile, @NotNull LayoutlibCallbackImpl layoutlibCallback) {
    Document frameLayoutDocument = XmlUtils.parseDocumentSilently(FRAME_LAYOUT_XML, true);
    if (frameLayoutDocument == null) {
      return createEmptyParser();
    }
    ActionBarHandler actionBarHandler = layoutlibCallback.getActionBarHandler();
    if (actionBarHandler != null) {
      ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(psiFile);
      if (repositoryManager != null) {
        ResourceReference menuResource =
            new ResourceReference(repositoryManager.getNamespace(), ResourceType.MENU,
                                  SdkUtils.fileNameToResourceName(psiFile.getName()));
        actionBarHandler.setMenuIds(Collections.singletonList(menuResource));
      }
    }
    return DomPullParser.createFromDocument(frameLayoutDocument, Collections.emptyMap());
  }

  @NotNull
  public static ILayoutPullParser createInNavigationView(@NotNull PsiFile file) {
    String navViewTag = mapAndroidxName(ModuleUtilCore.findModuleForPsiElement(file), NAVIGATION_VIEW);
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
