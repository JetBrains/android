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

package com.android.tools.idea.sdk.remote.internal.updater;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.tools.idea.sdk.remote.internal.updater.ISettingsPage;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.io.IFileOp;
import com.android.utils.ILogger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Controller class to get settings values. Settings are kept in-memory.
 * Users of this class must first load the settings before changing them and save
 * them when modified.
 * <p/>
 * Settings are enumerated by constants in {@link ISettingsPage}.
 */
public class SettingsController {

    private static final String SETTINGS_FILENAME = "androidtool.cfg"; //$NON-NLS-1$

    private final IFileOp mFileOp;
    private final ILogger mSdkLog;
    private final Settings mSettings;

    public interface OnChangedListener {
        public void onSettingsChanged(@NonNull com.android.tools.idea.sdk.remote.internal.updater.SettingsController controller,
                                      @NonNull Settings oldSettings);
    }
    private final List<OnChangedListener> mChangedListeners = new ArrayList<OnChangedListener>(1);

    /** The currently associated {@link ISettingsPage}. Can be null. */
    private ISettingsPage mSettingsPage;


    /**
     * Constructs a new default {@link com.android.tools.idea.sdk.remote.internal.updater.SettingsController}.
     *
     * @param sdkLog A non-null logger to use.
     */
    public SettingsController(@NonNull ILogger sdkLog) {
        this(new FileOp(), sdkLog);
    }

    /**
     * Constructs a new default {@link com.android.tools.idea.sdk.remote.internal.updater.SettingsController}.
     *
     * @param fileOp A non-null {@link FileOp} to perform file operations (to load/save settings.)
     * @param sdkLog A non-null logger to use.
     */
    public SettingsController(@NonNull IFileOp fileOp, @NonNull ILogger sdkLog) {
        this(fileOp, sdkLog, new Settings());
    }

    /**
     * Specialized constructor that wraps an existing {@link Settings} instance.
     * This is mostly used in unit-tests to override settings that are being used.
     * Normal usage should NOT need to call this constructor.
     *
     * @param fileOp   A non-null {@link FileOp} to perform file operations (to load/save settings)
     * @param sdkLog   A non-null logger to use.
     * @param settings A non-null {@link Settings} to use as-is. It is not duplicated.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected SettingsController(@NonNull IFileOp fileOp,
                                 @NonNull ILogger sdkLog,
                                 @NonNull Settings settings) {
        mFileOp = fileOp;
        mSdkLog = sdkLog;
        mSettings = settings;
    }

    @NonNull
    public Settings getSettings() {
        return mSettings;
    }

    public void registerOnChangedListener(@Nullable OnChangedListener listener) {
        if (listener != null && !mChangedListeners.contains(listener)) {
            mChangedListeners.add(listener);
        }
    }

    public void unregisterOnChangedListener(@Nullable OnChangedListener listener) {
        if (listener != null) {
            mChangedListeners.remove(listener);
        }
    }

    //--- Access to settings ------------


    public static class Settings {
        private final Properties mProperties;

        /** Initialize an empty set of settings. */
        public Settings() {
            mProperties = new Properties();
        }

        /** Duplicates a set of settings. */
        public Settings(@NonNull Settings settings) {
            this();
            for (Entry<Object, Object> entry : settings.mProperties.entrySet()) {
                mProperties.put(entry.getKey(), entry.getValue());
            }
        }

        /**
         * Specialized constructor for unit-tests that wraps an existing
         * {@link Properties} instance. The properties instance is not duplicated,
         * it's merely used as-is and changes will be reflected directly.
         */
        protected Settings(@NonNull Properties properties) {
            mProperties = properties;
        }

        /**
         * Returns the value of the {@link ISettingsPage#KEY_FORCE_HTTP} setting.
         *
         * @see ISettingsPage#KEY_FORCE_HTTP
         */
        public boolean getForceHttp() {
            return Boolean.parseBoolean(mProperties.getProperty(ISettingsPage.KEY_FORCE_HTTP));
        }

        /**
         * Returns the value of the {@link ISettingsPage#KEY_ASK_ADB_RESTART} setting.
         *
         * @see ISettingsPage#KEY_ASK_ADB_RESTART
         */
        public boolean getAskBeforeAdbRestart() {
            return Boolean.parseBoolean(mProperties.getProperty(ISettingsPage.KEY_ASK_ADB_RESTART));
        }

        /**
         * Returns the value of the {@link ISettingsPage#KEY_USE_DOWNLOAD_CACHE} setting.
         *
         * @see ISettingsPage#KEY_USE_DOWNLOAD_CACHE
         */
        public boolean getUseDownloadCache() {
            return Boolean.parseBoolean(
                    mProperties.getProperty(
                            ISettingsPage.KEY_USE_DOWNLOAD_CACHE,
                            Boolean.TRUE.toString()));
        }

        /**
         * Returns the value of the {@link ISettingsPage#KEY_SHOW_UPDATE_ONLY} setting.
         *
         * @see ISettingsPage#KEY_SHOW_UPDATE_ONLY
         */
        public boolean getShowUpdateOnly() {
            return Boolean.parseBoolean(
                    mProperties.getProperty(
                            ISettingsPage.KEY_SHOW_UPDATE_ONLY,
                            Boolean.TRUE.toString()));
        }

        /**
         * Returns the value of the {@link ISettingsPage#KEY_ENABLE_PREVIEWS} setting.
         *
         * @see ISettingsPage#KEY_ENABLE_PREVIEWS
         */
        public boolean getEnablePreviews() {
            return Boolean.parseBoolean(mProperties.getProperty(ISettingsPage.KEY_ENABLE_PREVIEWS));
        }

        /**
         * Returns the value of the {@link ISettingsPage#KEY_MONITOR_DENSITY} setting
         * @see ISettingsPage#KEY_MONITOR_DENSITY
         */
        public int getMonitorDensity() {
            String value = mProperties.getProperty(ISettingsPage.KEY_MONITOR_DENSITY, null);
            if (value == null) {
                return -1;
            }

            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    /**
     * Sets the value of the {@link ISettingsPage#KEY_SHOW_UPDATE_ONLY} setting.
     *
     * @param enabled True if only compatible non-obsolete update items should be shown.
     * @see ISettingsPage#KEY_SHOW_UPDATE_ONLY
     */
    public void setShowUpdateOnly(boolean enabled) {
        setSetting(ISettingsPage.KEY_SHOW_UPDATE_ONLY, enabled);
    }

    /**
     * Sets the value of the {@link ISettingsPage#KEY_MONITOR_DENSITY} setting.
     *
     * @param density the density of the monitor
     * @see ISettingsPage#KEY_MONITOR_DENSITY
     */
    public void setMonitorDensity(int density) {
        mSettings.mProperties.setProperty(
                ISettingsPage.KEY_MONITOR_DENSITY, Integer.toString(density));
    }

    /**
     * Internal helper to set a boolean setting.
     */
    void setSetting(@NonNull String key, boolean value) {
        mSettings.mProperties.setProperty(key, Boolean.toString(value));
    }

    //--- Controller methods -------------

    /**
     * Associate the given {@link ISettingsPage} with this {@link com.android.tools.idea.sdk.remote.internal.updater.SettingsController}.
     * <p/>
     * This loads the current properties into the setting page UI.
     * It then associates the SettingsChanged callback with this controller.
     * <p/>
     * If the setting page given is null, it will be unlinked from controller.
     *
     * @param settingsPage An {@link ISettingsPage} to associate with the controller.
     */
    public void setSettingsPage(@Nullable ISettingsPage settingsPage) {
        mSettingsPage = settingsPage;

        if (settingsPage != null) {
            settingsPage.loadSettings(mSettings.mProperties);

            settingsPage.setOnSettingsChanged(new ISettingsPage.SettingsChangedCallback() {
                @Override
                public void onSettingsChanged(ISettingsPage page) {
                    com.android.tools.idea.sdk.remote.internal.updater.SettingsController.this.onSettingsChanged();
                }
            });
        }
    }

    /**
     * Load settings from the settings file.
     */
    public void loadSettings() {

        String path = null;
        try {
            String folder = AndroidLocation.getFolder();
            File f = new File(folder, SETTINGS_FILENAME);
            path = f.getPath();

            Properties props = mFileOp.loadProperties(f);
            mSettings.mProperties.clear();
            mSettings.mProperties.putAll(props);

            // Properly reformat some settings to enforce their default value when missing.
            setShowUpdateOnly(mSettings.getShowUpdateOnly());
            setSetting(ISettingsPage.KEY_ASK_ADB_RESTART, mSettings.getAskBeforeAdbRestart());
            setSetting(ISettingsPage.KEY_USE_DOWNLOAD_CACHE, mSettings.getUseDownloadCache());

        } catch (Exception e) {
            if (mSdkLog != null) {
                mSdkLog.error(e,
                        "Failed to load settings from .android folder. Path is '%1$s'.",
                        path);
            }
        }
    }

    /**
     * Saves settings to the settings file.
     */
    public void saveSettings() {

        String path = null;
        try {
            String folder = AndroidLocation.getFolder();
            File f = new File(folder, SETTINGS_FILENAME);
            path = f.getPath();
            mFileOp.saveProperties(f, mSettings.mProperties, "## Settings for Android Tool");  //$NON-NLS-1$

        } catch (Exception e) {
            if (mSdkLog != null) {
                // This is important enough that we want to really nag the user about it
                String reason = null;

                if (e instanceof FileNotFoundException) {
                    reason = "File not found";
                } else if (e instanceof AndroidLocationException) {
                    reason = ".android folder not found, please define ANDROID_SDK_HOME";
                } else if (e.getMessage() != null) {
                    reason = String.format("%1$s: %2$s",
                            e.getClass().getSimpleName(),
                            e.getMessage());
                } else {
                    reason = e.getClass().getName();
                }

                mSdkLog.error(e, "Failed to save settings file '%1$s': %2$s", path, reason);
            }
        }
    }

    /**
     * When settings have changed: retrieve the new settings, apply them, save them
     * and notify on-settings-changed listeners.
     */
    private void onSettingsChanged() {
        if (mSettingsPage == null) {
            return;
        }

        Settings oldSettings = new Settings(mSettings);
        mSettingsPage.retrieveSettings(mSettings.mProperties);
        applySettings();
        saveSettings();

        for (OnChangedListener listener : mChangedListeners) {
            try {
                listener.onSettingsChanged(this, oldSettings);
            } catch (Throwable ignore) {}
        }
    }

    /**
     * Applies the current settings.
     */
    public void applySettings() {
        Properties props = System.getProperties();

        // Get the configured HTTP proxy settings
        String proxyHost = mSettings.mProperties.getProperty(ISettingsPage.KEY_HTTP_PROXY_HOST,
                ""); //$NON-NLS-1$
        String proxyPort = mSettings.mProperties.getProperty(ISettingsPage.KEY_HTTP_PROXY_PORT,
                ""); //$NON-NLS-1$

        // Set both the HTTP and HTTPS proxy system properties.
        // The system property constants can be found in the Java SE documentation at
        // http://download.oracle.com/javase/6/docs/technotes/guides/net/proxies.html
        final String JAVA_PROP_HTTP_PROXY_HOST =  "http.proxyHost";      //$NON-NLS-1$
        final String JAVA_PROP_HTTP_PROXY_PORT =  "http.proxyPort";      //$NON-NLS-1$
        final String JAVA_PROP_HTTPS_PROXY_HOST = "https.proxyHost";     //$NON-NLS-1$
        final String JAVA_PROP_HTTPS_PROXY_PORT = "https.proxyPort";     //$NON-NLS-1$

        // Only change the proxy if have something in the preferences.
        // Do not erase the default settings by empty values.
        if (proxyHost != null && proxyHost.length() > 0) {
            props.setProperty(JAVA_PROP_HTTP_PROXY_HOST,  proxyHost);
            props.setProperty(JAVA_PROP_HTTPS_PROXY_HOST, proxyHost);
        }
        if (proxyPort != null && proxyPort.length() > 0) {
            props.setProperty(JAVA_PROP_HTTP_PROXY_PORT,  proxyPort);
            props.setProperty(JAVA_PROP_HTTPS_PROXY_PORT, proxyPort);
        }
     }

}
