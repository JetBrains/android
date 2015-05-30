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
package com.android.tools.idea.structure.developerServices;

import com.android.tools.idea.ui.properties.Observable;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;

/**
 * A generic mapping of strings to {@link Observable} values and {@link Runnable} actions. Adding
 * entries here allows them to be referenced within service.xml.
 * <p/>
 * As a convention, you should organize your keys into namespaces, using periods to delimit them.
 * For example, instead of "countOfAnalyticsProjects" and "countOfAdsProjects", prefer instead
 * "analytics.projects.count" and "ads.projects.count"
 */
public final class ServiceContext {
  private final Map<String, Observable> myValues = Maps.newHashMap();
  private final Map<String, Runnable> myActions = Maps.newHashMap();

  public void putValue(@NotNull String key, @NotNull Observable observable) {
    myValues.put(key, observable);
  }

  public void putAction(@NotNull String key, @NotNull Runnable action) {
    myActions.put(key, action);
  }

  @NotNull
  public Observable getValue(@NotNull String key) {
    Observable value = myValues.get(key);
    if (value == null) {
      throw new IllegalArgumentException(String.format("Service context: Value \"%1$s\" not found.", key));
    }
    return value;
  }

  @NotNull
  public Runnable getAction(@NotNull String key) {
    Runnable action = myActions.get(key);
    if (action == null) {
      throw new IllegalArgumentException(String.format("Service context: Action \"%1$s\" not found.", key));
    }
    return action;
  }

  /**
   * Converts this service context, which is itslef backed by a flat map, into a hierarchical map,
   * a data structure that freemarker works well with.
   * <p/>
   * For example, a service context with the values "parent.child1" and "parent.child2" will return
   * a map that is nested like so
   * <pre>
   * "parent"
   *   "child1"
   *   "child2"
   * </pre>
   */
  @NotNull
  public Map<String, Object> toValueMap() {
    Map<String, Object> valueMap = Maps.newHashMap();
    Splitter splitter = Splitter.on('.');
    for (String key : myValues.keySet()) {
      Observable value = getValue(key);

      Map<String, Object> currLevel = valueMap;

      Iterator<String> keyParts = splitter.split(key).iterator();
      while (keyParts.hasNext()) {
        String keyPart = keyParts.next();
        if (keyParts.hasNext()) {
          if (currLevel.containsKey(keyPart)) {
            currLevel = (Map<String, Object>)currLevel.get(keyPart);
          }
          else {
            Map<String, Object> nextLevel = Maps.newHashMap();
            currLevel.put(keyPart, nextLevel);
            currLevel = nextLevel;
          }
        }
        else {
          // We're the last part of the key
          currLevel.put(keyPart, value);
        }
      }
    }

    return valueMap;
  }
}
