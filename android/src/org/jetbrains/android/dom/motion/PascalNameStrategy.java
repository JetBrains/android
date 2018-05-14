/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.dom.motion;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;
import com.intellij.util.xml.DomNameStrategy;

import java.util.Arrays;

public class PascalNameStrategy extends DomNameStrategy {
  public static final Function<String,String> CAPITALIZE_FUNCTION = s -> StringUtil.capitalize(s);

  @Override
  public final String convertName(String propertyName) {
    return StringUtil.capitalize(propertyName);
  }

  @Override
  public final String splitIntoWords(final String tagName) {
    return StringUtil.join(Arrays.asList(NameUtil.nameToWords(tagName)), CAPITALIZE_FUNCTION, " ");
  }
}
