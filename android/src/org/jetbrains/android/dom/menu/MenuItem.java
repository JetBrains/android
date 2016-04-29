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

package org.jetbrains.android.dom.menu;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.ExtendClass;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidResourceType;
import org.jetbrains.android.dom.Styleable;
import org.jetbrains.android.dom.converters.CompleteNonModuleClass;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.resources.ResourceValue;

@Styleable("MenuItem")
public interface MenuItem extends MenuElement {
  Menu getMenu();

  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("id")
  AndroidAttributeValue<ResourceValue> getId();

  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("string")
  AndroidAttributeValue<ResourceValue> getTitle();

  @Attribute("titleCondensed")
  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("string")
  AndroidAttributeValue<ResourceValue> getTitleCondensed();

  @Attribute("actionLayout")
  @Convert(ResourceReferenceConverter.class)
  @AndroidResourceType("layout")
  AndroidAttributeValue<ResourceValue> getActionLayout();

  @Attribute("actionViewClass")
  @Convert(PackageClassConverter.class)
  @ExtendClass(value = "android.view.View",
               instantiatable = false,
               allowAbstract = false,
               allowInterface = false,
               allowEnum = false)
  AndroidAttributeValue<PsiClass> getActionViewClass();

  @Attribute("actionProviderClass")
  @Convert(PackageClassConverter.class)
  @CompleteNonModuleClass
  @ExtendClass(value = "android.view.ActionProvider",
               instantiatable = false,
               allowAbstract = false,
               allowInterface = false,
               allowEnum = false)
  AndroidAttributeValue<PsiClass> getActionProviderClass();
}
