/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.tools.idea.stats;

import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.intellij.internal.statistic.StatisticsUploadAssistant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Manages persistence settings for DDMS.
 * <p/>
 * For convenience, this also stores persistence settings related to the "server stats" ping
 * as well as some ADT settings that are SDK specific but not workspace specific.
 * <p/>
 * This is mostly a copy of the tools/swt/sdkstats counterpart, adapted for compatibility with Studio.
 */
public class DdmsPreferenceStore {

  public  final static String PING_OPT_IN = "pingOptIn";          //$NON-NLS-1$
  private final static String PING_TIME   = "pingTime";           //$NON-NLS-1$
  private final static String PING_ID     = "pingId";             //$NON-NLS-1$

  private final static String ADT_USED = "adtUsed";               //$NON-NLS-1$
  private final static String LAST_SDK_PATH = "lastSdkPath";      //$NON-NLS-1$

  /**
   * PreferenceStore for DDMS.
   * Creation and usage must be synchronized on {@code DdmsPreferenceStore.class}.
   * Don't use it directly, instead retrieve it via {@link #getPreferenceStore()}.
   */
  private static volatile PreferenceStore sPrefStore;

  public DdmsPreferenceStore() {
  }

  /**
   * Returns the DDMS {@link PreferenceStore}.
   * This keeps a static reference on the store, so consequent calls will
   * return always the same store.
   */
  public PreferenceStore getPreferenceStore() {
    synchronized (DdmsPreferenceStore.class) {
      if (sPrefStore == null) {
        // get the location of the preferences
        String homeDir = null;
        try {
          homeDir = AndroidLocation.getFolder();
        } catch (AndroidLocationException e1) {
          // pass, we'll do a dummy store since homeDir is null
        }

        if (homeDir == null) {
          sPrefStore = new PreferenceStore();
          return sPrefStore;
        }

        assert homeDir != null;

        String rcFileName = homeDir + "ddms.cfg";                       //$NON-NLS-1$

        // also look for an old pref file in the previous location
        String oldPrefPath = System.getProperty("user.home")            //$NON-NLS-1$
                             + File.separator + ".ddmsrc";              //$NON-NLS-1$
        File oldPrefFile = new File(oldPrefPath);
        if (oldPrefFile.isFile()) {
          FileOutputStream fileOutputStream = null;
          try {
            PreferenceStore oldStore = new PreferenceStore(oldPrefPath);
            oldStore.load();

            fileOutputStream = new FileOutputStream(rcFileName);
            oldStore.save(fileOutputStream, "");    //$NON-NLS-1$
            oldPrefFile.delete();

            PreferenceStore newStore = new PreferenceStore(rcFileName);
            newStore.load();
            sPrefStore = newStore;
          } catch (IOException e) {
            // create a new empty store.
            sPrefStore = new PreferenceStore(rcFileName);
          } finally {
            if (fileOutputStream != null) {
              try {
                fileOutputStream.close();
              } catch (IOException e) {
                // pass
              }
            }
          }
        }
        else {
          sPrefStore = new PreferenceStore(rcFileName);

          try {
            sPrefStore.load();
          } catch (IOException e) {
            System.err.println("Error Loading DDMS Preferences");
          }
        }
      }

      assert sPrefStore != null;
      return sPrefStore;
    }
  }

  /**
   * Save the prefs to the config file.
   */
  public void save() {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      try {
        prefs.save();
      } catch (IOException ioe) {
        // FIXME com.android.dmmlib.Log.w("ddms", "Failed saving prefs file: " + ioe.getMessage());
      }
    }
  }

  // ---- Utility methods to access some specific prefs ----

  /**
   * Indicates whether the ping ID is set.
   * This should be true when {@link #isPingOptIn()} is true.
   *
   * @return true if a ping ID is set, which means the user gave permission
   *         to use the ping service.
   */
  public boolean hasPingId() {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      return prefs != null && prefs.contains(PING_ID);
    }
  }

  /**
   * Retrieves the current ping ID, if set.
   * To know if the ping ID is set, use {@link #hasPingId()}.
   * <p/>
   * There is no magic value reserved for "missing ping id or invalid store".
   * The only proper way to know if the ping id is missing is to use {@link #hasPingId()}.
   */
  public long getPingId() {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      // Note: getLong() returns 0L if the ID is missing so we do that too when
      // there's no store.
      return prefs == null ? 0L : prefs.getLong(PING_ID);
    }
  }

  /**
   * Generates a new random ping ID and saves it in the preference store.
   *
   * @return The new ping ID.
   */
  public long generateNewPingId() {
    PreferenceStore prefs = getPreferenceStore();

    Random rnd = new Random();
    long id = rnd.nextLong();

    synchronized (DdmsPreferenceStore.class) {
      prefs.setValue(PING_ID, id);
      try {
        prefs.save();
      } catch (IOException e) {
                /* ignore exceptions while saving preferences */
      }
    }

    return id;
  }

  /**
   * Returns the "ping opt in" value from the preference store.
   * This would be true if there's a valid preference store and
   * the user opted for sending ping statistics.
   */
  public boolean isPingOptIn() {
    // In the context of Studio, this checks whether the user approved to send stats
    // from the settings > stats panel. The whole stats code will only be invoked
    // if this is true, so it's almost certain this will return true.
    if (StatisticsUploadAssistant.isSendAllowed()) {
      return true;
    }
    // Legacy code from ADT/ddms. This won't be used here. Keep for reference.
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      return prefs != null && prefs.contains(PING_OPT_IN);
    }
  }

  /**
   * Saves the "ping opt in" value in the preference store.
   *
   * @param optIn The new user opt-in value.
   */
  public void setPingOptIn(boolean optIn) {
    PreferenceStore prefs = getPreferenceStore();

    synchronized (DdmsPreferenceStore.class) {
      prefs.setValue(PING_OPT_IN, optIn);
      try {
        prefs.save();
      } catch (IOException e) {
                /* ignore exceptions while saving preferences */
      }
    }
  }

  /**
   * Retrieves the ping time for the given app from the preference store.
   * Callers should use {@link System#currentTimeMillis()} for time stamps.
   *
   * @param app The app name identifier.
   * @return 0L if we don't have a preference store or there was no time
   *         recorded in the store for the requested app. Otherwise the time stamp
   *         from the store.
   */
  public long getPingTime(String app) {
    PreferenceStore prefs = getPreferenceStore();
    String timePref = PING_TIME + "." + app;  //$NON-NLS-1$
    synchronized (DdmsPreferenceStore.class) {
      return prefs == null ? 0 : prefs.getLong(timePref);
    }
  }

  /**
   * Sets the ping time for the given app from the preference store.
   * Callers should use {@link System#currentTimeMillis()} for time stamps.
   *
   * @param app       The app name identifier.
   * @param timeStamp The time stamp from the store.
   *                  0L is a special value that should not be used.
   */
  public void setPingTime(String app, long timeStamp) {
    PreferenceStore prefs = getPreferenceStore();
    String timePref = PING_TIME + "." + app;  //$NON-NLS-1$
    synchronized (DdmsPreferenceStore.class) {
      prefs.setValue(timePref, timeStamp);
      try {
        prefs.save();
      } catch (IOException ioe) {
                /* ignore exceptions while saving preferences */
      }
    }
  }

  /**
   * True if this is the first time the users runs ADT, which is detected by
   * the lack of the setting set using {@link #setAdtUsed(boolean)}
   * or this value being set to true.
   *
   * @return true if ADT has been used  before
   * @see #setAdtUsed(boolean)
   */
  public boolean isAdtUsed() {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      if (prefs == null || !prefs.contains(ADT_USED)) {
        return false;
      }
      return prefs.getBoolean(ADT_USED);
    }
  }

  /**
   * Sets whether the ADT startup wizard has been shown.
   * ADT sets first to false once the welcome wizard has been shown once.
   *
   * @param used true if ADT has been used
   */
  public void setAdtUsed(boolean used) {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      prefs.setValue(ADT_USED, used);
      try {
        prefs.save();
      } catch (IOException ioe) {
                /* ignore exceptions while saving preferences */
      }
    }
  }

  /**
   * Retrieves the last SDK OS path.
   * <p/>
   * This is just an information value, the path may not exist, may not
   * even be on an existing file system and/or may not point to an SDK
   * anymore.
   *
   * @return The last SDK OS path from the preference store, or null if
   *         there is no store or an empty string if it is not defined.
   */
  public String getLastSdkPath() {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      return prefs == null ? null : prefs.getString(LAST_SDK_PATH);
    }
  }

  /**
   * Sets the last SDK OS path.
   *
   * @param osSdkPath The SDK OS Path. Can be null or empty.
   */
  public void setLastSdkPath(String osSdkPath) {
    PreferenceStore prefs = getPreferenceStore();
    synchronized (DdmsPreferenceStore.class) {
      prefs.setValue(LAST_SDK_PATH, osSdkPath);
      try {
        prefs.save();
      } catch (IOException ioe) {
                /* ignore exceptions while saving preferences */
      }
    }
  }
}
