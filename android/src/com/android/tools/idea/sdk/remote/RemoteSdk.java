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

package com.android.tools.idea.sdk.remote;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.remote.internal.AddonsListFetcher;
import com.android.tools.idea.sdk.remote.internal.AddonsListFetcher.Site;
import com.android.tools.idea.sdk.remote.internal.DownloadCache;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.NullTaskMonitor;
import com.android.tools.idea.sdk.remote.internal.sources.*;
import com.android.tools.idea.sdk.remote.internal.updater.SettingsController;
import com.android.utils.ILogger;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import java.util.ArrayList;


/**
 * This class keeps information on the remote SDK repository.
 */
public class RemoteSdk {

  /**
   * Default expiration delay is 24 hours.
   */
  public final static long DEFAULT_EXPIRATION_PERIOD_MS = 24 * 3600 * 1000;

  private final SettingsController mSettingsController;
  private final SdkSources mSdkSources = new SdkSources();
  private long mSdkSourceTS;
  private DownloadCache mDownloadCache;

  public RemoteSdk() {
    mSettingsController = SettingsController.getInstance();
  }

  /**
   * Fetches the remote list of packages.
   * <p/>
   * This respects the settings from the {@link SettingsController} which
   * dictates whether the {@link DownloadCache} is used and whether HTTP
   * is enforced over HTTPS.
   * <p/>
   * The call may block on network access. Callers will likely want to invoke this
   * from a thread and make sure the logger is thread-safe with regard to UI updates.
   *
   * @param sources The sources to download from.
   * @param logger  A logger to report status & progress.
   * @return A non-null map of {@link PkgType} to {@link RemotePkgInfo}
   * describing the remote packages available for install/download.
   */
  @NonNull
  public Multimap<PkgType, RemotePkgInfo> fetch(@NonNull SdkSources sources, @NonNull ILogger logger) {
    Multimap<PkgType, RemotePkgInfo> remotes = HashMultimap.create();

    boolean forceHttp = mSettingsController.getForceHttp();

    // Implementation detail: right now this reuses the SdkSource(s) classes
    // from the sdk-repository v2. The problem with that is that the sources are
    // mutable and hold the fetch logic and hold the packages array.
    // Instead I'd prefer to have the sources be immutable descriptors and move
    // the fetch logic here. Eventually my goal is to get rid of them
    // and include the logic directly here instead but for right now lets
    // just start with what we have to avoid implementing it all at once.

    for (SdkSource source : sources.getAllSources()) {
      source.load(getDownloadCache(), new NullTaskMonitor(logger), forceHttp);
      RemotePkgInfo[] pkgs = source.getPackages();
      if (pkgs != null) {  // pkgs will be null if there was an error during load
        for (RemotePkgInfo p : pkgs) {
          remotes.put(p.getPkgDesc().getType(), p);
        }
      }
    }

    return remotes;
  }

  /**
   * Returns the {@link SdkSources} object listing all sources to load from.
   * This includes the main repository.xml, the main addon.xml as well as all the
   * add-ons or sys-img xmls listed in the addons-list.xml.
   * <p/>
   * The method caches the last access and only refresh it if data is either not
   * present or the expiration time has be passed.
   *
   * @param expirationDelayMs The expiration delay in milliseconds.
   *                          Use {@link #DEFAULT_EXPIRATION_PERIOD_MS} by default.
   * @param logger            A non-null object to log messages. TODO change to an ITaskMonitor
   *                          to be able to update the caller's progress bar UI, if any.
   * @return A non-null {@link SdkSources}
   */
  @NonNull
  public SdkSources fetchSources(long expirationDelayMs, @NonNull ILogger logger) {
    long now = System.currentTimeMillis();
    boolean expired = (now - mSdkSourceTS) > expirationDelayMs;

    // Load the conventional sources.
    // For testing, the env var can be set to replace the default root download URL.
    // It must end with a / and its the location where the updater will look for
    // the repository.xml, addons_list.xml and such files.

    if (expired || !mSdkSources.sourcesLoaded(SdkSourceCategory.ANDROID_REPO)) {
      String baseUrl = System.getenv("SDK_TEST_BASE_URL");                        //$NON-NLS-1$
      if (baseUrl == null || baseUrl.length() <= 0 || !baseUrl.endsWith("/")) {   //$NON-NLS-1$
        baseUrl = SdkRepoConstants.URL_GOOGLE_SDK_SITE;
      }

      mSdkSources.set(SdkSourceCategory.ANDROID_REPO,
                      Lists.<SdkSource>newArrayList(new SdkRepoSource(baseUrl, SdkSourceCategory.ANDROID_REPO.getUiName())));
    }

    // Load user sources (this will also notify change listeners but this operation is
    // done early enough that there shouldn't be any anyway.)
    // Don't refresh sources, as this doesn't really change except within the sdk manager, which manages refreshes separately.
    if (!mSdkSources.sourcesLoaded(SdkSourceCategory.USER_ADDONS)) {
      mSdkSources.loadUserAddons(logger);
    }

    if (expired || !mSdkSources.sourcesLoaded(SdkSourceCategory.ADDONS_3RD_PARTY)) {
      ITaskMonitor tempMonitor = new NullTaskMonitor(logger);

      String url = SdkAddonsListConstants.URL_ADDON_LIST;

      // We override SdkRepoConstants.URL_GOOGLE_SDK_SITE if this is defined
      String baseUrl = System.getenv("SDK_TEST_BASE_URL");            //$NON-NLS-1$
      if (baseUrl != null) {
        if (baseUrl.length() > 0 && baseUrl.endsWith("/")) {        //$NON-NLS-1$
          if (url.startsWith(SdkRepoConstants.URL_GOOGLE_SDK_SITE)) {
            url = baseUrl + url.substring(SdkRepoConstants.URL_GOOGLE_SDK_SITE.length());
          }
        }
        else {
          tempMonitor.logError("Ignoring invalid SDK_TEST_BASE_URL: %1$s", baseUrl);  //$NON-NLS-1$
        }
      }

      if (mSettingsController.getForceHttp()) {
        url = url.replaceAll("https://", "http://");    //$NON-NLS-1$ //$NON-NLS-2$
      }

      // Hook to bypass loading 3rd party addons lists.
      boolean fetch3rdParties = System.getenv("SDK_SKIP_3RD_PARTIES") == null;

      AddonsListFetcher fetcher = new AddonsListFetcher();
      Site[] sites = fetcher.fetch(url, getDownloadCache(), tempMonitor);

      if (sites != null) {
        mSdkSources.removeAll(SdkSourceCategory.ADDONS_3RD_PARTY);
        if (fetch3rdParties) {
          ArrayList<SdkSource> newSources = Lists.newArrayList();
          for (Site s : sites) {
            switch (s.getType()) {
              case ADDON_SITE:
                newSources.add(new SdkAddonSource(s.getUrl(), s.getUiName()));
                break;
              case SYS_IMG_SITE:
                newSources.add(new SdkSysImgSource(s.getUrl(), s.getUiName()));
                break;
            }
          }
          mSdkSources.set(SdkSourceCategory.ADDONS_3RD_PARTY, newSources);
        }
      }
    }

    mSdkSourceTS = now;

    return mSdkSources;
  }

  /**
   * Returns the {@link DownloadCache}
   * Extracted so that we can override this in unit tests.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected DownloadCache getDownloadCache() {
    if (mDownloadCache == null) {
      mDownloadCache = new DownloadCache(
        mSettingsController.getUseDownloadCache() ? DownloadCache.Strategy.FRESH_CACHE : DownloadCache.Strategy.DIRECT);
    }
    return mDownloadCache;
  }
}
