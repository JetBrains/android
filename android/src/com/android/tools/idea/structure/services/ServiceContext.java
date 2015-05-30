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
package com.android.tools.idea.structure.services;

import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.Observable;
import com.android.tools.idea.ui.properties.ObservableProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A generic mapping of strings to {@link Observable} values and {@link Runnable} actions. Adding
 * entries here allows them to be referenced within service.xml.
 * <p/>
 * Some of the values added to a context are marked as "watched", using
 * {@link #putWatchedValue(String, ObservableProperty)}. Those values are special, and modifying
 * any of them will mark this context as modified. {@link #snapshot()} and {@link #restore()} will
 * also save and revert watched values - this is useful, for example, if a user is modifying a
 * service but then cancels their changes.
 * <p/>
 * As a convention, you should organize your keys into namespaces, using periods to delimit them.
 * For example, instead of "countOfAnalyticsProjects" and "countOfAdsProjects", prefer instead
 * "analytics.projects.count" and "ads.projects.count"
 */
public final class ServiceContext {
  private final Map<String, Observable> myValues = Maps.newHashMap();
  private final Map<String, Runnable> myActions = Maps.newHashMap();
  private final Map<ObservableProperty, Object> myWatched = new WeakHashMap<ObservableProperty, Object>();
  private final BoolValueProperty myIsInstalled = new BoolValueProperty();
  private final BoolValueProperty myIsModified = new BoolValueProperty();

  private final InvalidationListener myWatchedListener = new InvalidationListener() {
    @Override
    protected void onInvalidated(@NotNull Observable sender) {
      myIsModified.set(true);
    }
  };

  public BoolValueProperty isModified() {
    return myIsModified;
  }

  public BoolValueProperty isInstalled() {
    return myIsInstalled;
  }

  public void snapshot() {
    for (ObservableProperty property : myWatched.keySet()) {
      myWatched.put(property, property.get());
    }

    myIsModified.set(false);
  }

  public void restore() {
    for (ObservableProperty property : myWatched.keySet()) {
      //noinspection unchecked
      property.set(myWatched.get(property));
    }

    myIsModified.set(false);
  }

  public void putValue(@NotNull String key, @NotNull Observable observable) {
    myValues.put(key, observable);
  }

  public void putWatchedValue(@NotNull String key, @NotNull ObservableProperty property) {
    putValue(key, property);
    property.addWeakListener(myWatchedListener);
    myWatched.put(property, property.get());
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
