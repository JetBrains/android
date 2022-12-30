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
package com.android.tools.idea.common.util;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.AUTO_URI;

import com.android.utils.XmlUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public final class XmlTagUtil {
  /**
   * Creates a new XML tag with the given {@code text}. If the given {@code text} is not valid XML, this method will return a
   * {@code TextView} with the passed content as {@code android:text} value.
   */
  @NotNull
  public static XmlTag createTag(@NotNull Project project, @NotNull String text) {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);
    XmlTag tag = null;
    if (XmlUtils.parseDocumentSilently(text, false) != null) {
      try {
        tag = elementFactory.createTagFromText(text);

        setNamespaceUri(tag, ANDROID_NS_NAME, ANDROID_URI);
        setNamespaceUri(tag, APP_PREFIX, AUTO_URI);
      }
      catch (IncorrectOperationException ignore) {
        // Thrown by XmlElementFactory if you try to parse non-valid XML. User might have tried
        // to drop something like plain text -- insert this as a text view instead.
        // However, createTagFromText may not always throw this for invalid XML, so we perform the above parseDocument
        // check first instead.
      }
    }
    if (tag == null) {
      // TODO: remove the need for this case
      tag = elementFactory.createTagFromText("<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                                             " android:text=\"" + XmlUtils.toXmlAttributeValue(text) + "\"" +
                                             " android:layout_width=\"wrap_content\"" +
                                             " android:layout_height=\"wrap_content\"" +
                                             "/>");
    }
    return tag;
  }

  /**
   * Sets the namespace URI in the given {@link XmlTag}
   */
  public static void setNamespaceUri(@NotNull XmlTag tag, @NotNull String prefix, @NotNull String uri) {
    boolean anyMatch = Arrays.stream(tag.getAttributes())
      .anyMatch(attribute -> attribute.getNamespacePrefix().equals(prefix));

    if (anyMatch) {
      tag.setAttribute("xmlns:" + prefix, uri);
    }
  }
}
