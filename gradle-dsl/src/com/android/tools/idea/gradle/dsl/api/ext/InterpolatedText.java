/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an String expression with text injections.
 * We represent an InterpolatedText as a list of {@link InterpolatedTextItem}. Each of which contains either a literal String or a Dsl
 * Reference {@link ReferenceTo}.
 */
public class InterpolatedText {

  @NotNull private List<InterpolatedTextItem> myInterpolatedTextItems;

  public InterpolatedText(List<InterpolatedTextItem> interpolations) {
    myInterpolatedTextItems = new ArrayList<>(interpolations);
  }

  @Override
  @NotNull
  public String toString() {
    StringBuilder expressionBuilder = new StringBuilder();
    for (InterpolatedTextItem interpolatedTextItem : myInterpolatedTextItems) {
      expressionBuilder.append(interpolatedTextItem.toString());
    }
    return expressionBuilder.toString();
  }

  @NotNull
  public List<InterpolatedTextItem> getInterpolationElements() {
    return ImmutableList.copyOf(myInterpolatedTextItems);
  }


  /**
   * Static class to represent an InterpolationItem: each item consists of either a text {@link String} or an injection {@link ReferenceTo}.
   */
  public static final class InterpolatedTextItem {
    private String textItem;
    private ReferenceTo referenceItem;

    public InterpolatedTextItem(String textItem) {
      this.textItem = textItem;
      referenceItem = null;
    }

    public InterpolatedTextItem(ReferenceTo referenceItem) {
      this.textItem = null;
      this.referenceItem = referenceItem;
    }

    @Nullable
    public String getTextItem() {
      return textItem;
    }

    @Nullable
    public ReferenceTo getReferenceItem() {
      return referenceItem;
    }

    @Override
    @NotNull
    public String toString() {
      String representation = "";
      if (textItem != null) {
        representation += textItem;
      }
      if (referenceItem != null) {
        representation += "${" + referenceItem.toString() + "}";
      }
      return representation;
    }
  }
}
