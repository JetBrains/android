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
  public static @NotNull Collector<Object[], ?, ExternalToModelMap> toModelMap(ExternalToModelMap... parentMaps) {
    Function<Object[], SurfaceSyntaxDescription> surfaceSyntaxDescriptionGetter = data -> {
      String name = (String)data[0];
      Integer arity = (Integer)data[1];
      SemanticsDescription description = (SemanticsDescription)data[3];
      if (Objects.equals(arity, ArityHelper.property)) {
        if (!(description instanceof PropertySemanticsDescription)) {
          // OK to throw: used in static initializers
          throw new RuntimeException("Dsl setup problem for " + name + ": property/semantics description mismatch");
        }
      }
      else {
        if (!(description instanceof MethodSemanticsDescription)) {
          // OK to throw: used in static initializers
          throw new RuntimeException("Dsl setup problem for " + name + ": method/semantics description mismatch");
        }
      }
      return new SurfaceSyntaxDescription(name, arity);
    };
    Function<Object[], ModelEffectDescription> modelEffectDescriptionGetter = data -> {
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
        // OK to throw: used in static initializers
        throw new RuntimeException("Unrecognized model property description designator for " + name + ": " + propertyDescriptionDesignator);
      }
      VersionConstraint vc = data.length == 4 ? null : (VersionConstraint)data[4];
      return new ModelEffectDescription(mpd, sd, vc);
    };
    Function<Object[], VersionConstraint> versionConstraintGetter = data -> data.length == 4 ? null : (VersionConstraint)data[4];
    return Collector.of(
      (Supplier<LinkedHashSet<ExternalToModelMap.Entry>>)LinkedHashSet::new,
      (s, o) -> s.add(new ExternalToModelMap.Entry(
        surfaceSyntaxDescriptionGetter.apply(o), modelEffectDescriptionGetter.apply(o), versionConstraintGetter.apply(o))
      ),
      (a, b) -> { a.addAll(b); return a; },
      (s) -> {
        for (ExternalToModelMap parentMap : parentMaps) {
          s.addAll(parentMap.getEntrySet());
        }
        return new ExternalToModelMap(s);
      }
    );
  }
}
