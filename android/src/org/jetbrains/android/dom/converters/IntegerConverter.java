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
package org.jetbrains.android.dom.converters;

import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class IntegerConverter extends ResolvingConverter<String> {
  public static final IntegerConverter INSTANCE = new IntegerConverter();

  public IntegerConverter() {
  }

  @NotNull
  @Override
  public Collection<String> getVariants(@NotNull ConvertContext context) {
    return Collections.emptyList();
  }

  @Override
  public String fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null || IdeResourcesUtil.isIdDeclaration(s) || IdeResourcesUtil.isIdReference(s)) {
      return s;
    }
    try {
      // Can't use Integer.decode since it will overflow for values like 0xff4861ec.
      // Use Long.decode instead, but reject values larger than what will fit in an integer
      Long l = Long.decode(s);
      if (l >= 0x100000000L || l <= -0xffffffffL) {
        return null;
      }
    }
    catch (NumberFormatException e) {
      return null;
    }
    return s;
  }

  @Override
  public String toString(@Nullable String s, @NotNull ConvertContext context) {
    return s;
  }
}
