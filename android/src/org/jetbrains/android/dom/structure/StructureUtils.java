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
package org.jetbrains.android.dom.structure;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Various utilities methods for structure view builders
 */
public final class StructureUtils {
  private StructureUtils() {
  }

  /**
   * {@link DomElement#acceptChildren(DomElementVisitor)} doesn't iterate over children in order
   * they appear in the XML file. This is a helper method that iterates over subtags of given
   * element in the order of appearance and accepts a visitor for each one of them that correspond
   * to a DOM element
   */
  public static void acceptChildrenInOrder(@NotNull DomElement element, @NotNull DomElementVisitor visitor) {
    final XmlTag tag = element.getXmlTag();
    if (tag == null) {
      return;
    }
    for (XmlTag xmlTag : tag.getSubTags()) {
      final DomElement child = element.getManager().getDomElement(xmlTag);
      if (child == null) {
        return;
      }

      child.accept(visitor);
    }
  }
}
