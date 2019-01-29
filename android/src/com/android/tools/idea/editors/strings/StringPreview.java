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
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.parsers.DomPullParser;
import com.android.tools.swing.layoutlib.AndroidPreviewPanel;
import com.intellij.openapi.module.Module;
import com.intellij.xml.CommonXmlStrings;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import android.widget.TextView;
import android.text.Html;

import javax.swing.*;
import java.util.List;

public class StringPreview {

  private final AndroidPreviewPanel myPreview;

  public StringPreview(@NotNull Module module) {
    Configuration config = ThemeEditorUtils.getConfigurationForModule(module);

    myPreview = new AndroidPreviewPanel(config);

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;

    Element textView = document.createElement(SdkConstants.TEXT_VIEW);
    Attr attr = document.createAttributeNS(SdkConstants.XMLNS_URI, SdkConstants.XMLNS_ANDROID);
    attr.setValue(SdkConstants.ANDROID_URI);
    textView.getAttributes().setNamedItemNS(attr);

    textView.setAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH, SdkConstants.VALUE_FILL_PARENT);
    textView.setAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT, SdkConstants.VALUE_FILL_PARENT);
    textView.setAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND, "?android:attr/colorBackground");

    document.appendChild(textView);
    myPreview.setDocument(document);
  }

  public JComponent getComponent() {
    return myPreview;
  }

  public void setText(@NotNull String text) {
    if (text.startsWith(CommonXmlStrings.CDATA_START) && text.endsWith(CommonXmlStrings.CDATA_END)) {
      text = text.substring(CommonXmlStrings.CDATA_START.length(), text.length() - CommonXmlStrings.CDATA_END.length());
    }
    List<ViewInfo> views = myPreview.getRootViews();
    if (!views.isEmpty() && views.get(0).getViewObject() instanceof TextView) {
      TextView textView = (TextView)views.get(0).getViewObject();
      textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
      myPreview.revalidate();
      myPreview.repaint();
    }
  }
}
