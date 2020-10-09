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
package org.jetbrains.android.dom.manifest;

import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.converters.values.BooleanValueConverter;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.Styleable;
import org.jetbrains.android.dom.converters.AndroidUsesFeatureNameConverter;

@Styleable("AndroidManifestUsesFeature")
public interface UsesFeature extends ManifestElementWithName {
  String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";

  @Attribute("name")
  @Convert(AndroidUsesFeatureNameConverter.class)
  AndroidAttributeValue<String> getName();

  @Attribute("required")
  @Convert(BooleanValueConverter.class)
  AndroidAttributeValue<Boolean> getRequired();
}
