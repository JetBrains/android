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

import com.android.tools.idea.ui.properties.AbstractProperty;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;

/**
 * A generic mapping of strings to {@link ObservableValue}s and {@link Runnable} actions. Adding
 * entries here allows them to be referenced within service.xml.
 * <p/>
 * Some of the values added to a context are marked as "watched", using
 * {@link #putWatchedValue(String, AbstractProperty)}. Those values are special, and modifying
 * any of them will mark this context as modified. {@link #snapshot()} and {@link #restore()} will
 * also save and revert watched values - this is useful, for example, if a user is modifying a
 * service but then cancels their changes.
 * <p/>
 * As a convention, you should organize your keys into namespaces, using periods to delimit them.
 * For example, instead of "countOfAnalyticsProjects" and "countOfAdsProjects", prefer instead
 * "analytics.projects.count" and "ads.projects.count"
 * <p/>
 * TODO: Revisit this class so that the whole concept of snapshot and restore is unecessary. Every
 * time we show the UI of a service, we should instead create and initialize a ServiceContext from
 * scratch?
 */
public final class ServiceContext {

  private final Map<String, ObservableValue> myValues = Maps.newHashMap();
  private final Map<String, Runnable> myActions = Maps.newHashMap();
  private final Map<AbstractProperty, Object> myWatched = new WeakHashMap<AbstractProperty, Object>();
  private final BoolValueProperty myInstalled = new BoolValueProperty();
  private final BoolValueProperty myModified = new BoolValueProperty();

  private final InvalidationListener myWatchedListener = new InvalidationListener() {
    @Override
    public void onInvalidated(@NotNull ObservableValue<?> sender) {
      myModified.set(true);
    }
  };

  @NotNull private final String myBuildSystemId;

  private Runnable myBeforeShown = EmptyRunnable.INSTANCE;
  private Callable<Boolean> myTestValidity = new Callable<Boolean>() {
    @Override
    public Boolean call() throws Exception {
      return true;
    }
  };

  public ServiceContext(@NotNull String buildSystemId) {
    myBuildSystemId = buildSystemId;
  }

  @NotNull
  String getBuildSystemId() {
    return myBuildSystemId;
  }

  /**
   * Set a callback to call before this service's UI is shown to the user. This is a useful place
   * to put in any expensive operations, like network requests, that should only happen when
   * needed.
   */
  public void setBeforeShownCallback(@NotNull Runnable beforeShown) {
    myBeforeShown = beforeShown;
  }

  /**
   * Set a callback to call when the user wishes to install this service, to ensure the values
   * are OK.
   */
  public void setIsValidCallback(@NotNull Callable<Boolean> testValidity) {
    myTestValidity = testValidity;
  }


  public void beginEditing() {
    myBeforeShown.run();

    if (myWatched.isEmpty()) {
      myModified.set(isValid());
    }
  }

  public void finishEditing() {
    if (!myModified.get()) {
      return;
    }

    myModified.set(isValid());
  }

  public void cancelEditing() {
    myModified.set(false);
  }


  /**
   * A property which indicates whether this service is already installed into the current module
   * or not.
   */
  public BoolValueProperty installed() {
    return myInstalled;
  }

  /**
   * A property which indicates if any of the watched values have been changed.
   *
   * @see #putWatchedValue(String, AbstractProperty)
   */
  public ObservableBool modified() {
    return myModified;
  }

  /**
   * Take a snapshot of the current state of all watched values and clear the modified flag. You
   * can later {@link #restore()} the values to the snapshot.
   *
   * @see #putWatchedValue(String, AbstractProperty)
   */
  public void snapshot() {
    // TODO: The snapshot concept was added when we thought users would be able to modify the
    // values of installed services. However, that's currently not supported and may never be.
    // Remove this method? (Related: http://b.android.com/178452)
    for (AbstractProperty property : myWatched.keySet()) {
      myWatched.put(property, property.get());
    }

    myModified.set(false);
  }

  /**
   * Restore the values captured by {@link #snapshot()}
   */
  public void restore() {
    for (AbstractProperty property : myWatched.keySet()) {
      //noinspection unchecked
      property.set(myWatched.get(property));
    }

    myModified.set(false);
  }

  /**
   * Put a named value into the context.
   */
  public void putValue(@NotNull String key, @NotNull ObservableValue value) {
    myValues.put(key, value);
  }

  /**
   * Put a named value into the context which can be {@link #snapshot()}ed and {@link #restore()}d.
   * Watched values are also used to determine whether this service has been {@link #modified()}.
   */
  public void putWatchedValue(@NotNull String key, @NotNull AbstractProperty property) {
    putValue(key, property);
    property.addWeakListener(myWatchedListener);
    myWatched.put(property, property.get());
  }

  /**
   * Put a named {@link Runnable} into the context.
   */
  public void putAction(@NotNull String key, @NotNull Runnable action) {
    myActions.put(key, action);
  }

  @NotNull
  public ObservableValue getValue(@NotNull String key) {
    ObservableValue value = myValues.get(key);
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
   * Converts this service context, which is itself backed by a flat map, into a hierarchical map,
   * a data structure that freemarker works well with.
   * <p/>
   * For example, a service context with the values "parent.child1" and "parent.child2" will return
   * a map that is nested like so
   * <pre>
   * parent
   *   child1
   *   child2
   * </pre>
   */
  @NotNull
  public Map<String, Object> toValueMap() {
    Map<String, Object> valueMap = Maps.newHashMap();
    Splitter splitter = Splitter.on('.');
    for (String key : myValues.keySet()) {
      ObservableValue value = getValue(key);

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

  /**
   * Check if this service is valid - if any of the user's values are bad, we shouldn't install
   * this service.
   */
  private boolean isValid() {
    try {
      return myTestValidity.call();
    }
    catch (Exception e) {
      return false;
    }
  }
}
