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
package org.jetbrains.android.dom.layout;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.android.dom.converters.DataBindingConverter;
import org.jetbrains.android.dom.converters.PackageClassConverter;

@DefinesXml
public interface Import extends LayoutElement, DataBindingElement {
  @Attribute("type")
  @Required
  @Convert(value = DataBindingConverter.class, soft = false)
  GenericAttributeValue<PsiClass> getType();

  /**
   * Optional alias this import should be renamed to in this layout.
   * <p/>
   * If absent, the import behaves like it does in Java. To get the name this import will use in the layout, use
   * {@link AndroidLayoutUtil#getAlias(Import)}.
   */
  @Attribute("alias")
  GenericAttributeValue<String> getAlias();
}
