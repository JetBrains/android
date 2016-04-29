/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.dom.color;

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.Styleable;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

/**
 * Framework code: ColorStateList#inflate
 * <p/>
 * Framework uses styleable ColorStateListItem for providing "color" and "alpha" attributes,
 * but it's not mentioned here in @Styleable annotation, because "color" attribute is marked
 * as required and thus will be inserted automatically on tag completion. Right now there is
 * no way to provide the same information for attributes coming from @Styleable annotation,
 * and using this styleable when having {@link #getColor()} method leads to two autocompletion
 * results with "android:color".
 * <p/>
 * TODO: implement a way to mark some attributes as required and use it here for "color" attribute
 */
@Styleable({"DrawableStates"})
public interface ColorStateListItem extends ColorDomElement {
  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("color")
  @Required
  AndroidAttributeValue<ResourceValue> getColor();
}
