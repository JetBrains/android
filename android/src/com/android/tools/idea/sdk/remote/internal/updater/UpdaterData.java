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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.ISdkChangeListener;
import com.android.sdklib.repository.License;
import com.android.sdklib.repository.SdkAddonConstants;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdklib.util.LineUtil;
import com.android.tools.idea.sdk.remote.internal.*;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.archives.ArchiveInstaller;
import com.android.tools.idea.sdk.remote.internal.packages.AddonPackage;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.PlatformToolPackage;
import com.android.tools.idea.sdk.remote.internal.packages.ToolPackage;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSourceCategory;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.utils.ILogger;
import com.android.utils.IReaderLogger;
import com.android.utils.SparseIntArray;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * Data shared by the SDK Manager updaters.
 */
public class UpdaterData implements IUpdaterData {

  public static final int NO_TOOLS_MSG = 0;
  public static final int TOOLS_MSG_UPDATED_FROM_ADT = 1;
  public static final int TOOLS_MSG_UPDATED_FROM_SDKMAN = 2;

  private String mOsSdkRoot;

  private final LocalSdkParser mLocalSdkParser = new LocalSdkParser();
  /**
   * Holds all sources. Do not use this directly.
   * Instead use {@link #getSources()} so that unit tests can override this as needed.
   */
  private final SdkSources mSources = new SdkSources();
  /**
   * Holds settings. Do not use this directly.
   * Instead use {@link #getSettingsController()} so that unit tests can override this.
   */
  private final SettingsController mSettingsController;
  private final ArrayList<ISdkChangeListener> mListeners = new ArrayList<ISdkChangeListener>();
  private final ILogger mSdkLog;
  private ITaskFactory mTaskFactory;

  private SdkManager mSdkManager;
  /**
   * The current {@link PackageLoader} to use.
   * Lazily created in {@link #getPackageLoader()}.
   */
  private PackageLoader mPackageLoader;
  /**
   * The current {@link DownloadCache} to use.
   * Lazily created in {@link #getDownloadCache()}.
   */
  private DownloadCache mDownloadCache;
  private AndroidLocationException mAvdManagerInitError;

  /**
   * Creates a new updater data.
   *
   * @param sdkLog    Logger. Cannot be null.
   * @param osSdkRoot The OS path to the SDK root.
   */
  public UpdaterData(String osSdkRoot, ILogger sdkLog) {
    mOsSdkRoot = osSdkRoot;
    mSdkLog = sdkLog;

    mSettingsController = initSettingsController();
    initSdk();
  }

  // ----- getters, setters ----

  public String getOsSdkRoot() {
    return mOsSdkRoot;
  }

  @Override
  public DownloadCache getDownloadCache() {
    if (mDownloadCache == null) {
      mDownloadCache = new DownloadCache(
        getSettingsController().getSettings().getUseDownloadCache() ? DownloadCache.Strategy.FRESH_CACHE : DownloadCache.Strategy.DIRECT);
    }
    return mDownloadCache;
  }

  public void setTaskFactory(ITaskFactory taskFactory) {
    mTaskFactory = taskFactory;
  }

  @Override
  public ITaskFactory getTaskFactory() {
    return mTaskFactory;
  }

  public SdkSources getSources() {
    return mSources;
  }

  public LocalSdkParser getLocalSdkParser() {
    return mLocalSdkParser;
  }

  @Override
  public ILogger getSdkLog() {
    return mSdkLog;
  }

  @Override
  public SdkManager getSdkManager() {
    return mSdkManager;
  }

  @Override
  public SettingsController getSettingsController() {
    return mSettingsController;
  }

  /**
   * Removes a listener ({@link ISdkChangeListener}) that is notified when the SDK is reloaded.
   */
  public void removeListener(ISdkChangeListener listener) {
    mListeners.remove(listener);
  }

  public PackageLoader getPackageLoader() {
    // The package loader is lazily initialized here.
    if (mPackageLoader == null) {
      mPackageLoader = new PackageLoader(this);
    }
    return mPackageLoader;
  }

  /**
   * Check if any error occurred during initialization.
   * If it did, display an error message.
   *
   * @return True if an error occurred, false if we should continue.
   */
  public boolean checkIfInitFailed() {
    if (mAvdManagerInitError != null) {
      String example;
      if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
        example = "%USERPROFILE%";     //$NON-NLS-1$
      }
      else {
        example = "~";                 //$NON-NLS-1$
      }

      String error = String.format("The AVD manager normally uses the user's profile directory to store " +
                                   "AVD files. However it failed to find the default profile directory. " +
                                   "\n" +
                                   "To fix this, please set the environment variable ANDROID_SDK_HOME to " +
                                   "a valid path such as \"%s\".", example);

      displayInitError(error);

      return true;
    }
    return false;
  }

  protected void displayInitError(String error) {
    mSdkLog.error(null /* Throwable */, "%s", error);  //$NON-NLS-1$
  }

  // -----

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
   * Initializes the {@link SdkManager} and the {@link AvdManager}.
   * Extracted so that we can override this in unit tests.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected void initSdk() {
    setSdkManager(SdkManager.createManager(mOsSdkRoot, mSdkLog));
    // notify listeners.
    broadcastOnSdkReload();
  }

  /**
   * Initializes the {@link SettingsController}
   * Extracted so that we can override this in unit tests.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected SettingsController initSettingsController() {
    SettingsController settingsController = new SettingsController(mSdkLog);
    return settingsController;
  }

  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected void setSdkManager(SdkManager sdkManager) {
    mSdkManager = sdkManager;
  }

  /**
   * Reloads the SDK content (targets).
   * <p/>
   * This also reloads the AVDs in case their status changed.
   * <p/>
   * This does not notify the listeners ({@link ISdkChangeListener}).
   */
  public void reloadSdk() {
    // reload SDK
    mSdkManager.reloadSdk(mSdkLog);
    mLocalSdkParser.clearPackages();

    // notify listeners
    broadcastOnSdkReload();
  }

  /**
   * Sets up the default sources: <br/>
   * - the default google SDK repository, <br/>
   * - the user sources from prefs <br/>
   * - the extra repo URLs from the environment, <br/>
   * - and finally the extra user repo URLs from the environment.
   */
  public void setupDefaultSources() {
    SdkSources sources = getSources();

    // Load the conventional sources.
    // For testing, the env var can be set to replace the default root download URL.
    // It must end with a / and its the location where the updater will look for
    // the repository.xml, addons_list.xml and such files.

    String baseUrl = System.getenv("SDK_TEST_BASE_URL");                        //$NON-NLS-1$
    if (baseUrl == null || baseUrl.length() <= 0 || !baseUrl.endsWith("/")) {   //$NON-NLS-1$
      baseUrl = SdkRepoConstants.URL_GOOGLE_SDK_SITE;
    }

    sources.add(SdkSourceCategory.ANDROID_REPO, new SdkRepoSource(baseUrl, SdkSourceCategory.ANDROID_REPO.getUiName()));

    // Load user sources (this will also notify change listeners but this operation is
    // done early enough that there shouldn't be any anyway.)
    sources.loadUserAddons(getSdkLog());
  }

  /**
   * Returns the list of installed packages, parsing them if this has not yet been done.
   * <p/>
   * The package list is cached in the {@link LocalSdkParser} and will be reset when
   * {@link #reloadSdk()} is invoked.
   */
  public Package[] getInstalledPackages(ITaskMonitor monitor) {
    LocalSdkParser parser = getLocalSdkParser();

    Package[] packages = parser.getPackages();

    if (packages == null) {
      // load on demand the first time
      packages = parser.parseSdk(getOsSdkRoot(), getSdkManager(), monitor);
    }

    return packages;
  }

  /**
   * Install the list of given {@link Archive}s. This is invoked by the user selecting some
   * packages in the remote page and then clicking "install selected".
   *
   * @param archives The archives to install. Incompatible ones will be skipped.
   * @param flags    Optional flags for the installer, such as {@link #NO_TOOLS_MSG}.
   * @return A list of archives that have been installed. Can be empty but not null.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected List<Archive> installArchives(final List<ArchiveInfo> archives, final int flags) {
    if (mTaskFactory == null) {
      throw new IllegalArgumentException("Task Factory is null");
    }

    // this will accumulate all the packages installed.
    final List<Archive> newlyInstalledArchives = new ArrayList<Archive>();

    final boolean forceHttp = getSettingsController().getSettings().getForceHttp();

    // sort all archives based on their dependency level.
    Collections.sort(archives, new InstallOrderComparator());

    mTaskFactory.start("Installing Archives", new ITask() {
      @Override
      public void run(ITaskMonitor monitor) {

        final int progressPerArchive = 2 * ArchiveInstaller.NUM_MONITOR_INC;
        monitor.setProgressMax(1 + archives.size() * progressPerArchive);
        monitor.setDescription("Preparing to install archives");

        boolean installedAddon = false;
        boolean installedTools = false;
        boolean installedPlatformTools = false;
        boolean preInstallHookInvoked = false;

        // Mark all current local archives as already installed.
        HashSet<Archive> installedArchives = new HashSet<Archive>();
        for (Package p : getInstalledPackages(monitor.createSubMonitor(1))) {
          for (Archive a : p.getArchives()) {
            installedArchives.add(a);
          }
        }

        int numInstalled = 0;
        nextArchive:
        for (ArchiveInfo ai : archives) {
          Archive archive = ai.getNewArchive();
          if (archive == null) {
            // This is not supposed to happen.
            continue nextArchive;
          }

          int nextProgress = monitor.getProgress() + progressPerArchive;
          try {
            if (monitor.isCancelRequested()) {
              break;
            }

            ArchiveInfo[] adeps = ai.getDependsOn();
            if (adeps != null) {
              for (ArchiveInfo adep : adeps) {
                Archive na = adep.getNewArchive();
                if (na == null) {
                  // This archive depends on a missing archive.
                  // We shouldn't get here.
                  // Skip it.
                  monitor.log("Skipping '%1$s'; it depends on a missing package.", archive.getParentPackage().getShortDescription());
                  continue nextArchive;
                }
                else if (!installedArchives.contains(na)) {
                  // This archive depends on another one that was not installed.
                  // We shouldn't get here.
                  // Skip it.
                  monitor.logError("Skipping '%1$s'; it depends on '%2$s' which was not installed.",
                                   archive.getParentPackage().getShortDescription(), adep.getShortDescription());
                  continue nextArchive;
                }
              }
            }

            if (!preInstallHookInvoked) {
              preInstallHookInvoked = true;
              broadcastPreInstallHook();
            }

            ArchiveInstaller installer = createArchiveInstaler();
            if (installer.install(ai, mOsSdkRoot, forceHttp, mSdkManager, getDownloadCache(), monitor)) {
              // We installed this archive.
              newlyInstalledArchives.add(archive);
              installedArchives.add(archive);
              numInstalled++;

              // If this package was replacing an existing one, the old one
              // is no longer installed.
              installedArchives.remove(ai.getReplaced());

              // Check if we successfully installed a platform-tool or add-on package.
              if (archive.getParentPackage() instanceof AddonPackage) {
                installedAddon = true;
              }
              else if (archive.getParentPackage() instanceof ToolPackage) {
                installedTools = true;
              }
              else if (archive.getParentPackage() instanceof PlatformToolPackage) {
                installedPlatformTools = true;
              }
            }

          }
          catch (Throwable t) {
            // Display anything unexpected in the monitor.
            String msg = t.getMessage();
            if (msg != null) {
              msg = String.format("Unexpected Error installing '%1$s': %2$s: %3$s", archive.getParentPackage().getShortDescription(),
                                  t.getClass().getCanonicalName(), msg);
            }
            else {
              // no error info? get the stack call to display it
              // At least that'll give us a better bug report.
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              t.printStackTrace(new PrintStream(baos));

              msg = String
                .format("Unexpected Error installing '%1$s'\n%2$s", archive.getParentPackage().getShortDescription(), baos.toString());
            }

            monitor.log("%1$s", msg);      //$NON-NLS-1$
            mSdkLog.error(t, "%1$s", msg);      //$NON-NLS-1$
          }
          finally {

            // Always move the progress bar to the desired position.
            // This allows internal methods to not have to care in case
            // they abort early
            monitor.incProgress(nextProgress - monitor.getProgress());
          }
        }

        if (installedAddon) {
          // Update the USB vendor ids for adb
          try {
            mSdkManager.updateAdb();
            monitor.log("Updated ADB to support the USB devices declared in the SDK add-ons.");
          }
          catch (Exception e) {
            mSdkLog.error(e, "Update ADB failed");
            monitor.logError("failed to update adb to support the USB devices declared in the SDK add-ons.");
          }
        }

        if (preInstallHookInvoked) {
          broadcastPostInstallHook();
        }

        if (installedAddon || installedPlatformTools) {
          // We need to restart ADB. Actually since we don't know if it's even
          // running, maybe we should just kill it and not start it.
          // Note: it turns out even under Windows we don't need to kill adb
          // before updating the tools folder, as adb.exe is (surprisingly) not
          // locked.

          askForAdbRestart(monitor);
        }

        if (installedTools) {
          notifyToolsNeedsToBeRestarted(flags);
        }

        if (numInstalled == 0) {
          monitor.setDescription("Done. Nothing was installed.");
        }
        else {
          monitor.setDescription("Done. %1$d %2$s installed.", numInstalled, numInstalled == 1 ? "package" : "packages");

          //notify listeners something was installed, so that they can refresh
          reloadSdk();
        }
      }
    });

    return newlyInstalledArchives;
  }

  /**
   * A comparator to sort all the {@link ArchiveInfo} based on their
   * dependency level. This forces the installer to install first all packages
   * with no dependency, then those with one level of dependency, etc.
   */
  private static class InstallOrderComparator implements Comparator<ArchiveInfo> {

    private final Map<ArchiveInfo, Integer> mOrders = new HashMap<ArchiveInfo, Integer>();

    @Override
    public int compare(ArchiveInfo o1, ArchiveInfo o2) {
      int n1 = getDependencyOrder(o1);
      int n2 = getDependencyOrder(o2);

      return n1 - n2;
    }

    private int getDependencyOrder(ArchiveInfo ai) {
      if (ai == null) {
        return 0;
      }

      // reuse cached value, if any
      Integer cached = mOrders.get(ai);
      if (cached != null) {
        return cached.intValue();
      }

      ArchiveInfo[] deps = ai.getDependsOn();
      if (deps == null) {
        return 0;
      }

      // compute dependencies, recursively
      int n = deps.length;

      for (ArchiveInfo dep : deps) {
        n += getDependencyOrder(dep);
      }

      // cache it
      mOrders.put(ai, Integer.valueOf(n));

      return n;
    }

  }

  /**
   * Attempts to restart ADB.
   * <p/>
   * If the "ask before restart" setting is set (the default), prompt the user whether
   * now is a good time to restart ADB.
   */
  protected void askForAdbRestart(ITaskMonitor monitor) {
    // Restart ADB if we don't need to ask.
    if (!getSettingsController().getSettings().getAskBeforeAdbRestart()) {
      AdbWrapper adb = new AdbWrapper(getOsSdkRoot(), monitor);
      adb.stopAdb();
      adb.startAdb();
    }
  }

  protected void notifyToolsNeedsToBeRestarted(int flags) {

    String msg = null;
    if ((flags & TOOLS_MSG_UPDATED_FROM_ADT) == TOOLS_MSG_UPDATED_FROM_ADT) {
      msg = "The Android SDK and AVD Manager that you are currently using has been updated. " +
            "Please also run Eclipse > Help > Check for Updates to see if the Android " +
            "plug-in needs to be updated.";

    }
    else if ((flags & TOOLS_MSG_UPDATED_FROM_SDKMAN) == TOOLS_MSG_UPDATED_FROM_SDKMAN) {
      msg = "The Android SDK and AVD Manager that you are currently using has been updated. " +
            "It is recommended that you now close the manager window and re-open it. " +
            "If you use Eclipse, please run Help > Check for Updates to see if the Android " +
            "plug-in needs to be updated.";
    }
    else if ((flags & NO_TOOLS_MSG) == NO_TOOLS_MSG) {
      return;
    }
    mSdkLog.info("%s", msg);  //$NON-NLS-1$
  }

  /**
   * Fetches all archives available on the known remote sources.
   * <p/>
   * Used by {@link UpdaterData#listRemotePackages_NoGUI} and
   * {@link UpdaterData#updateOrInstallAll_NoGUI}.
   *
   * @param includeAll True to list and install all packages, including obsolete ones.
   * @return A list of potential {@link ArchiveInfo} to install.
   */
  private List<ArchiveInfo> getRemoteArchives_NoGUI(boolean includeAll) {
    refreshSources(true);
    getPackageLoader().loadRemoteAddonsList(new NullTaskMonitor(getSdkLog()));

    List<ArchiveInfo> archives;
    SdkUpdaterLogic ul = new SdkUpdaterLogic(this);

    if (includeAll) {
      archives = ul.getAllRemoteArchives(getSources(), getLocalSdkParser().getPackages(), includeAll);

    }
    else {
      archives = ul.computeUpdates(null /*selectedArchives*/, getSources(), getLocalSdkParser().getPackages(), includeAll);

      ul.addNewPlatforms(archives, getSources(), getLocalSdkParser().getPackages(), includeAll);
    }

    Collections.sort(archives);
    return archives;
  }

  /**
   * Lists remote packages available for install using
   * {@link updateOrInstallAll_NoGUI}.
   *
   * @param includeAll     True to list and install all packages, including obsolete ones.
   * @param extendedOutput True to display more details on each package.
   */
  public void listRemotePackages_NoGUI(boolean includeAll, boolean extendedOutput) {

    List<ArchiveInfo> archives = getRemoteArchives_NoGUI(includeAll);

    mSdkLog.info("Packages available for installation or update: %1$d\n", archives.size());

    int index = 1;
    for (ArchiveInfo ai : archives) {
      Archive a = ai.getNewArchive();
      if (a != null) {
        Package p = a.getParentPackage();
        if (p != null) {
          if (extendedOutput) {
            mSdkLog.info("----------\n");
            mSdkLog.info("id: %1$d or \"%2$s\"\n", index, p.installId());
            mSdkLog.info("     Type: %1$s\n", p.getClass().getSimpleName().replaceAll("Package", "")); //$NON-NLS-1$ //$NON-NLS-2$
            String desc = LineUtil.reformatLine("     Desc: %s\n", p.getLongDescription());
            mSdkLog.info("%s", desc); //$NON-NLS-1$
          }
          else {
            mSdkLog.info("%1$ 4d- %2$s\n", index, p.getShortDescription());
          }
          index++;
        }
      }
    }
  }

  /**
   * Tries to update all the *existing* local packages.
   * This version *requires* to be run with a GUI.
   * <p/>
   * There are two modes of operation:
   * <ul>
   * <li>If selectedArchives is null, refreshes all sources, compares the available remote
   * packages with the current local ones and suggest updates to be done to the user (including
   * new platforms that the users doesn't have yet).
   * <li>If selectedArchives is not null, this represents a list of archives/packages that
   * the user wants to install or update, so just process these.
   * </ul>
   *
   * @param selectedArchives The list of remote archives to consider for the update.
   *                         This can be null, in which case a list of remote archive is fetched from all
   *                         available sources.
   * @param includeObsoletes True if obsolete packages should be used when resolving what
   *                         to update.
   * @param flags            Optional flags for the installer, such as {@link #NO_TOOLS_MSG}.
   * @return A list of archives that have been installed. Can be null if nothing was done.
   */
  public List<Archive> updateOrInstallAll_WithGUI(Collection<Archive> selectedArchives, boolean includeObsoletes, int flags) {
    // FIXME revisit this logic. This is just an transitional implementation
    // while I refactor the way the sdk manager works internally.

    SdkUpdaterLogic ul = new SdkUpdaterLogic(this);
    List<ArchiveInfo> archives = ul.computeUpdates(selectedArchives, getSources(), getLocalSdkParser().getPackages(), includeObsoletes);

    if (selectedArchives == null) {
      getPackageLoader().loadRemoteAddonsList(new NullTaskMonitor(getSdkLog()));
      ul.addNewPlatforms(archives, getSources(), getLocalSdkParser().getPackages(), includeObsoletes);
    }

    Collections.sort(archives);

    if (archives.size() > 0) {
      return installArchives(archives, flags);
    }
    return null;
  }

  /**
   * Tries to update all the *existing* local packages.
   * This version is intended to run without a GUI and
   * only outputs to the current {@link ILogger}.
   *
   * @param pkgFilter     A list of {@link SdkRepoConstants#NODES} or {@link Package#installId()}
   *                      or package indexes to limit the packages we can update or install.
   *                      A null or empty list means to update everything possible.
   * @param includeAll    True to list and install all packages, including obsolete ones.
   * @param dryMode       True to check what would be updated/installed but do not actually
   *                      download or install anything.
   * @param acceptLicense SDK licenses to automatically accept.
   * @return A list of archives that have been installed. Can be null if nothing was done.
   * @deprecated Use {@link #updateOrInstallAll_NoGUI(Collection, boolean, boolean, String, boolean)}
   * instead
   */
  @Deprecated
  public List<Archive> updateOrInstallAll_NoGUI(Collection<String> pkgFilter, boolean includeAll, boolean dryMode, String acceptLicense) {
    return updateOrInstallAll_NoGUI(pkgFilter, includeAll, dryMode, acceptLicense, false);
  }

  /**
   * Tries to update all the *existing* local packages.
   * This version is intended to run without a GUI and
   * only outputs to the current {@link ILogger}.
   *
   * @param pkgFilter           A list of {@link SdkRepoConstants#NODES} or {@link Package#installId()}
   *                            or package indexes to limit the packages we can update or install.
   *                            A null or empty list means to update everything possible.
   * @param includeAll          True to list and install all packages, including obsolete ones.
   * @param dryMode             True to check what would be updated/installed but do not actually
   *                            download or install anything.
   * @param acceptLicense       SDK licenses to automatically accept.
   * @param includeDependencies If true, also include any required dependencies
   * @return A list of archives that have been installed. Can be null if nothing was done.
   */
  public List<Archive> updateOrInstallAll_NoGUI(Collection<String> pkgFilter,
                                                boolean includeAll,
                                                boolean dryMode,
                                                String acceptLicense,
                                                boolean includeDependencies) {

    List<ArchiveInfo> archives = getRemoteArchives_NoGUI(includeAll);

    // Filter the selected archives to only keep the ones matching the filter
    if (pkgFilter != null && pkgFilter.size() > 0 && archives != null && archives.size() > 0) {
      // Map filter types to an SdkRepository Package type,
      // e.g. create a map "platform" => PlatformPackage.class
      HashMap<String, Class<? extends Package>> pkgMap = new HashMap<String, Class<? extends Package>>();

      mapFilterToPackageClass(pkgMap, SdkRepoConstants.NODES);
      mapFilterToPackageClass(pkgMap, SdkAddonConstants.NODES);

      // Prepare a map install-id => package instance
      HashMap<String, Package> installIdMap = new HashMap<String, Package>();
      for (ArchiveInfo ai : archives) {
        Archive a = ai.getNewArchive();
        if (a != null) {
          Package p = a.getParentPackage();
          if (p != null) {
            String iid = p.installId().toLowerCase(Locale.US);
            if (iid != null && iid.length() > 0 && !installIdMap.containsKey(iid)) {
              installIdMap.put(iid, p);
            }
          }
        }
      }

      // Now intersect this with the pkgFilter requested by the user, in order to
      // only keep the classes that the user wants to install.
      // We also create a set with the package indices requested by the user
      // and a set of install-ids requested by the user.

      HashSet<Class<? extends Package>> userFilteredClasses = new HashSet<Class<? extends Package>>();
      SparseIntArray userFilteredIndices = new SparseIntArray();
      Set<String> userFilteredInstallIds = new HashSet<String>();

      for (String iid : pkgFilter) {
        // The install-id is not case-sensitive.
        iid = iid.toLowerCase(Locale.US);

        if (installIdMap.containsKey(iid)) {
          userFilteredInstallIds.add(iid);

        }
        else if (iid.replaceAll("[0-9]+", "").length() == 0) {//$NON-NLS-1$ //$NON-NLS-2$
          // An all-digit number is a package index requested by the user.
          int index = Integer.parseInt(iid);
          userFilteredIndices.put(index, index);

        }
        else if (pkgMap.containsKey(iid)) {
          userFilteredClasses.add(pkgMap.get(iid));

        }
        else {
          // This should not happen unless there's a mismatch in the package map.
          mSdkLog.error(null, "Ignoring unknown package filter '%1$s'", iid);
        }
      }

      // we don't need the maps anymore
      pkgMap = null;
      installIdMap = null;

      // Now filter the remote archives list to keep:
      // - any package which class matches userFilteredClasses
      // - any package index which matches userFilteredIndices
      // - any package install id which matches userFilteredInstallIds

      int index = 1;
      for (Iterator<ArchiveInfo> it = archives.iterator(); it.hasNext(); ) {
        boolean keep = false;
        ArchiveInfo ai = it.next();
        Archive a = ai.getNewArchive();
        if (a != null) {
          Package p = a.getParentPackage();
          if (p != null) {
            if (userFilteredInstallIds.contains(p.installId().toLowerCase(Locale.US)) ||
                userFilteredClasses.contains(p.getClass()) ||
                userFilteredIndices.get(index) > 0) {
              keep = true;
            }

            index++;
          }
        }

        if (!keep) {
          it.remove();
        }
      }

      if (archives.isEmpty()) {
        mSdkLog.info(LineUtil.reflowLine(
          "Warning: The package filter removed all packages. There is nothing to install.\nPlease consider trying to update again without a package filter.\n"));
        return null;
      }
    }

    if (archives != null && !archives.isEmpty()) {
      if (includeDependencies) {
        List<ArchiveInfo> dependencies = getDependencies(archives);
        if (!dependencies.isEmpty()) {
          List<ArchiveInfo> combined = Lists.newArrayList();
          combined.addAll(dependencies);
          combined.addAll(archives);
          archives = combined;
        }
      }
      if (dryMode) {
        mSdkLog.info("Packages selected for install:\n");
        for (ArchiveInfo ai : archives) {
          Archive a = ai.getNewArchive();
          if (a != null) {
            Package p = a.getParentPackage();
            if (p != null) {
              mSdkLog.info("- %1$s\n", p.getShortDescription());
            }
          }
        }
        mSdkLog.info("\nDry mode is on so nothing is actually being installed.\n");
      }
      else {
        if (acceptLicense(archives, acceptLicense, 100 /* numRetries */)) {
          return installArchives(archives, NO_TOOLS_MSG);
        }
      }
    }
    else {
      mSdkLog.info("There is nothing to install or update.\n");
    }

    return null;
  }

  /**
   * Computes the transitive dependencies of the given list of archives. This will only
   * include dependencies that also need to be installed, not satisfied dependencies.
   */
  private static List<ArchiveInfo> getDependencies(@NonNull List<ArchiveInfo> archives) {
    List<ArchiveInfo> dependencies = Lists.newArrayList();
    for (ArchiveInfo archive : archives) {
      addDependencies(dependencies, archive, Sets.<ArchiveInfo>newHashSet());
    }
    return dependencies;
  }

  private static void addDependencies(@NonNull List<ArchiveInfo> dependencies,
                                      @NonNull ArchiveInfo archive,
                                      @NonNull Set<ArchiveInfo> visited) {
    if (visited.contains(archive)) {
      return;
    }
    visited.add(archive);

    ArchiveInfo[] dependsOn = archive.getDependsOn();
    if (dependsOn != null) {
      for (ArchiveInfo dependency : dependsOn) {
        if (!dependencies.contains(dependency)) {
          dependencies.add(dependency);
          addDependencies(dependencies, dependency, visited);
        }
      }
    }
  }

  /**
   * Validates that all archive licenses are accepted.
   * <p/>
   * There are 2 cases: <br/>
   * - When {@code acceptLicenses} is given, the licenses specified are automatically
   * accepted and all those not specified are automatically rejected. <br/>
   * - When {@code acceptLicenses} is empty or null, licenses are collected and there's
   * an input prompt on StdOut to ask a yes/no question. To output, this uses the
   * current {@link #mSdkLog} which should be configured to send
   * {@link ILogger#info(String, Object...)} directly to {@link System#out}. <br/>
   * <p/>
   * Finally only accepted licenses are kept in the archive list.
   *
   * @param archives         The archives to validate.
   * @param acceptLicenseIds A comma-separated list of licenses ids already approved.
   * @param numRetries       The number of times the command-line will ask to accept a given
   *                         license when the input doesn't match the expected y/n/yes/no answer.
   *                         Use 0 for infinite. Useful for unit-tests. Once the number of retries
   *                         is reached, the license is assumed as rejected.
   * @return True if there are any archives left to install.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  boolean acceptLicense(List<ArchiveInfo> archives, String acceptLicenseIds, final int numRetries) {
    TreeSet<String> acceptedLids = new TreeSet<String>();
    if (acceptLicenseIds != null) {
      acceptedLids.addAll(Arrays.asList(acceptLicenseIds.split(",")));  //$NON-NLS-1$
    }
    boolean automated = !acceptedLids.isEmpty();

    TreeSet<String> rejectedLids = new TreeSet<String>();
    TreeMap<String, License> lidToAccept = new TreeMap<String, License>();
    TreeMap<String, List<String>> lidPkgNames = new TreeMap<String, List<String>>();

    // Find the licenses needed. Include those already accepted.
    for (ArchiveInfo ai : archives) {
      License lic = getArchiveInfoLicense(ai);
      if (lic == null) {
        continue;
      }
      String lid = getLicenseId(lic);
      if (!acceptedLids.contains(lid)) {
        if (automated) {
          // Automatically reject those not already accepted
          rejectedLids.add(lid);
        }
        else {
          // Queue it to ask for it to be accepted
          lidToAccept.put(lid, lic);
          List<String> list = lidPkgNames.get(lid);
          if (list == null) {
            list = new ArrayList<String>();
            lidPkgNames.put(lid, list);
          }
          list.add(ai.getShortDescription());
        }
      }
    }

    // Ask for each license that needs to be asked manually for confirmation
    nextEntry:
    for (Map.Entry<String, License> entry : lidToAccept.entrySet()) {
      String lid = entry.getKey();
      License lic = entry.getValue();
      mSdkLog.info("-------------------------------\n");
      mSdkLog.info("License id: %1$s\n", lid);
      mSdkLog.info("Used by: \n - %1$s\n", Joiner.on("\n  - ").skipNulls().join(lidPkgNames.get(lid)));
      mSdkLog.info("-------------------------------\n\n");
      mSdkLog.info("%1$s\n", lic.getLicense());

      int retries = numRetries;
      tryAgain:
      while (true) {
        try {
          mSdkLog.info("Do you accept the license '%1$s' [y/n]: ", lid);

          byte[] buffer = new byte[256];
          if (mSdkLog instanceof IReaderLogger) {
            ((IReaderLogger)mSdkLog).readLine(buffer);
          }
          else {
            System.in.read(buffer);
          }
          mSdkLog.info("\n");

          String reply = new String(buffer, Charsets.UTF_8);
          reply = reply.trim().toLowerCase(Locale.US);

          if ("y".equals(reply) || "yes".equals(reply)) {
            acceptedLids.add(lid);
            continue nextEntry;

          }
          else if ("n".equals(reply) || "no".equals(reply)) {
            break tryAgain;

          }
          else {
            mSdkLog.info("Unknown response '%1$s'.\n", reply);
            if (--retries == 0) {
              mSdkLog.info("Max number of retries exceeded. Rejecting '%1$s'\n", lid);
              break tryAgain;
            }
            continue tryAgain;
          }

        }
        catch (IOException e) {
          // Panic. Don't install anything.
          e.printStackTrace();
          return false;
        }
      }
      rejectedLids.add(lid);
    }

    // Finally remove all archive which license is rejected or not accepted.
    for (Iterator<ArchiveInfo> it = archives.iterator(); it.hasNext(); ) {
      ArchiveInfo ai = it.next();
      License lic = getArchiveInfoLicense(ai);
      if (lic == null) {
        continue;
      }
      String lid = getLicenseId(lic);
      if (rejectedLids.contains(lid) || !acceptedLids.contains(lid)) {
        mSdkLog.info("Package %1$s not installed due to rejected license '%2$s'.\n", ai.getShortDescription(), lid);
        it.remove();
      }
    }


    return !archives.isEmpty();
  }

  private License getArchiveInfoLicense(ArchiveInfo ai) {
    Archive a = ai.getNewArchive();
    if (a != null) {
      Package p = a.getParentPackage();
      if (p != null) {
        License lic = p.getLicense();
        if (lic != null &&
            lic.getLicenseRef() != null &&
            lic.getLicense().length() > 0 &&
            lic.getLicense() != null &&
            lic.getLicense().length() > 0) {
          return lic;
        }
      }
    }

    return null;
  }

  private String getLicenseId(License lic) {
    return String.format("%1$s-%2$08x",       //$NON-NLS-1$
                         lic.getLicenseRef(), lic.getLicense().hashCode());
  }

  @SuppressWarnings("unchecked")
  private void mapFilterToPackageClass(HashMap<String, Class<? extends Package>> inOutPkgMap, String[] nodes) {

    // Automatically find the classes matching the node names
    ClassLoader classLoader = getClass().getClassLoader();
    String basePackage = Package.class.getPackage().getName();

    for (String node : nodes) {
      // Capitalize the name
      String name = node.substring(0, 1).toUpperCase() + node.substring(1);

      // We can have one dash at most in a name. If it's present, we'll try
      // with the dash or with the next letter capitalized.
      int dash = name.indexOf('-');
      if (dash > 0) {
        name = name.replaceFirst("-", "");
      }

      for (int alternatives = 0; alternatives < 2; alternatives++) {

        String fqcn = basePackage + '.' + name + "Package";  //$NON-NLS-1$
        try {
          Class<? extends Package> clazz = (Class<? extends Package>)classLoader.loadClass(fqcn);
          if (clazz != null) {
            inOutPkgMap.put(node, clazz);
            continue;
          }
        }
        catch (Throwable ignore) {
        }

        if (alternatives == 0 && dash > 0) {
          // Try an alternative where the next letter after the dash
          // is converted to an upper case.
          name = name.substring(0, dash) +
                 name.substring(dash, dash + 1).toUpperCase() +
                 name.substring(dash + 1);
        }
        else {
          break;
        }
      }
    }
  }

  /**
   * Refresh all sources. This is invoked either internally (reusing an existing monitor)
   * or as a UI callback on the remote page "Refresh" button (in which case the monitor is
   * null and a new task should be created.)
   *
   * @param forceFetching When true, load sources that haven't been loaded yet.
   *                      When false, only refresh sources that have been loaded yet.
   */
  public void refreshSources(final boolean forceFetching) {
    assert mTaskFactory != null;

    final boolean forceHttp = getSettingsController().getSettings().getForceHttp();

    mTaskFactory.start("Refresh Sources", new ITask() {
      @Override
      public void run(ITaskMonitor monitor) {

        getPackageLoader().loadRemoteAddonsList(monitor);

        SdkSource[] sources = getSources().getAllSources();
        monitor.setDescription("Refresh Sources");
        monitor.setProgressMax(monitor.getProgress() + sources.length);
        for (SdkSource source : sources) {
          if (forceFetching ||
              source.getPackages() != null ||
              source.getFetchError() != null) {
            source.load(getDownloadCache(), monitor.createSubMonitor(1), forceHttp);
          }
          monitor.incProgress(1);
        }
      }
    });
  }

  /**
   * Safely invoke all the registered {@link ISdkChangeListener#onSdkLoaded()}.
   * This can be called from any thread.
   */
  public void broadcastOnSdkLoaded() {
    if (mListeners.size() > 0) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          for (ISdkChangeListener listener : mListeners) {
            try {
              listener.onSdkLoaded();
            }
            catch (Throwable t) {
              mSdkLog.error(t, null);
            }
          }
        }
      });
    }
  }

  /**
   * Safely invoke all the registered {@link ISdkChangeListener#onSdkReload()}.
   * This can be called from any thread.
   */
  private void broadcastOnSdkReload() {
    if (mListeners.size() > 0) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          for (ISdkChangeListener listener : mListeners) {
            try {
              listener.onSdkReload();
            }
            catch (Throwable t) {
              mSdkLog.error(t, null);
            }
          }
        }
      });
    }
  }

  /**
   * Safely invoke all the registered {@link ISdkChangeListener#preInstallHook()}.
   * This can be called from any thread.
   */
  private void broadcastPreInstallHook() {
    if (mListeners.size() > 0) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          for (ISdkChangeListener listener : mListeners) {
            try {
              listener.preInstallHook();
            }
            catch (Throwable t) {
              mSdkLog.error(t, null);
            }
          }
        }
      });
    }
  }

  /**
   * Safely invoke all the registered {@link ISdkChangeListener#postInstallHook()}.
   * This can be called from any thread.
   */
  private void broadcastPostInstallHook() {
    if (mListeners.size() > 0) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          for (ISdkChangeListener listener : mListeners) {
            try {
              listener.postInstallHook();
            }
            catch (Throwable t) {
              mSdkLog.error(t, null);
            }
          }
        }
      });
    }
  }

  /**
   * Internal helper to return a new {@link ArchiveInstaller}.
   * This allows us to override the installer for unit-testing.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected ArchiveInstaller createArchiveInstaler() {
    return new ArchiveInstaller();
  }
}
