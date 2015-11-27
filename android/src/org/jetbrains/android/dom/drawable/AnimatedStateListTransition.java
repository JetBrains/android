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
package org.jetbrains.android.dom.drawable;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.Styleable;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

@DefinesXml
@Styleable("AnimatedStateListDrawableTransition")
public interface AnimatedStateListTransition extends DrawableDomElement {
  AnimationList getAnimationList();

  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("id")
  @Attribute("fromId") // without this it's interpreted as "from-id"
  @Required
  AndroidAttributeValue<ResourceValue> getFromId();

  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("id")
  @Attribute("toId") // without this it's interpreted as "to-id"
  @Required
  AndroidAttributeValue<ResourceValue> getToId();

  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("drawable")
  AndroidAttributeValue<ResourceValue> getDrawable();
}
