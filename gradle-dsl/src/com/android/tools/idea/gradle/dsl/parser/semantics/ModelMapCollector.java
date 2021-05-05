/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.semantics;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import org.jetbrains.annotations.NotNull;

public final class ModelMapCollector {
  public static @NotNull Collector<Object[], ?, ExternalToModelMap> toModelMap() {
    Function<Object[], SurfaceSyntaxDescription> k = data -> {
      String name = (String) data[0];
      Integer arity = (Integer) data[1];
      SemanticsDescription description = (SemanticsDescription) data[3];
      if (Objects.equals(arity, ArityHelper.property)) {
        if (!(description instanceof PropertySemanticsDescription)) {
          throw new RuntimeException("Dsl setup problem for " + name + ": property/semantics description mismatch");
        }
      }
      else {
        if (!(description instanceof MethodSemanticsDescription)) {
          throw new RuntimeException("Dsl setup problem for " + name + ": method/semantics description mismatch");
        }
      }
      return new SurfaceSyntaxDescription(name, arity);
    };
    Function<Object[], ModelEffectDescription> v = data -> {
      String name = (String)data[0];
      Object propertyDescriptionDesignator = data[2];
      SemanticsDescription sd = (SemanticsDescription)data[3];
      ModelPropertyDescription mpd;
      if (propertyDescriptionDesignator instanceof String) {
        mpd = new ModelPropertyDescription((String)propertyDescriptionDesignator);
      }
      else if (propertyDescriptionDesignator instanceof ModelPropertyDescription) {
        mpd = (ModelPropertyDescription)propertyDescriptionDesignator;
      }
      else {
        throw new RuntimeException("Unrecognized model property description designator for " + name + ": " + propertyDescriptionDesignator);
      }
      return new ModelEffectDescription(mpd, sd);
    };
    return Collector.of(
      (Supplier<LinkedHashSet<ExternalToModelMap.Entry>>)LinkedHashSet::new,
      (s, o) -> s.add(new ExternalToModelMap.Entry(k.apply(o), v.apply(o))),
      (a, b) -> { a.addAll(b); return a; },
      ExternalToModelMap::new
    );
  }
}
