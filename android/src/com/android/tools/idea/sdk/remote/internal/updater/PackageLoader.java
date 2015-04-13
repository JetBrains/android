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
import com.android.sdklib.repository.SdkAddonsListConstants;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.*;
import com.android.tools.idea.sdk.remote.internal.AddonsListFetcher.Site;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.Package.UpdateInfo;
import com.android.tools.idea.sdk.remote.internal.sources.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads packages fetched from the remote SDK Repository and keeps track
 * of their state compared with the current local SDK installation.
 */
public class PackageLoader {

  /**
   * The update data context. Never null.
   */
  private final UpdaterData mUpdaterData;

  /**
   * The {@link DownloadCache} override. Can be null, in which case the one from
   * {@link UpdaterData} is used instead.
   *
   * @see #getDownloadCache()
   */
  private final DownloadCache mOverrideCache;

  /**
   * 0 = need to fetch remote addons list once..
   * 1 = fetch succeeded, don't need to do it any more.
   * -1= fetch failed, do it again only if the user requests a refresh
   * or changes the force-http setting.
   */
  private int mStateFetchRemoteAddonsList;

  /**
   * Creates a new PackageManager associated with the given {@link UpdaterData}
   * and using the {@link UpdaterData}'s default {@link DownloadCache}.
   *
   * @param updaterData The {@link UpdaterData}. Must not be null.
   */
  public PackageLoader(UpdaterData updaterData) {
    mUpdaterData = updaterData;
    mOverrideCache = null;
  }
  /**
   * Loads the remote add-ons list.
   */
  public void loadRemoteAddonsList(ITaskMonitor monitor) {

    if (mStateFetchRemoteAddonsList != 0) {
      return;
    }

    mUpdaterData.getTaskFactory().start("Load Add-ons List", monitor, new ITask() {
      @Override
      public void run(ITaskMonitor subMonitor) {
        loadRemoteAddonsListInTask(subMonitor);
      }
    });
  }

  private void loadRemoteAddonsListInTask(ITaskMonitor monitor) {
    mStateFetchRemoteAddonsList = -1;

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
        monitor.logError("Ignoring invalid SDK_TEST_BASE_URL: %1$s", baseUrl);  //$NON-NLS-1$
      }
    }

    if (mUpdaterData.getSettingsController().getSettings().getForceHttp()) {
      url = url.replaceAll("https://", "http://");    //$NON-NLS-1$ //$NON-NLS-2$
    }

    // Hook to bypass loading 3rd party addons lists.
    boolean fetch3rdParties = System.getenv("SDK_SKIP_3RD_PARTIES") == null;

    AddonsListFetcher fetcher = new AddonsListFetcher();
    Site[] sites = fetcher.fetch(url, getDownloadCache(), monitor);
    if (sites != null) {
      SdkSources sources = mUpdaterData.getSources();
      sources.removeAll(SdkSourceCategory.ADDONS_3RD_PARTY);

      if (fetch3rdParties) {
        for (Site s : sites) {
          switch (s.getType()) {
            case ADDON_SITE:
              sources.add(SdkSourceCategory.ADDONS_3RD_PARTY, new SdkAddonSource(s.getUrl(), s.getUiName()));
              break;
            case SYS_IMG_SITE:
              sources.add(SdkSourceCategory.ADDONS_3RD_PARTY, new SdkSysImgSource(s.getUrl(), s.getUiName()));
              break;
          }
        }
      }

      mStateFetchRemoteAddonsList = 1;
    }

    monitor.setDescription("Fetched Add-ons List successfully");
  }

  /**
   * Returns the {@link DownloadCache} to use.
   *
   * @return Returns {@link #mOverrideCache} if not null; otherwise returns the
   * one from {@link UpdaterData} is used instead.
   */
  private DownloadCache getDownloadCache() {
    return mOverrideCache != null ? mOverrideCache : mUpdaterData.getDownloadCache();
  }
}
