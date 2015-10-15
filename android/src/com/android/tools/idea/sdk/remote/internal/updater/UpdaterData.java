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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.ISdkChangeListener;
import com.android.sdklib.repository.License;
import com.android.sdklib.util.LineUtil;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.*;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.archives.ArchiveInstaller;
import com.android.tools.idea.sdk.remote.internal.packages.RemoteAddonPkgInfo;
import com.android.tools.idea.sdk.remote.internal.packages.PlatformToolRemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.packages.RemoteToolPkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSourceCategory;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.utils.ILogger;
import com.android.utils.IReaderLogger;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * Data shared by the SDK Manager updaters.
 */
public class UpdaterData {

  public static final int NO_TOOLS_MSG = 0;
  public static final int TOOLS_MSG_UPDATED_FROM_ADT = 1;
  public static final int TOOLS_MSG_UPDATED_FROM_SDKMAN = 2;

  private String mOsSdkRoot;

  /**
   * Holds all sources. Do not use this directly.
   * Instead use {@link #getSources()} so that unit tests can override this as needed.
   */
  private final SdkSources mSources = new SdkSources();
  private final ArrayList<ISdkChangeListener> mListeners = new ArrayList<ISdkChangeListener>();
  private final ILogger mSdkLog;
  private ITaskFactory mTaskFactory;

  private SdkManager mSdkManager;
  /**
   * The current {@link DownloadCache} to use.
   * Lazily created in {@link #getDownloadCache()}.
   */
  private DownloadCache mDownloadCache;

  /**
   * Creates a new updater data.
   *
   * @param sdkLog    Logger. Cannot be null.
   * @param osSdkRoot The OS path to the SDK root.
   */
  public UpdaterData(String osSdkRoot, ILogger sdkLog) {
    mOsSdkRoot = osSdkRoot;
    mSdkLog = sdkLog;

    initSdk();
  }

  // ----- getters, setters ----

  public String getOsSdkRoot() {
    return mOsSdkRoot;
  }

  public DownloadCache getDownloadCache() {
    if (mDownloadCache == null) {
      mDownloadCache = new DownloadCache(
        SettingsController.getInstance().getUseDownloadCache() ? DownloadCache.Strategy.FRESH_CACHE : DownloadCache.Strategy.DIRECT);
    }
    return mDownloadCache;
  }

  public void setTaskFactory(ITaskFactory taskFactory) {
    mTaskFactory = taskFactory;
  }

  public SdkManager getSdkManager() {
    return mSdkManager;
  }

  /**
   * Removes a listener ({@link ISdkChangeListener}) that is notified when the SDK is reloaded.
   */
  public void removeListener(ISdkChangeListener listener) {
    mListeners.remove(listener);
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
    // Load the conventional sources.
    // For testing, the env var can be set to replace the default root download URL.
    // It must end with a / and its the location where the updater will look for
    // the repository.xml, addons_list.xml and such files.

    String baseUrl = System.getenv("SDK_TEST_BASE_URL");                        //$NON-NLS-1$
    if (baseUrl == null || baseUrl.length() <= 0 || !baseUrl.endsWith("/")) {   //$NON-NLS-1$
      baseUrl = SdkRepoConstants.URL_GOOGLE_SDK_SITE;
    }

    mSources.add(SdkSourceCategory.ANDROID_REPO, new SdkRepoSource(baseUrl, SdkSourceCategory.ANDROID_REPO.getUiName()));

    // Load user sources (this will also notify change listeners but this operation is
    // done early enough that there shouldn't be any anyway.)
    mSources.loadUserAddons(mSdkLog);
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

    final boolean forceHttp = SettingsController.getInstance().getForceHttp();

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
              numInstalled++;

              // Check if we successfully installed a platform-tool or add-on package.
              if (archive.getParentPackage() instanceof RemoteAddonPkgInfo) {
                installedAddon = true;
              }
              else if (archive.getParentPackage() instanceof RemoteToolPkgInfo) {
                installedTools = true;
              }
              else if (archive.getParentPackage() instanceof PlatformToolRemotePkgInfo) {
                installedPlatformTools = true;
              }
            }

          }
          catch (ProcessCanceledException e) {
            // A valid exception that shouldn't be logged in the monitor. Propagate it back up.
            throw e;
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
    if (!SettingsController.getInstance().getAskBeforeAdbRestart()) {
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

    List<ArchiveInfo> archives = getRemoteArchives(includeAll);

    // Filter the selected archives to only keep the ones matching the filter
    if (pkgFilter != null && pkgFilter.size() > 0 && archives != null && archives.size() > 0) {
      // Prepare a map install-id => package instance
      HashSet<String> installIds = new HashSet<String>();
      for (ArchiveInfo ai : archives) {
        Archive a = ai.getNewArchive();
        if (a != null) {
          RemotePkgInfo p = a.getParentPackage();
          if (p != null) {
            String iid = p.installId().toLowerCase(Locale.US);
            if (iid.length() > 0 && !installIds.contains(iid)) {
              installIds.add(iid);
            }
          }
        }
      }

      // Now intersect this with the pkgFilter requested by the user, in order to
      // only keep the classes that the user wants to install.
      // We also create a set with the package indices requested by the user
      // and a set of install-ids requested by the user.

      Set<String> userFilteredInstallIds = new HashSet<String>();

      for (String iid : pkgFilter) {
        // The install-id is not case-sensitive.
        iid = iid.toLowerCase(Locale.US);

        if (installIds.contains(iid)) {
          userFilteredInstallIds.add(iid);

        }
        else {
          // This should not happen unless there's a mismatch in the package map.
          mSdkLog.error(null, "Ignoring unknown package filter '%1$s'", iid);
        }
      }

      // Now filter the remote archives list to keep:
      // - any package which class matches userFilteredClasses
      // - any package index which matches userFilteredIndices
      // - any package install id which matches userFilteredInstallIds

      for (Iterator<ArchiveInfo> it = archives.iterator(); it.hasNext(); ) {
        boolean keep = false;
        ArchiveInfo ai = it.next();
        Archive a = ai.getNewArchive();
        if (a != null) {
          RemotePkgInfo p = a.getParentPackage();
          if (p != null) {
            if (userFilteredInstallIds.contains(p.installId().toLowerCase(Locale.US))) {
              keep = true;
            }
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
            RemotePkgInfo p = a.getParentPackage();
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

  private List<ArchiveInfo> getRemoteArchives(boolean includeAll) {
    SdkState state = SdkState.getInstance(AndroidSdkData.getSdkData(mOsSdkRoot));
    state.loadSynchronously(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, null, null, false);
    List<ArchiveInfo> result = Lists.newArrayList();
    for (UpdatablePkgInfo update : state.getPackages().getConsolidatedPkgs().values()) {
      for (RemotePkgInfo remote : update.getAllRemotes()) {
        if (includeAll || !remote.isObsolete()) {
          for (Archive archive : remote.getArchives()) {
            if (archive.isCompatible()) {
              result.add(new ArchiveInfo(archive, update.getLocalInfo(), null));
            }
          }
        }
      }
    }
    return result;
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
      RemotePkgInfo p = a.getParentPackage();
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
