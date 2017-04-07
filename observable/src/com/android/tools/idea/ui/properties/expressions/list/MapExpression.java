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
package com.android.tools.idea.ui.properties.expressions.list;

import com.android.tools.idea.ui.properties.collections.ObservableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An expression which maps one list to another.
 */
public abstract class MapExpression<S, D> extends ListExpression<S, D> {
  private final ObservableList<? extends S> mySourceList;

  protected MapExpression(ObservableList<? extends S> sourceList) {
    super(sourceList);

    mySourceList = sourceList;
  }

  @NotNull
  @Override
  public final List<? extends D> get() {
    List<D> mappedList = Lists.newArrayListWithCapacity(mySourceList.size());
    for (S srcElement : mySourceList) {
      mappedList.add(transform(srcElement));
    }
    return mappedList;
  }

  @NotNull
  protected abstract D transform(@NotNull S srcElement);
}
