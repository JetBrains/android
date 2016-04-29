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

import com.google.common.collect.Lists;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import net.jcip.annotations.NotThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Composite version of {@link ResolvingConverter}.
 */
public class CompositeConverter extends ResolvingConverter<String> {
  private final List<ResolvingConverter<String>> myConverters;

  /**
   * Constructor is not public, use {@link Builder} to create an instance of {@link ResolvingConverter}
   * that might (or might not) be a {@link CompositeConverter}
   */
  CompositeConverter(List<ResolvingConverter<String>> converters) {
    myConverters = converters;
  }

  @Override
  @NotNull
  public Collection<String> getVariants(ConvertContext context) {
    List<String> variants = Lists.newArrayList();
    for (ResolvingConverter<String> converter : myConverters) {
      variants.addAll(converter.getVariants(context));
    }
    return variants;
  }

  @Override
  public String fromString(@Nullable String s, ConvertContext context) {
    // Returning non-null value from this method means that the value is valid.
    // To ensure that, we check all the containing converters to make sure at least
    // one of those recognized passed value.
    for (ResolvingConverter<String> converter : myConverters) {
      String result = converter.fromString(s, context);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }

  /**
   * Helper class for building a composite {@link ResolvingConverter}.
   */
  @NotThreadSafe
  public static class Builder {
    /**
     * List that stores converters added via {@link #addConverter(ResolvingConverter)}.
     * Method {@link #build()} passes it to CompositeConverter directly without defensive
     * copying, which is justified by ensuring it would be called only once.
     */
    private final List<ResolvingConverter<String>> myConverters = Lists.newArrayList();
    private boolean myIsBuilt = false;

    private void assertNotBuilt() {
      if (myIsBuilt) {
        throw new IllegalStateException("CompositeConverterBuilder shouldn't be used after .build() is called");
      }
    }

    public void addConverter(@NotNull ResolvingConverter<String> converter) {
      assertNotBuilt();
      myConverters.add(converter);
    }

    /**
     * Build a composite {@link ResolvingConverter} which uses all added converters to this builder.
     * Has an optimization for cases when only zero or one converters were added and thus no
     * composite wrapper is required.
     * <p/>
     * After calling this method instance CompositeConverterBuilder shouldn't be used,
     * and invocations of all methods will throw an {@link IllegalStateException}
     */
    @Nullable
    public ResolvingConverter<String> build() {
      assertNotBuilt();
      myIsBuilt = true;

      switch (myConverters.size()) {
        case 0:
          return null;
        case 1:
          return myConverters.get(0);
        default:
          return new CompositeConverter(myConverters);
      }
    }
  }
}
