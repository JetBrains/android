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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.intellij.openapi.util.text.StringUtil.isQuotedString;

/**
 * FakeElement representing part of an artifact in compact notation. This allows us to represent each component as its own property.
 * The actual element in the tree that the compact notation is represented by is a {@link GradleDslLiteral} with a value
 * such as 'com.android.support:appcompat-v7:22.1.1'. However for the model api we need to be able to treat each component as
 * its own property to provide a consistency with the map based form.
 */
public class FakeArtifactElement extends FakeElement {
  @NotNull private final Function<ArtifactDependencySpec, String> myGetter;
  @NotNull private final BiConsumer<ArtifactDependencySpec, String> mySetter;

  public FakeArtifactElement(@Nullable GradleDslElement parent,
                             @NotNull GradleNameElement name,
                             @NotNull GradleDslExpression originExpression,
                             @NotNull Function<ArtifactDependencySpec, String> getFunc,
                             @NotNull BiConsumer<ArtifactDependencySpec, String> setFunc,
                             boolean canDelete) {
    super(parent, name, originExpression, canDelete);
    myGetter = getFunc;
    mySetter = setFunc;
  }

  @Nullable
  @Override
  protected Object produceValue() {
    GradleDslExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    ArtifactDependencySpec spec = getSpec(resolved);

    if (resolved.getDslFile().getParser().shouldInterpolate(resolved)) {
      String result = myGetter.apply(spec);
      return result == null ? null : iStr(result);
    }
    else {
      return myGetter.apply(spec);
    }
  }

  @Override
  protected void consumeValue(@Nullable Object value) {
    GradleDslExpression resolved = PropertyUtil.resolveElement(myRealExpression);
    ArtifactDependencySpec spec = getSpec(resolved);
    if (spec == null) {
      throw new IllegalArgumentException("Could not create ArtifactDependencySpec from: " + value);
    }
    assert value instanceof String || value instanceof ReferenceTo || value == null;
    String strValue = (value instanceof ReferenceTo) ? "${" + ((ReferenceTo)value).getText() + "}" : (String)value;
    mySetter.accept(spec, strValue);
    if (strValue != null &&
        (value instanceof ReferenceTo ||
         isQuotedString(strValue) ||
         resolved.getDslFile().getParser().shouldInterpolate(resolved))) {
      myRealExpression.setValue(iStr(spec.compactNotation()));
    }
    else {
      myRealExpression.setValue(spec.compactNotation());
    }
  }

  @Nullable
  private static ArtifactDependencySpec getSpec(@NotNull GradleDslExpression element) {
    Object val = element.getUnresolvedValue();
    assert val instanceof String;
    String stringValue = (String)val;
    return ArtifactDependencySpec.create(stringValue);
  }
}
