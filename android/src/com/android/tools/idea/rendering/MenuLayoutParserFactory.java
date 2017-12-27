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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.XmlUtils;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.rendering.LayoutPullParsers.createEmptyParser;

/**
 * Renderer which creates a preview of menus and renders them into a layout XML element hierarchy.
 * This creates an empty FrameLayout to render and adds the menu to {@link ActionBarHandler}
 * <p>
 * See
 * http://developer.android.com/guide/topics/ui/menus.html
 * http://developer.android.com/guide/topics/resources/menu-resource.html
 */
public class MenuLayoutParserFactory {
  @NotNull
  private static final String FRAME_LAYOUT_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<FrameLayout\n" +
      "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"match_parent\" />\n";

  @NotNull
  private final RenderTask myRenderTask;

  public MenuLayoutParserFactory(@NotNull RenderTask renderTask) {
    assert renderTask.supportsCapability(Features.ACTION_BAR) : "Action Bar not supported.";
    myRenderTask = renderTask;
  }

  @NotNull
  public ILayoutPullParser render() {
    Document frameLayoutDocument = XmlUtils.parseDocumentSilently(FRAME_LAYOUT_XML, true);
    if (frameLayoutDocument == null) {
      return createEmptyParser();
    }
    XmlFile psiFile = myRenderTask.getPsiFile();
    if (psiFile == null) {
      throw new IllegalStateException("RenderTask should have PsiFile to render menu files");
    }
    String resourceName = ResourceHelper.getResourceName(psiFile);
    ActionBarHandler actionBarHandler = myRenderTask.getLayoutlibCallback().getActionBarHandler();
    if (actionBarHandler != null) {
      actionBarHandler.setMenuIdNames(Collections.singletonList(resourceName));
    }
    Map<Element, Object> viewCookies = new HashMap<>();
    return new DomPullParser(frameLayoutDocument.getDocumentElement()).setViewCookies(viewCookies);
  }
}
