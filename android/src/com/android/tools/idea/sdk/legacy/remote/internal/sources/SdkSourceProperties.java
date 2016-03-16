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

package com.android.tools.idea.sdk.legacy.remote.internal.sources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Properties for individual sources which are persisted by a local settings file.
 * <p/>
 * All instances of {@link com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSourceProperties} share the same singleton storage.
 * The persisted setting file is loaded as necessary, however callers must persist
 * it at some point by calling {@link #save()}.
 */
public class SdkSourceProperties {

    /**
     * An internal file version number, in case we want to change the format later.
     */
    private static final String KEY_VERSION  = "@version@";                 //$NON-NLS-1$
    /**
     * The last known UI name of the source.
     */
    public static final String KEY_NAME     = "@name@";                     //$NON-NLS-1$
    /**
     * A non-null string if the source is disabled. Null if the source is enabled.
     */
    public static final String KEY_DISABLED = "@disabled@";                 //$NON-NLS-1$

    private static final Properties sSourcesProperties = new Properties();
    private static final String     SRC_FILENAME = "sites-settings.cfg";    //$NON-NLS-1$

    private static boolean sModified = false;

    public SdkSourceProperties() {
    }

    public void save() {
        synchronized (sSourcesProperties) {
            if (sModified && !sSourcesProperties.isEmpty()) {
                saveLocked();
                sModified = false;
            }
        }
    }

    /**
     * Retrieves a property for the given source URL and the given key type.
     * <p/>
     * Implementation detail: this loads the persistent settings file as needed.
     *
     * @param key The kind of property to retrieve for that source URL.
     * @param sourceUrl The source URL.
     * @param defaultValue The default value to return, if the property isn't found. Can be null.
     * @return The non-null string property for the key/sourceUrl or the default value.
     */
    @Nullable
    public String getProperty(@NonNull String key,
                              @NonNull String sourceUrl,
                              @Nullable String defaultValue) {
        String value = defaultValue;

        synchronized (sSourcesProperties) {
            if (sSourcesProperties.isEmpty()) {
                loadLocked();
            }

            value = sSourcesProperties.getProperty(key + sourceUrl, defaultValue);
        }

        return value;
    }

    /**
     * Sets or remove a property for the given source URL and the given key type.
     * <p/>
     * Implementation detail: this does <em>not</em> save the persistent settings file.
     * Somehow the caller will need to call the {@link #save()} method later.
     *
     * @param key The kind of property to retrieve for that source URL.
     * @param sourceUrl The source URL.
     * @param value The new value to set (if non null) or null to remove an existing property.
     */
    public void setProperty(String key, String sourceUrl, String value) {
        synchronized (sSourcesProperties) {
            if (sSourcesProperties.isEmpty()) {
                loadLocked();
            }

            key += sourceUrl;

            String old = sSourcesProperties.getProperty(key);
            if (value == null) {
                if (old != null) {
                    sSourcesProperties.remove(key);
                    sModified = true;
                }
            } else if (old == null || !old.equals(value)) {
                sSourcesProperties.setProperty(key, value);
                sModified = true;
            }
        }
    }

    /**
     * Returns an internal string representation of the underlying Properties map,
     * sorted by ascending keys. Useful for debugging and testing purposes only.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("<SdkSourceProperties");      //$NON-NLS-1$
        synchronized (sSourcesProperties) {
            List<Object> keys = Collections.list(sSourcesProperties.keys());
            Collections.sort(keys, new Comparator<Object>() {
                @Override
                public int compare(Object o1, Object o2) {
                    return o1.toString().compareTo(o2.toString());
                }});

            for (Object key : keys) {
                sb.append('\n').append(key)
                  .append(" = ").append(sSourcesProperties.get(key));       //$NON-NLS-1$
            }
        }
        sb.append('>');
        return sb.toString();
    }

    /** Load state from persistent file. Expects sSourcesProperties to be synchronized. */
    private void loadLocked() {
        // Load state from persistent file
        if (loadProperties()) {
            // If it lacks our magic version key, don't use it
            if (sSourcesProperties.getProperty(KEY_VERSION) == null) {
                sSourcesProperties.clear();
            }

            sModified = false;
        }

        if (sSourcesProperties.isEmpty()) {
            // Nothing was loaded. Initialize the storage with a version
            // identified. This isn't currently checked back, but we might
            // want it later if we decide to change the way this works.
            // The version key is chosen on purpose to not match any valid URL.
            sSourcesProperties.setProperty(KEY_VERSION, "1"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /**
     * Load properties from default file. Extracted so that it can be mocked in tests.
     *
     * @return True if actually loaded the file. False if there was an IO error or no
     *   file and nothing was loaded.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected boolean loadProperties() {
        try {
            String folder = AndroidLocation.getFolder();
            File f = new File(folder, SRC_FILENAME);
            if (f.exists()) {
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(f);
                    sSourcesProperties.load(fis);
                } catch (IOException ignore) {
                    // nop
                } finally {
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException ignore) {}
                    }
                }

                return true;
            }
        } catch (AndroidLocationException ignore) {
            // nop
        }
        return false;
    }

    /**
     * Save file to disk. Expects sSourcesProperties to be synchronized.
     * Made accessible for testing purposes.
     * For public usage, please use {@link #save()} instead.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected void saveLocked() {
        // Persist it to the file
        FileOutputStream fos = null;
        try {
            String folder = AndroidLocation.getFolder();
            File f = new File(folder, SRC_FILENAME);

            fos = new FileOutputStream(f);

            sSourcesProperties.store(fos,"## Sites Settings for Android SDK Manager");//$NON-NLS-1$

        } catch (AndroidLocationException ignore) {
            // nop
        } catch (IOException ignore) {
            // nop
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignore) {}
            }
        }
    }

    /** Empty current property list. Made accessible for testing purposes. */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected void clear() {
        synchronized (sSourcesProperties) {
            sSourcesProperties.clear();
            sModified = false;
        }
    }
}
