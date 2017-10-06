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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;

import static com.android.tools.idea.rendering.LayoutPullParsers.createEmptyParser;

final class NavigationViewMenuLayoutPullParserFactory {
  private final RenderTask myTask;

  NavigationViewMenuLayoutPullParserFactory(@NotNull RenderTask task) {
    myTask = task;
  }

  @NotNull
  ILayoutPullParser render() {
    PsiFile file = myTask.getPsiFile();
    assert file != null;

    @Language("XML")
    String xml = "<android.support.design.widget.NavigationView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                 "    android:layout_width=\"wrap_content\"\n" +
                 "    android:layout_height=\"match_parent\"\n" +
                 "    app:menu=\"@menu/" + ResourceHelper.getResourceName(file) + "\" />\n";

    Document document = XmlUtils.parseDocumentSilently(xml, true);
    return document == null ? createEmptyParser() : new DomPullParser(document.getDocumentElement());
  }
}
