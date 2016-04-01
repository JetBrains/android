/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.SdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.intellij.lang.annotations.Language;

import static com.android.SdkConstants.COORDINATOR_LAYOUT;

/**
 * Handler for the {@code <android.support.design.widget.FloatingActionButton>} widget.
 */
public class FloatingActionButtonHandler extends ImageViewHandler {

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case PREVIEW_ON_PALETTE:
        return String.format("<%1$s\n" +
                             "  android:src=\"%2$s\"\n" +
                             "  android:layout_width=\"wrap_content\"\n" +
                             "  android:layout_height=\"wrap_content\"\n" +
                             "  android:clickable=\"true\"\n" +
                             "  app:elevation=\"0dp\">\n" +
                             "</%1$s>\n", tagName, getSampleImageSrc());
      default:
        return String.format("<%1$s\n" +
                             "  android:src=\"%2$s\"\n" +
                             "  android:layout_width=\"wrap_content\"\n" +
                             "  android:layout_height=\"wrap_content\"\n" +
                             "  android:clickable=\"true\"\n" +
                             "  app:fabSize=\"mini\">\n" +
                             "</%1$s>\n", tagName, getSampleImageSrc());
    }
  }

  @Override
  @NotNull
  public String getSampleImageSrc() {
    // Builtin graphics available since v1:
    return "@android:drawable/ic_input_add"; //$NON-NLS-1$
  }

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    return 0.8;
  }

  @Override
  public boolean acceptsParent(@NotNull NlComponent layout, @NotNull NlComponent newChild) {
    NlComponent appBar = getAppBar(layout);
    if (appBar == null) {
      return super.acceptsParent(layout, newChild);
    }
    return layout == appBar.getParent();
  }

  @Nullable
  private static NlComponent getAppBar(@NotNull NlComponent component) {
    NlComponent parent = component.getParent();
    while (parent != null) {
      component = parent;
      parent = component.getParent();
    }
    if (!component.getTagName().equals(COORDINATOR_LAYOUT)) {
      return null;
    }
    for (NlComponent child : component.getChildren()) {
      if (child.getTagName().equals(SdkConstants.APP_BAR_LAYOUT)) {
        return child;
      }
    }
    return null;
  }
}
