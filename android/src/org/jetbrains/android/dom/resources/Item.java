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
package org.jetbrains.android.dom.resources;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.resources.ResourceType;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.converters.FormatConverter;
import org.jetbrains.android.dom.converters.QuietResourceReferenceConverter;
import org.jetbrains.android.dom.converters.StaticEnumConverter;

@Convert(QuietResourceReferenceConverter.class)
public interface Item extends ResourceElement {
  class TypeConverter extends StaticEnumConverter {
    public TypeConverter() {
      // TODO(b/110344051): type="styleable" crashes aapt2.
      super(ResourceType.getClassNames()
              .stream()
              .filter(type -> !type.equals("styleable"))
              .collect(Collectors.toList()));
    }
  }

  @Required
  @Convert(TypeConverter.class)
  GenericAttributeValue<String> getType();

  @Convert(FormatConverter.class)
  GenericAttributeValue<List<AttributeFormat>> getFormat();
}
