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
package com.android.tools.profilers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * A class which maintains a mapping between a a model class and a generator of its views.
 * This allows for a reactive UI builder that given an object of an unknown class
 * it can create its associated view.
 *
 * To use, register class associations using {@link #bind} and then, later, call
 * {@link #build} to cause an instantiation.
 *
 * @param <P> Any type, usually one that represents a parent view object.
 * @param <M> Any type, usually one that represents some model data.
 * @param <V> Any type, usually one that represents a view associated with a model.
 */
public class ViewBinder<P, M, V> {

  private final Map<Class<? extends M>, BiFunction<? extends P, ? extends M, ? extends V>> myBuilders;

  public ViewBinder() {
    myBuilders = new HashMap<>();
  }

  public <Q extends P, N extends M, W extends V> void bind(Class<N> clazz, BiFunction<Q, N, W> builder) {
    myBuilders.put(clazz, builder);
  }

  public V build(P parentView, M model) {
    Class<?> clazz = model.getClass();
    // By construction the function cast is valid as we know that when we added
    // it to the map, the types were checked at compile time.
    BiFunction<P, M, V> builder = (BiFunction<P, M, V>)myBuilders.get(clazz);
    return builder.apply(parentView, model);
  }
}
