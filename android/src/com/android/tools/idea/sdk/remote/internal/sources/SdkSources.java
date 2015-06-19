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

package com.android.tools.idea.sdk.remote.internal.sources;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.jcip.annotations.GuardedBy;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * A list of sdk-repository and sdk-addon sources, sorted by {@link SdkSourceCategory}.
 */
public class SdkSources {

  private static final String KEY_COUNT = "count";
  private static final String KEY_SRC = "src";
  private static final String KEY_DISPLAY = "disp";

  private static final String SRC_FILENAME = "repositories.cfg"; //$NON-NLS-1$

  private static final List<SdkSource> EMPTY_LIST = ImmutableList.of();

  @GuardedBy("itself") private final EnumMap<SdkSourceCategory, List<SdkSource>> mySources =
    new EnumMap<SdkSourceCategory, List<SdkSource>>(SdkSourceCategory.class);

  private final ArrayList<Runnable> mChangeListeners = new ArrayList<Runnable>();

  public SdkSources() {
  }

  /**
   * Adds a new source to the Sources list.
   * <p/>
   * Implementation detail: {@link SdkSources} doesn't invoke {@link #notifyChangeListeners()}
   * directly. Callers who use {@code add()} are responsible for notifying the listeners once
   * they are done modifying the sources list. The intent is to notify the listeners only once
   * at the end, not for every single addition.
   */
  public void add(SdkSourceCategory category, SdkSource source) {
    synchronized (mySources) {
      List<SdkSource> list = mySources.get(category);
      if (list == null || list == EMPTY_LIST) {
        list = new ArrayList<SdkSource>();
        mySources.put(category, list);
      }

      list.add(source);
    }
  }

  /**
   * Replaces the current collection of sources corresponding to a particular category with the given collection.
   * <p/>
   * Implementation detail: {@link SdkSources} doesn't invoke {@link #notifyChangeListeners()}
   * directly. Callers who use {@code set()} are responsible for notifying the listeners once
   * they are done modifying the sources list. The intent is to notify the listeners only once
   * at the end, not for every single addition.
   */
  public void set(SdkSourceCategory category, Collection<SdkSource> sources) {
    synchronized (mySources) {
      mySources.put(category, Lists.newArrayList(sources));
    }
  }

  /**
   * Removes a source from the Sources list.
   * <p/>
   * Callers who remove entries are responsible for notifying the listeners using
   * {@link #notifyChangeListeners()} once they are done modifying the sources list.
   */
  public void remove(SdkSource source) {
    synchronized (mySources) {
      for (SdkSourceCategory category : mySources.keySet()) {
        List<SdkSource> list = mySources.get(category);

        if (list.remove(source)) {
          if (list.isEmpty()) {
            // Set to the marker so we know not to reload it
            mySources.put(category, EMPTY_LIST);
          }
        }
      }
    }
  }

  /**
   * Removes all the sources in the given category.
   * <p/>
   * Callers who remove entries are responsible for notifying the listeners using
   * {@link #notifyChangeListeners()} once they are done modifying the sources list.
   */
  public void removeAll(SdkSourceCategory category) {
    synchronized (mySources) {
      mySources.remove(category);
    }
  }

  /**
   * Returns a new array of sources attached to the given category.
   * Might return an empty array, but never returns null.
   */
  public SdkSource[] getSources(SdkSourceCategory category) {
    synchronized (mySources) {
      List<SdkSource> list = mySources.get(category);
      if (list == null) {
        return new SdkSource[0];
      }
      else {
        return list.toArray(new SdkSource[list.size()]);
      }
    }
  }

  /**
   * Returns true if there are sources for the given category.
   */
  public boolean sourcesLoaded(SdkSourceCategory category) {
    synchronized (mySources) {
      List<SdkSource> list = mySources.get(category);
      return list == EMPTY_LIST || (list != null && !list.isEmpty());
    }
  }

  /**
   * Returns an array of the sources across all categories. This is never null.
   */
  public SdkSource[] getAllSources() {
    synchronized (mySources) {
      int n = 0;

      for (List<SdkSource> list : mySources.values()) {
        n += list.size();
      }

      SdkSource[] sources = new SdkSource[n];

      int i = 0;
      for (List<SdkSource> list : mySources.values()) {
        for (SdkSource source : list) {
          sources[i++] = source;
        }
      }

      return sources;
    }
  }

 /**
   * Returns true if there's already a similar source in the sources list
   * under any category.
   * <p/>
   * Important: The match is NOT done on object identity.
   * Instead, this searches for a <em>similar</em> source, based on
   * {@link SdkSource#equals(Object)} which compares the source URLs.
   * <p/>
   * The search is O(N), which should be acceptable on the expectedly small source list.
   */
  public boolean hasSourceUrl(SdkSource source) {
    synchronized (mySources) {
      for (List<SdkSource> list : mySources.values()) {
        for (SdkSource s : list) {
          if (s.equals(source)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  /**
   * Loads all user sources. This <em>replaces</em> all existing user sources
   * by the ones from the property file.
   * <p/>
   * This calls {@link #notifyChangeListeners()} at the end of the operation.
   */
  public void loadUserAddons(ILogger log) {
    // Implementation detail: synchronize on the sources list to make sure that
    // a- the source list doesn't change while we load/save it, and most important
    // b- to make sure it's not being saved while loaded or the reverse.
    // In most cases we do these operation from the UI thread so it's not really
    // that necessary. This is more a protection in case of someone calls this
    // from a worker thread by mistake.
    synchronized (mySources) {
      List<SdkSource> result = Lists.newArrayList();

      // Load new user sources from property file
      FileInputStream fis = null;
      try {
        String folder = AndroidLocation.getFolder();
        File f = new File(folder, SRC_FILENAME);
        if (f.exists()) {
          fis = new FileInputStream(f);

          Properties props = new Properties();
          props.load(fis);

          int count = Integer.parseInt(props.getProperty(KEY_COUNT, "0"));

          for (int i = 0; i < count; i++) {
            String url = props.getProperty(String.format("%s%02d", KEY_SRC, i));  //$NON-NLS-1$
            String disp = props.getProperty(String.format("%s%02d", KEY_DISPLAY, i));  //$NON-NLS-1$
            if (url != null) {
              // FIXME: this code originally only dealt with add-on XML sources.
              // Now we'd like it to deal with system-image sources too, but we
              // don't know which kind of object it is (at least not without
              // trying to fetch it.) As a temporary workaround, just take a
              // guess based on the leaf URI name. However ideally what we can
              // simply do is add a checkbox "is system-image XML" in the user
              // dialog and pass this info down here. Another alternative is to
              // make a "dynamic" source object that tries to guess its type once
              // the URI has been fetched.
              SdkSource s;
              if (url.endsWith(SdkSysImgConstants.URL_DEFAULT_FILENAME)) {
                s = new SdkSysImgSource(url, disp);
              }
              else {
                s = new SdkAddonSource(url, disp);
              }
              if (!hasSourceUrl(s)) {
                result.add(s);
              }
            }
          }
        }
        if (result.isEmpty()) {
          result = EMPTY_LIST;
        }
        mySources.put(SdkSourceCategory.USER_ADDONS, result);
      }
      catch (NumberFormatException e) {
        log.error(e, null);

      }
      catch (AndroidLocationException e) {
        log.error(e, null);

      }
      catch (IOException e) {
        log.error(e, null);

      }
      finally {
        if (fis != null) {
          try {
            fis.close();
          }
          catch (IOException e) {
          }
        }
      }
    }
    notifyChangeListeners();
  }

  /**
   * Saves all the user sources.
   *
   * @param log Logger. Cannot be null.
   */
  public void saveUserAddons(ILogger log) {
    // See the implementation detail note in loadUserAddons() about the synchronization.
    synchronized (mySources) {
      FileOutputStream fos = null;
      try {
        String folder = AndroidLocation.getFolder();
        File f = new File(folder, SRC_FILENAME);

        fos = new FileOutputStream(f);

        Properties props = new Properties();

        int count = 0;
        for (SdkSource s : getSources(SdkSourceCategory.USER_ADDONS)) {
          props.setProperty(String.format("%s%02d", KEY_SRC, count), //$NON-NLS-1$
                            s.getUrl());
          if (s.getUiName() != null) {
            props.setProperty(String.format("%s%02d", KEY_DISPLAY, count), //$NON-NLS-1$
                              s.getUiName());
          }
          count++;
        }
        props.setProperty(KEY_COUNT, Integer.toString(count));

        props.store(fos, "## User Sources for Android SDK Manager");  //$NON-NLS-1$

      }
      catch (AndroidLocationException e) {
        log.error(e, null);

      }
      catch (IOException e) {
        log.error(e, null);

      }
      finally {
        if (fos != null) {
          try {
            fos.close();
          }
          catch (IOException e) {
          }
        }
      }
    }
  }

  /**
   * Adds a listener that will be notified when the sources list has changed.
   *
   * @param changeListener A non-null listener to add. Ignored if already present.
   * @see SdkSources#notifyChangeListeners()
   */
  public void addChangeListener(@NotNull Runnable changeListener) {
    synchronized (mChangeListeners) {
      if (!mChangeListeners.contains(changeListener)) {
        mChangeListeners.add(changeListener);
      }
    }
  }

  /**
   * Removes a listener from the list of listeners to notify when the sources change.
   *
   * @param changeListener A listener to remove. Ignored if not previously added.
   */
  public void removeChangeListener(@NotNull Runnable changeListener) {
    synchronized (mChangeListeners) {
      mChangeListeners.remove(changeListener);
    }
  }

  /**
   * Invoke all the registered change listeners, if any.
   * <p/>
   * This <em>may</em> be called from a worker thread, in which case the runnable
   * should take care of only updating UI from a main thread.
   */
  public void notifyChangeListeners() {
    synchronized (mChangeListeners) {
      for (Runnable runnable : mChangeListeners) {
        try {
          runnable.run();
        }
        catch (Throwable ignore) {
          assert false : "A SdkSource.ChangeListener failed with an exception: " + ignore.toString();
        }
      }
    }
  }
}
