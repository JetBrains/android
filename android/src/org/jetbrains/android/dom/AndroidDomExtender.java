/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import java.util.Set;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidDomExtender extends DomExtender<AndroidDomElement> {
  @Override
  public boolean supportsStubs() {
    return false;
  }

  @NotNull
  private static Class getValueClass(@Nullable AttributeFormat format) {
    if (format == null) return String.class;
    switch (format) {
      case BOOLEAN:
        return boolean.class;
      case REFERENCE:
      case DIMENSION:
      case COLOR:
        return ResourceValue.class;
      default:
        return String.class;
    }
  }

  @Override
  public void registerExtensions(@NotNull AndroidDomElement element, @NotNull final DomExtensionsRegistrar registrar) {
    final AndroidFacet facet = AndroidFacet.getInstance(element);

    if (facet == null) {
      return;
    }
    AttributeProcessingUtil.processAttributes(element, facet, true, (xmlName, attrDef, parentStyleableName) -> {
      Set<AttributeFormat> formats = attrDef.getFormats();
      Class valueClass = formats.size() == 1 ? getValueClass(formats.iterator().next()) : String.class;
      registrar.registerAttributeChildExtension(xmlName, GenericAttributeValue.class);
      return registrar.registerGenericAttributeValueChildExtension(xmlName, valueClass);
    });

    SubtagsProcessingUtil.processSubTags(facet, element, true, registrar::registerCollectionChildrenExtension);
  }
}
