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

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.GenericAttributeValue;
import java.util.List;

@DefinesXml
public interface Data extends DataBindingElement {
  List<Variable> getVariables();

  List<Import> getImports();

  /**
   * The value of this attribute directs data binding to generate a binding class with a custom
   * name / path.
   * <p>
   * See also:
   * <a href="https://developer.android.com/topic/libraries/data-binding/generated-binding#custom_binding_class_names">related docs</a>
   */
  @Attribute("class")
  GenericAttributeValue<String> getCustomBindingClass();
}
