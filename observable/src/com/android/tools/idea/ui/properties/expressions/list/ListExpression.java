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
import com.android.tools.idea.ui.properties.expressions.Expression;

import java.util.List;

/**
 * Base class for List expressions, converting a source {@link ObservableList} into a target
 * {@link List}.
 * @param <S> The type of the elements in the source list
 * @param <D> The type of the elements in the final destination list
 */
public abstract class ListExpression<S, D> extends Expression<List<? extends D>> {
  protected ListExpression(ObservableList<? extends S> sourceList) {
    super(sourceList);
  }
}
