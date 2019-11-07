/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import java.util.Map;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO;

/**
 * Base class for all the {@link GradleDslElement}s that represent blocks like android, productFlavors, buildTypes etc.
 */
public class GradleDslBlockElement extends GradlePropertiesDslElement {
  protected GradleDslBlockElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  protected void maybeRenameElement(@NotNull GradleDslElement element) {
    String name = element.getName();
    Map<Pair<String, Integer>,Pair<String,SemanticsDescription>> nameMapper = getExternalToModelMap(element.getDslFile().getParser());
    if (element.shouldUseAssignment()) {
      Pair<String, SemanticsDescription> value = nameMapper.get(new Pair<>(name, (Integer) null));
      if (value != null) {
        SemanticsDescription semantics = value.getSecond();
        // we are maybe-renaming a property involved in an assignment, which only makes sense if the property has a writer (i.e.
        // it is a property and not a read-only VAL)
        if (semantics == VAR || semantics == VWO) {
          String newName = value.getFirst();
          // we rename the GradleNameElement, and not the element directly, because this renaming is not about renaming the property
          // but about providing a canonical model name for a thing.
          element.getNameElement().canonize(newName); // NOTYPO
        }
      }
    }
    else {
      for (Map.Entry<Pair<String, Integer>,Pair<String, SemanticsDescription>> entry : nameMapper.entrySet()) {
        String entryName = entry.getKey().getFirst();
        Integer arity = entry.getKey().getSecond();
        // TODO(xof): distinguish between semantics based on expressed arities (and then do something different based on those semantics)
        //noinspection NumberEquality (property is null)
        if (entryName.equals(name) && arity != property) {
          String newName = entry.getValue().getFirst();
          element.getNameElement().canonize(newName); // NOTYPO
          return;
        }
      }
    }
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(element.getFullName())) {
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this);
        super.addParsedElement(applyDslElement);
      }
      applyDslElement.addParsedElement(element);
      return;
    }
    super.addParsedElement(element);
  }
}
