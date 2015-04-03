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
import com.android.tools.idea.sdk.remote.internal.AddonsListFetcher;
import com.android.tools.idea.sdk.remote.internal.AddonsListFetcher.Site;
import com.android.tools.idea.sdk.remote.internal.DownloadCache;
import com.android.tools.idea.sdk.remote.internal.ITask;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.NullTaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.Package.UpdateInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkAddonSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSourceCategory;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSysImgSource;
import com.android.tools.idea.sdk.remote.internal.updater.PkgItem;
import com.android.tools.idea.sdk.remote.internal.updater.UpdaterData;
import com.android.sdklib.repository.SdkAddonsListConstants;
import com.android.sdklib.repository.SdkRepoConstants;

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

    /** The update data context. Never null. */
    private final UpdaterData mUpdaterData;

    /**
     * The {@link DownloadCache} override. Can be null, in which case the one from
     * {@link UpdaterData} is used instead.
     * @see #getDownloadCache()
     */
    private final DownloadCache mOverrideCache;

    /**
     * 0 = need to fetch remote addons list once..
     * 1 = fetch succeeded, don't need to do it any more.
     * -1= fetch failed, do it again only if the user requests a refresh
     *     or changes the force-http setting.
     */
    private int mStateFetchRemoteAddonsList;


    /**
     * Interface for the callback called by
     * {@link com.android.tools.idea.sdk.remote.internal.updater.PackageLoader#loadPackages(boolean, ISourceLoadedCallback)}.
     * <p/>
     * After processing each source, the package loader calls {@link #onUpdateSource}
     * with the list of packages found in that source.
     * By returning true from {@link #onUpdateSource}, the client tells the loader to
     * continue and process the next source. By returning false, it tells to stop loading.
     * <p/>
     * The {@link #onLoadCompleted()} method is guaranteed to be called at the end, no
     * matter how the loader stopped, so that the client can clean up or perform any
     * final action.
     */
    public interface ISourceLoadedCallback {
        /**
         * After processing each source, the package loader calls this method with the
         * list of packages found in that source.
         * By returning true from {@link #onUpdateSource}, the client tells
         * the loader to continue and process the next source.
         * By returning false, it tells to stop loading.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients which
         * try to access any UI widgets must wrap their calls into
         * {@code Display.syncExec(Runnable)} or {@code Display.asyncExec(Runnable)}.
         *
         * @param packages All the packages loaded from the source. Never null.
         * @return True if the load operation should continue, false if it should stop.
         */
        public boolean onUpdateSource(SdkSource source, Package[] packages);

        /**
         * This method is guaranteed to be called at the end, no matter how the
         * loader stopped, so that the client can clean up or perform any final action.
         */
        public void onLoadCompleted();
    }

    /**
     * Interface describing the task of installing a specific package.
     * For details on the operation,
     * see {@link com.android.tools.idea.sdk.remote.internal.updater.PackageLoader#loadPackagesWithInstallTask(int, IAutoInstallTask)}.
     *
     * @see com.android.tools.idea.sdk.remote.internal.updater.PackageLoader#loadPackagesWithInstallTask(int, IAutoInstallTask)
     */
    public interface IAutoInstallTask {
        /**
         * Invoked by the loader once a source has been loaded and its package
         * definitions are known. The method should return the {@code packages}
         * array and can modify it if necessary.
         * The loader will call {@link #acceptPackage(Package)} on all the packages returned.
         *
         * @param source The source of the packages. Null for the locally installed packages.
         * @param packages The packages found in the source.
         */
        public Package[] filterLoadedSource(SdkSource source, Package[] packages);

        /**
         * Called by the install task for every package available (new ones, updates as well
         * as existing ones that don't have a potential update.)
         * The method should return true if this is a package that should be installed.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients who try
         * to access any UI widgets must wrap their calls into {@code Display.syncExec(Runnable)}
         * or {@code Display.asyncExec(Runnable)}.
         */
        public boolean acceptPackage(Package pkg);

        /**
         * Called when the accepted package has been installed, successfully or not.
         * If an already installed (aka existing) package has been accepted, this will
         * be called with a 'true' success and the actual install paths.
         * <p/>
         * <em>Important</em>: This method is called from a sub-thread, so clients who try
         * to access any UI widgets must wrap their calls into {@code Display.syncExec(Runnable)}
         * or {@code Display.asyncExec(Runnable)}.
         */
        public void setResult(boolean success, Map<Package, File> installPaths);

        /**
         * Called when the task is done iterating and completed.
         */
        public void taskCompleted();
    }

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
     * Creates a new PackageManager associated with the given {@link UpdaterData}
     * but using the specified {@link DownloadCache} instead of the one from
     * {@link UpdaterData}.
     *
     * @param updaterData The {@link UpdaterData}. Must not be null.
     * @param cache The {@link DownloadCache} to use instead of the one from {@link UpdaterData}.
     */
    public PackageLoader(UpdaterData updaterData, DownloadCache cache) {
        mUpdaterData = updaterData;
        mOverrideCache = cache;
    }

    public UpdaterData getUpdaterData() {
        return mUpdaterData;
    }

    /**
     * Runs a runnable on the UI thread.
     * The base implementation just runs the runnable right away.
     *
     * @param r Non-null runnable.
     */
    protected void runOnUiThread(@NonNull Runnable r) {
        r.run();
    }

    /**
     * Loads all packages from the remote repository.
     * This runs in an {@link ITask}. The call is blocking.
     * <p/>
     * The callback is called with each set of {@link PkgItem} found in each source.
     * The caller is responsible to accumulate the packages given to the callback
     * after each source is finished loaded. In return the callback tells the loader
     * whether to continue loading sources.
     * <p/>
     * Normally this method doesn't access the remote source if it's already
     * been loaded in the in-memory source (e.g. don't fetch twice).
     *
     * @param overrideExisting Set this to true  when the caller wants to
     *          check for updates and discard any existing source already
     *          loaded in memory. It should be false for normal use.
     * @param sourceLoadedCallback The callback to invoke for each loaded source.
     */
    public void loadPackages(
            final boolean overrideExisting,
            final ISourceLoadedCallback sourceLoadedCallback) {
        try {
            if (mUpdaterData == null) {
                return;
            }

            mUpdaterData.getTaskFactory().start("Loading Sources", new ITask() {
                @Override
                public void run(ITaskMonitor monitor) {
                    monitor.setProgressMax(10);

                    // get local packages and offer them to the callback
                    Package[] localPkgs =
                        mUpdaterData.getInstalledPackages(monitor.createSubMonitor(1));
                    if (localPkgs == null) {
                        localPkgs = new Package[0];
                    }
                    if (!sourceLoadedCallback.onUpdateSource(null, localPkgs)) {
                        return;
                    }

                    // get remote packages
                    boolean forceHttp =
                        mUpdaterData.getSettingsController().getSettings().getForceHttp();
                    loadRemoteAddonsList(monitor.createSubMonitor(1));

                    SdkSource[] sources = mUpdaterData.getSources().getAllSources();
                    try {
                        if (sources != null && sources.length > 0) {
                            ITaskMonitor subMonitor = monitor.createSubMonitor(8);
                            subMonitor.setProgressMax(sources.length);
                            for (SdkSource source : sources) {
                                Package[] pkgs = source.getPackages();
                                if (pkgs == null || overrideExisting) {
                                    source.load(getDownloadCache(),
                                            subMonitor.createSubMonitor(1),
                                            forceHttp);
                                    pkgs = source.getPackages();
                                }
                                if (pkgs == null) {
                                    continue;
                                }

                                // Notify the callback a new source has finished loading.
                                // If the callback requests so, stop right away.
                                if (!sourceLoadedCallback.onUpdateSource(source, pkgs)) {
                                    return;
                                }
                            }
                        }
                    } catch(Exception e) {
                        monitor.logError("Loading source failed: %1$s", e.toString());
                    } finally {
                        monitor.setDescription("Done loading packages.");
                    }
                }
            });
        } finally {
            sourceLoadedCallback.onLoadCompleted();
        }
    }

    /**
     * Load packages, source by source using
     * {@link #loadPackages(boolean, ISourceLoadedCallback)},
     * and executes the given {@link IAutoInstallTask} on the current package list.
     * That is for each package known, the install task is queried to find if
     * the package is the one to be installed or updated.
     * <p/>
     * - If an already installed package is accepted by the task, it is returned. <br/>
     * - If a new package (remotely available but not installed locally) is accepted,
     * the user will be <em>prompted</em> for permission to install it. <br/>
     * - If an existing package has updates, the install task will be accept if it
     * accepts one of the updating packages, and if yes the the user will be
     * <em>prompted</em> for permission to install it. <br/>
     * <p/>
     * Only one package can be accepted, after which the task is completed.
     * There is no direct return value, {@link IAutoInstallTask#setResult} is called on the
     * result of the accepted package.
     * When the task is completed, {@link IAutoInstallTask#taskCompleted()} is called.
     * <p/>
     * The call is blocking. Although the name says "Task", this is not an {@link ITask}
     * running in its own thread but merely a synchronous call.
     *
     * @param installFlags Flags for installation such as
     *  {@link UpdaterData#TOOLS_MSG_UPDATED_FROM_ADT}.
     * @param installTask The task to perform.
     */
    public void loadPackagesWithInstallTask(
            final int installFlags,
            final IAutoInstallTask installTask) {

        loadPackages(false /*overrideExisting*/, new ISourceLoadedCallback() {
            List<Archive> mArchivesToInstall = new ArrayList<Archive>();
            Map<Package, File> mInstallPaths = new HashMap<Package, File>();

            @Override
            public boolean onUpdateSource(SdkSource source, Package[] packages) {
                packages = installTask.filterLoadedSource(source, packages);
                if (packages == null || packages.length == 0) {
                    // Tell loadPackages() to process the next source.
                    return true;
                }

                for (Package pkg : packages) {
                    if (pkg.isLocal()) {
                        // This is a local (aka installed) package
                        if (installTask.acceptPackage(pkg)) {
                            // If the caller is accepting an installed package,
                            // return a success and give the package's install path
                            Archive[] a = pkg.getArchives();
                            // an installed package should have one local compatible archive
                            if (a.length == 1 && a[0].isCompatible()) {
                                mInstallPaths.put(pkg, new File(a[0].getLocalOsPath()));
                            }
                        }

                    } else {
                        // This is a remote package
                        if (installTask.acceptPackage(pkg)) {
                            // The caller is accepting this remote package. We'll install it.
                            for (Archive archive : pkg.getArchives()) {
                                if (archive.isCompatible()) {
                                    mArchivesToInstall.add(archive);
                                    break;
                                }
                            }
                        }
                    }
                }

                // Tell loadPackages() to process the next source.
                return true;
            }

            @Override
            public void onLoadCompleted() {
                if (!mArchivesToInstall.isEmpty()) {
                    installArchives(mArchivesToInstall);
                }
                if (mInstallPaths == null) {
                    installTask.setResult(false, null);
                } else {
                    installTask.setResult(true, mInstallPaths);
                }

                installTask.taskCompleted();
            }

            /**
             * Shows the UI of the install selector.
             * If the package is then actually installed, refresh the local list and
             * notify the install task of the installation path.
             *
             * @param archivesToInstall The archives to install.
             */
            private void installArchives(final List<Archive> archivesToInstall) {
                // Actually install the new archives that we just found.
                // This will display some UI so we need a shell's sync exec.

                final List<Archive> installedArchives = new ArrayList<Archive>();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        List<Archive> archives =
                            mUpdaterData.updateOrInstallAll_WithGUI(
                                archivesToInstall,
                                true /* includeObsoletes */,
                                installFlags);

                        if (archives != null) {
                            installedArchives.addAll(archives);
                        }
                    }
                });

                if (installedArchives.isEmpty()) {
                    // We failed to install anything.
                    mInstallPaths = null;
                    return;
                }

                // The local package list has changed, make sure to refresh it
                mUpdaterData.getSdkManager().reloadSdk(mUpdaterData.getSdkLog());
                mUpdaterData.getLocalSdkParser().clearPackages();
                final Package[] localPkgs = mUpdaterData.getInstalledPackages(
                        new NullTaskMonitor(mUpdaterData.getSdkLog()));

                // Scan the installed package list to find the install paths.
                for (Archive installedArchive : installedArchives) {
                    Package pkg = installedArchive.getParentPackage();

                    for (Package localPkg : localPkgs) {
                        if (localPkg.canBeUpdatedBy(pkg) == UpdateInfo.NOT_UPDATE) {
                            Archive[] localArchive = localPkg.getArchives();
                            if (localArchive.length == 1 && localArchive[0].isCompatible()) {
                                mInstallPaths.put(
                                    localPkg,
                                    new File(localArchive[0].getLocalOsPath()));
                            }
                        }
                    }
                }
            }
        });
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
            } else {
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
                        sources.add(SdkSourceCategory.ADDONS_3RD_PARTY,
                                new SdkAddonSource(s.getUrl(), s.getUiName()));
                        break;
                    case SYS_IMG_SITE:
                        sources.add(SdkSourceCategory.ADDONS_3RD_PARTY,
                                new SdkSysImgSource(s.getUrl(), s.getUiName()));
                        break;
                    }
                }
            }

            sources.notifyChangeListeners();

            mStateFetchRemoteAddonsList = 1;
        }

        monitor.setDescription("Fetched Add-ons List successfully");
    }

    /**
     * Returns the {@link DownloadCache} to use.
     *
     * @return Returns {@link #mOverrideCache} if not null; otherwise returns the
     *  one from {@link UpdaterData} is used instead.
     */
    private DownloadCache getDownloadCache() {
        return mOverrideCache != null ? mOverrideCache : mUpdaterData.getDownloadCache();
    }
}
