/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import java.util.HashMap;
import java.util.Map;

class Assoc<K, V> {
  public final Map<K, V> keyToValue;
  public final Map<V, K> valueToKey;

  Assoc(Map<K, V> keyToValue, Map<V, K> valueToKey) {
    this.keyToValue = keyToValue;
    this.valueToKey = valueToKey;
  }

  Assoc() {
    this(new HashMap<K, V>(), new HashMap<V, K>());
  }

  public void add(K key, V value) {
    keyToValue.put(key, value);
    valueToKey.put(value, key);
  }

  public void remove(K key, V value) {
    keyToValue.remove(key);
    valueToKey.remove(value);
  }

  public void clear() {
    keyToValue.clear();
    valueToKey.clear();
  }
}
