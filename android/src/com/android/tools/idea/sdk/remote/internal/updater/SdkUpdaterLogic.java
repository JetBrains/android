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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.sdk.remote.internal.ITask;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.AddonPackage;
import com.android.tools.idea.sdk.remote.internal.packages.BuildToolPackage;
import com.android.tools.idea.sdk.remote.internal.packages.DocPackage;
import com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage;
import com.android.tools.idea.sdk.remote.internal.packages.IAndroidVersionProvider;
import com.android.tools.idea.sdk.remote.internal.packages.IExactApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.IMinApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.IMinPlatformToolsDependency;
import com.android.tools.idea.sdk.remote.internal.packages.IMinToolsDependency;
import com.android.tools.idea.sdk.remote.internal.packages.IPlatformDependency;
import com.android.tools.idea.sdk.remote.internal.packages.MinToolsPackage;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.Package.UpdateInfo;
import com.android.tools.idea.sdk.remote.internal.packages.PlatformPackage;
import com.android.tools.idea.sdk.remote.internal.packages.PlatformToolPackage;
import com.android.tools.idea.sdk.remote.internal.packages.SamplePackage;
import com.android.tools.idea.sdk.remote.internal.packages.SystemImagePackage;
import com.android.tools.idea.sdk.remote.internal.packages.ToolPackage;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.sdk.remote.internal.updater.ArchiveInfo;
import com.android.tools.idea.sdk.remote.internal.updater.IUpdaterData;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IdDisplay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The logic to compute which packages to install, based on the choices
 * made by the user. This adds required packages as needed.
 * <p/>
 * When the user doesn't provide a selection, looks at local package to find
 * those that can be updated and compute dependencies too.
 */
public class SdkUpdaterLogic {

    private final com.android.tools.idea.sdk.remote.internal.updater.IUpdaterData mUpdaterData;

    public SdkUpdaterLogic(IUpdaterData updaterData) {
        mUpdaterData = updaterData;
    }

    /**
     * Retrieves an unfiltered list of all remote archives.
     * The archives are guaranteed to be compatible with the current platform.
     */
    public List<ArchiveInfo> getAllRemoteArchives(
            SdkSources sources,
            Package[] localPkgs,
            boolean includeAll) {

        List<Package> remotePkgs = new ArrayList<Package>();
        SdkSource[] remoteSources = sources.getAllSources();
        fetchRemotePackages(remotePkgs, remoteSources);

        ArrayList<Archive> archives = new ArrayList<Archive>();
        for (Package remotePkg : remotePkgs) {
            // Only look for non-obsolete updates unless requested to include them
            if (includeAll || !remotePkg.isObsolete()) {
                // Found a suitable update. Only accept the remote package
                // if it provides at least one compatible archive

                addArchives:
                for (Archive a : remotePkg.getArchives()) {
                    if (a.isCompatible()) {

                        // If we're trying to add a package for revision N,
                        // make sure we don't also have a package for revision N-1.
                        for (int i = archives.size() - 1; i >= 0; i--) {
                            Package pkgFound = archives.get(i).getParentPackage();
                            if (pkgFound.canBeUpdatedBy(remotePkg) == UpdateInfo.UPDATE) {
                                // This package can update one we selected earlier.
                                // Remove the one that can be updated by this new one.
                                archives.remove(i);
                            } else if (remotePkg.canBeUpdatedBy(pkgFound) == UpdateInfo.UPDATE) {
                                // There is a package in the list that is already better
                                // than the one we want to add, so don't add it.
                                break addArchives;
                            }
                        }

                        archives.add(a);
                        break;
                    }
                }
            }
        }

        ArrayList<ArchiveInfo> result = new ArrayList<ArchiveInfo>();

        ArchiveInfo[] localArchives = createLocalArchives(localPkgs);

        for (Archive a : archives) {
            insertArchive(a,
                    result,
                    archives,
                    remotePkgs,
                    remoteSources,
                    localArchives,
                    false /*automated*/);
        }

        return result;
    }

    /**
     * Compute which packages to install by taking the user selection
     * and adding required packages as needed.
     *
     * When the user doesn't provide a selection, looks at local packages to find
     * those that can be updated and compute dependencies too.
     */
    public List<ArchiveInfo> computeUpdates(
            Collection<Archive> selectedArchives,
            SdkSources sources,
            Package[] localPkgs,
            boolean includeAll) {

        List<ArchiveInfo> archives = new ArrayList<ArchiveInfo>();
        List<Package>   remotePkgs = new ArrayList<Package>();
        SdkSource[] remoteSources = sources.getAllSources();

        // Create ArchiveInfos out of local (installed) packages.
        ArchiveInfo[] localArchives = createLocalArchives(localPkgs);

        // If we do not have a specific list of archives to install (that is the user
        // selected "update all" rather than request specific packages), then we try to
        // find updates based on the *existing* packages.
        if (selectedArchives == null) {
            selectedArchives = findUpdates(
                    localArchives,
                    remotePkgs,
                    remoteSources,
                    includeAll);
        }

        // Once we have a list of packages to install, we try to solve all their
        // dependencies by automatically adding them to the list of things to install.
        // This works on the list provided either by the user directly or the list
        // computed from potential updates.
        for (Archive a : selectedArchives) {
            insertArchive(a,
                    archives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives,
                    false /*automated*/);
        }

        // Finally we need to look at *existing* packages which are not being updated
        // and check if they have any missing dependencies and suggest how to fix
        // these dependencies.
        fixMissingLocalDependencies(
                archives,
                selectedArchives,
                remotePkgs,
                remoteSources,
                localArchives);

        return archives;
    }

    private double getRevisionRank(FullRevision rev) {
        int p = rev.isPreview() ? 999 : 999 - rev.getPreview();
        return  rev.getMajor() +
                rev.getMinor() / 1000.d +
                rev.getMicro() / 1000000.d +
                p              / 1000000000.d;
    }

    /**
     * Finds new packages that the user does not have in his/her local SDK
     * and adds them to the list of archives to install.
     * <p/>
     * The default is to only find "new" platforms, that is anything more
     * recent than the highest platform currently installed.
     * A side effect is that for an empty SDK install this will list *all*
     * platforms available (since there's no "highest" installed platform.)
     * <p/>
     * This also adds "silent" dependencies. For example the user probably
     * needs to have at least one version of the build-tools package although
     * there is nothing that directly depends on it. So if the user doesn't have
     * any installed or selected version, selected the most recent one as a
     * candidate for install.
     *
     * @param archives The in-out list of archives to install. Typically the
     *  list is not empty at first as it should contain any archives that is
     *  already scheduled for install. This method will add to the list.
     * @param sources The list of all sources, to fetch them as necessary.
     * @param localPkgs The list of all currently installed packages.
     * @param includeAll When true, this will list all platforms.
     * (included these lower than the highest installed one) as well as
     * all obsolete packages of these platforms.
     */
    public void addNewPlatforms(
            Collection<ArchiveInfo> archives,
            SdkSources sources,
            Package[] localPkgs,
            boolean includeAll) {

        // Create ArchiveInfos out of local (installed) packages.
        ArchiveInfo[] localArchives = createLocalArchives(localPkgs);

        // Find the highest platform installed
        double currentBuildToolScore = 0;
        double currentPlatformScore = 0;
        double currentSampleScore = 0;
        double currentAddonScore = 0;
        double currentDocScore = 0;
        HashMap<String, Double> currentExtraScore = new HashMap<String, Double>();
        if (!includeAll) {
            if (localPkgs != null) {
                for (Package p : localPkgs) {
                    double rev = getRevisionRank(p.getRevision());
                    int api = 0;
                    boolean isPreview = false;
                    if (p instanceof IAndroidVersionProvider) {
                        AndroidVersion vers = ((IAndroidVersionProvider) p).getAndroidVersion();
                        api = vers.getApiLevel();
                        isPreview = vers.isPreview();
                    }

                    // The score is 1000*api + (999 if preview) + rev
                    // This allows previews to rank above a non-preview and
                    // allows revisions to rank appropriately.
                    double score = api * 1000 + (isPreview ? 999 : 0) + rev;

                    if (p instanceof BuildToolPackage) {
                        currentBuildToolScore = Math.max(currentBuildToolScore, score);
                    } else if (p instanceof PlatformPackage) {
                        currentPlatformScore = Math.max(currentPlatformScore, score);
                    } else if (p instanceof SamplePackage) {
                        currentSampleScore = Math.max(currentSampleScore, score);
                    } else if (p instanceof AddonPackage) {
                        currentAddonScore = Math.max(currentAddonScore, score);
                    } else if (p instanceof ExtraPackage) {
                        currentExtraScore.put(((ExtraPackage) p).getPath(), score);
                    } else if (p instanceof DocPackage) {
                        currentDocScore = Math.max(currentDocScore, score);
                    }
                }
            }
        }

        SdkSource[] remoteSources = sources.getAllSources();
        ArrayList<Package> remotePkgs = new ArrayList<Package>();
        fetchRemotePackages(remotePkgs, remoteSources);

        Package suggestedDoc = null;
        Package suggestedBuildTool = null;

        for (Package p : remotePkgs) {
            // Skip obsolete packages unless requested to include them.
            if (p.isObsolete() && !includeAll) {
                continue;
            }

            double rev = getRevisionRank(p.getRevision());
            int api = 0;
            boolean isPreview = false;
            if (p instanceof IAndroidVersionProvider) {
                AndroidVersion vers = ((IAndroidVersionProvider) p).getAndroidVersion();
                api = vers.getApiLevel();
                isPreview = vers.isPreview();
            }

            double score = api * 1000 + (isPreview ? 999 : 0) + rev;

            boolean shouldAdd = false;
            if (p instanceof BuildToolPackage) {
                // We don't want all the build-tool packages, only the most recent one
                // if not is currently installed.
                if (currentBuildToolScore == 0 && score > currentBuildToolScore) {
                    suggestedBuildTool = p;
                    currentBuildToolScore = score;
                }
            } else if (p instanceof PlatformPackage) {
                shouldAdd = score > currentPlatformScore;
            } else if (p instanceof SamplePackage) {
                shouldAdd = score > currentSampleScore;
            } else if (p instanceof AddonPackage) {
                shouldAdd = score > currentAddonScore;
            } else if (p instanceof ExtraPackage) {
                String key = ((ExtraPackage) p).getPath();
                shouldAdd = !currentExtraScore.containsKey(key) ||
                    score > currentExtraScore.get(key).doubleValue();
            } else if (p instanceof DocPackage) {
                // We don't want all the doc, only the most recent one
                if (score > currentDocScore) {
                    suggestedDoc = p;
                    currentDocScore = score;
                }
            }

            if (shouldAdd) {
                // We should suggest this package for installation.
                for (Archive a : p.getArchives()) {
                    if (a.isCompatible()) {
                        insertArchive(a,
                                archives,
                                null /*selectedArchives*/,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }

            if (p instanceof PlatformPackage && (score >= currentPlatformScore)) {
                // We just added a new platform *or* we are visiting the highest currently
                // installed platform. In either case we want to make sure it either has
                // its own system image or that we provide one by default.
                PlatformPackage pp = (PlatformPackage) p;
                if (pp.getIncludedAbi() == null) {
                    for (Package p2 : remotePkgs) {
                        if (!(p2 instanceof SystemImagePackage) ||
                            ((SystemImagePackage)p2).isPlatform() ||
                            (p2.isObsolete() && !includeAll)) {
                            continue;
                        }
                        SystemImagePackage sip = (SystemImagePackage) p2;
                        if (sip.getAndroidVersion().equals(pp.getAndroidVersion())) {
                            for (Archive a : sip.getArchives()) {
                                if (a.isCompatible()) {
                                    insertArchive(a,
                                            archives,
                                            null /*selectedArchives*/,
                                            remotePkgs,
                                            remoteSources,
                                            localArchives,
                                            true /*automated*/);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (suggestedDoc != null) {
            // We should suggest this package for installation.
            for (Archive a : suggestedDoc.getArchives()) {
                if (a.isCompatible()) {
                    insertArchive(a,
                            archives,
                            null /*selectedArchives*/,
                            remotePkgs,
                            remoteSources,
                            localArchives,
                            true /*automated*/);
                }
            }
        }

        if (suggestedBuildTool != null) {
            // We should suggest this package for installation.
            for (Archive a : suggestedBuildTool.getArchives()) {
                if (a.isCompatible()) {
                    insertArchive(a,
                            archives,
                            null /*selectedArchives*/,
                            remotePkgs,
                            remoteSources,
                            localArchives,
                            true /*automated*/);
                }
            }
        }
    }

    /**
     * Create a array of {@link ArchiveInfo} based on all local (already installed)
     * packages. The array is always non-null but may be empty.
     * <p/>
     * The local {@link ArchiveInfo} are guaranteed to have one non-null archive
     * that you can retrieve using {@link ArchiveInfo#getNewArchive()}.
     */
    public ArchiveInfo[] createLocalArchives(Package[] localPkgs) {

        if (localPkgs != null) {
            ArrayList<ArchiveInfo> list = new ArrayList<ArchiveInfo>();
            for (Package p : localPkgs) {
                // Only accept packages that have one compatible archive.
                // Local package should have 1 and only 1 compatible archive anyway.
                for (Archive a : p.getArchives()) {
                    if (a != null && a.isCompatible()) {
                        // We create an "installed" archive info to wrap the local package.
                        // Note that dependencies are not computed since right now we don't
                        // deal with more than one level of dependencies and installed archives
                        // are deemed implicitly accepted anyway.
                        list.add(new LocalArchiveInfo(a));
                    }
                }
            }

            return list.toArray(new ArchiveInfo[list.size()]);
        }

        return new ArchiveInfo[0];
    }

    /**
     * Find suitable updates to all current local packages.
     * <p/>
     * Returns a list of potential updates for *existing* packages. This does NOT solve
     * dependencies for the new packages.
     * <p/>
     * Always returns a non-null collection, which can be empty.
     */
    private Collection<Archive> findUpdates(
            ArchiveInfo[] localArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            boolean includeAll) {
        ArrayList<Archive> updates = new ArrayList<Archive>();

        fetchRemotePackages(remotePkgs, remoteSources);

        for (ArchiveInfo ai : localArchives) {
            Archive na = ai.getNewArchive();
            if (na == null) {
                continue;
            }
            Package localPkg = na.getParentPackage();

            for (Package remotePkg : remotePkgs) {
                // Only look for non-obsolete updates unless requested to include them
                if ((includeAll || !remotePkg.isObsolete()) &&
                        localPkg.canBeUpdatedBy(remotePkg) == UpdateInfo.UPDATE) {
                    // Found a suitable update. Only accept the remote package
                    // if it provides at least one compatible archive

                    addArchives:
                    for (Archive a : remotePkg.getArchives()) {
                        if (a.isCompatible()) {

                            // If we're trying to add a package for revision N,
                            // make sure we don't also have a package for revision N-1.
                            for (int i = updates.size() - 1; i >= 0; i--) {
                                Package pkgFound = updates.get(i).getParentPackage();
                                if (pkgFound.canBeUpdatedBy(remotePkg) == UpdateInfo.UPDATE) {
                                    // This package can update one we selected earlier.
                                    // Remove the one that can be updated by this new one.
                                   updates.remove(i);
                                } else if (remotePkg.canBeUpdatedBy(pkgFound) ==
                                                UpdateInfo.UPDATE) {
                                    // There is a package in the list that is already better
                                    // than the one we want to add, so don't add it.
                                    break addArchives;
                                }
                            }

                            updates.add(a);
                            break;
                        }
                    }
                }
            }
        }

        return updates;
    }

    /**
     * Check all local archives which are NOT being updated and see if they
     * miss any dependency. If they do, try to fix that dependency by selecting
     * an appropriate package.
     */
    private void fixMissingLocalDependencies(
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {

        nextLocalArchive: for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            Package p = a == null ? null : a.getParentPackage();
            if (p == null) {
                continue;
            }

            // Is this local archive being updated?
            for (ArchiveInfo ai2 : outArchives) {
                if (ai2.getReplaced() == a) {
                    // this new archive will replace the current local one,
                    // so we don't have to care about fixing dependencies (since the
                    // new archive should already have had its dependencies resolved)
                    continue nextLocalArchive;
                }
            }

            // find dependencies for the local archive and add them as needed
            // to the outArchives collection.
            ArchiveInfo[] deps = findDependency(p,
                  outArchives,
                  selectedArchives,
                  remotePkgs,
                  remoteSources,
                  localArchives);

            if (deps != null) {
                // The already installed archive has a missing dependency, which we
                // just selected for install. Make sure we remember the dependency
                // so that we can enforce it later in the UI.
                for (ArchiveInfo aid : deps) {
                    aid.addDependencyFor(ai);
                }
            }
        }
    }

    private ArchiveInfo insertArchive(Archive archive,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives,
            boolean automated) {
        Package p = archive.getParentPackage();

        // Is this an update?
        Archive updatedArchive = null;
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package lp = a.getParentPackage();

                if (lp.canBeUpdatedBy(p) == UpdateInfo.UPDATE) {
                    updatedArchive = a;
                }
            }
        }

        // Find dependencies and adds them as needed to outArchives
        ArchiveInfo[] deps = findDependency(p,
                outArchives,
                selectedArchives,
                remotePkgs,
                remoteSources,
                localArchives);

        // Make sure it's not a dup
        ArchiveInfo ai = null;

        for (ArchiveInfo ai2 : outArchives) {
            Archive a2 = ai2.getNewArchive();
            if (a2 != null && a2.getParentPackage().sameItemAs(archive.getParentPackage())) {
                ai = ai2;
                break;
            }
        }

        if (ai == null) {
            ai = new ArchiveInfo(
                archive,        //newArchive
                updatedArchive, //replaced
                deps            //dependsOn
                );
            outArchives.add(ai);
        }

        if (deps != null) {
            for (ArchiveInfo d : deps) {
                d.addDependencyFor(ai);
            }
        }

        return ai;
    }

    /**
     * Resolves dependencies for a given package.
     *
     * Returns null if no dependencies were found.
     * Otherwise return an array of {@link ArchiveInfo}, which is guaranteed to have
     * at least size 1 and contain no null elements.
     */
    private ArchiveInfo[] findDependency(Package pkg,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {

        // Current dependencies can be:
        // - addon: *always* depends on platform of same API level
        // - platform: *might* depends on tools of rev >= min-tools-rev
        // - extra: *might* depends on platform with api >= min-api-level

        Set<ArchiveInfo> aiFound = new HashSet<ArchiveInfo>();

        if (pkg instanceof IPlatformDependency) {
            ArchiveInfo ai = findPlatformDependency(
                    (IPlatformDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                aiFound.add(ai);
            }
        }

        if (pkg instanceof IMinToolsDependency) {

            ArchiveInfo ai = findToolsDependency(
                    (IMinToolsDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                aiFound.add(ai);
            }
        }

        if (pkg instanceof IMinPlatformToolsDependency) {

            ArchiveInfo ai = findPlatformToolsDependency(
                    (IMinPlatformToolsDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                aiFound.add(ai);
            }
        }

        if (pkg instanceof IMinApiLevelDependency) {

            ArchiveInfo ai = findMinApiLevelDependency(
                    (IMinApiLevelDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                aiFound.add(ai);
            }
        }

        if (pkg instanceof IExactApiLevelDependency) {

            ArchiveInfo ai = findExactApiLevelDependency(
                    (IExactApiLevelDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                aiFound.add(ai);
            }
        }

        if (aiFound.size() > 0) {
            ArchiveInfo[] result = aiFound.toArray(new ArchiveInfo[aiFound.size()]);
            Arrays.sort(result);
            return result;
        }

        return null;
    }

    /**
     * Resolves dependencies on tools.
     *
     * A platform or an extra package can both have a min-tools-rev, in which case it
     * depends on having a tools package of the requested revision.
     * Finds the tools dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    public ArchiveInfo findToolsDependency(
            IMinToolsDependency pkg,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {
        // This is the requirement to match.
        FullRevision rev = pkg.getMinToolsRevision();

        if (rev.equals(MinToolsPackage.MIN_TOOLS_REV_NOT_SPECIFIED)) {
            // Well actually there's no requirement.
            return null;
        }

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof ToolPackage) {
                    if (((ToolPackage) p).getRevision().compareTo(rev) >= 0) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install
        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof ToolPackage) {
                    if (((ToolPackage) p).getRevision().compareTo(rev) >= 0) {
                        // The dependency is already scheduled for install, nothing else to do.
                        return ai;
                    }
                }
            }
        }

        // Otherwise look in the selected archives.
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof ToolPackage) {
                    if (((ToolPackage) p).getRevision().compareTo(rev) >= 0) {
                        // It's not already in the list of things to install, so add it now
                        return insertArchive(a,
                                outArchives,
                                selectedArchives,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        FullRevision localRev = rev;
        Archive localArch = null;
        for (Package p : remotePkgs) {
            if (p instanceof ToolPackage) {
                FullRevision r = ((ToolPackage) p).getRevision();
                if (r.compareTo(localRev) >= 0) {
                    // It's not already in the list of things to install, so add the
                    // first compatible archive we can find.
                    for (Archive a : p.getArchives()) {
                        if (a.isCompatible()) {
                            localRev = r;
                            localArch = a;
                            break;
                        }
                    }
                }
            }
        }
        if (localArch != null) {
            return insertArchive(localArch,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives,
                    true /*automated*/);

        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this extra depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingArchiveInfo(MissingArchiveInfo.TITLE_TOOL, rev);
    }

    /**
     * Resolves dependencies on platform-tools.
     *
     * A tool package can have a min-platform-tools-rev, in which case it depends on
     * having a platform-tool package of the requested revision.
     * Finds the platform-tool dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    public ArchiveInfo findPlatformToolsDependency(
            IMinPlatformToolsDependency pkg,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {
        // This is the requirement to match.
        FullRevision rev = pkg.getMinPlatformToolsRevision();
        boolean findMax = false;
        int compareThreshold = 0;
        ArchiveInfo aiMax = null;
        Archive aMax = null;

        if (rev.equals(IMinPlatformToolsDependency.MIN_PLATFORM_TOOLS_REV_INVALID)) {
            // The requirement is invalid, which is not supposed to happen since this
            // property is mandatory. However in a typical upgrade scenario we can end
            // up with the previous updater managing a new package and not dealing
            // correctly with the new unknown property.
            // So instead we parse all the existing and remote packages and try to find
            // the max available revision and we'll use it.
            findMax = true;
            // When findMax is false, we want r.compareTo(rev) >= 0.
            // When findMax is true, we want r.compareTo(rev) > 0 (so >= 1).
            compareThreshold = 1;
        }

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformToolPackage) {
                    FullRevision r = ((PlatformToolPackage) p).getRevision();
                    if (findMax && r.compareTo(rev) > compareThreshold) {
                        rev = r;
                        aiMax = ai;
                    } else if (!findMax && r.compareTo(rev) >= compareThreshold) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Because of previews, we can have more than 1 choice, so get the local max.
        FullRevision localRev = rev;
        ArchiveInfo localAiMax = null;
        // Look in archives already scheduled for install
        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformToolPackage) {
                    FullRevision r = ((PlatformToolPackage) p).getRevision();
                    if (r.compareTo(localRev) >= compareThreshold) {
                        localRev = r;
                        localAiMax = ai;
                    }
                }
            }
        }
        if (localAiMax != null) {
            if (findMax) {
                rev = localRev;
                aiMax = localAiMax;
            } else {
                // The dependency is already scheduled for install, nothing else to do.
                return localAiMax;
            }
        }


        // Otherwise look in the selected archives.
        localRev = rev;
        Archive localAMax = null;
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformToolPackage) {
                    FullRevision r = ((PlatformToolPackage) p).getRevision();
                    if (r.compareTo(localRev) >= compareThreshold) {
                        localRev = r;
                        localAiMax = null;
                        localAMax = a;
                    }
                }
            }
            if (localAMax != null) {
                if (findMax) {
                    rev = localRev;
                    aiMax = null;
                    aMax = localAMax;
                } else {
                    // It's not already in the list of things to install, so add it now
                    return insertArchive(localAMax,
                            outArchives,
                            selectedArchives,
                            remotePkgs,
                            remoteSources,
                            localArchives,
                            true /*automated*/);
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        localRev = rev;
        localAMax = null;
        for (Package p : remotePkgs) {
            if (p instanceof PlatformToolPackage) {
                FullRevision r = ((PlatformToolPackage) p).getRevision();
                if (r.compareTo(rev) >= 0) {
                    // Make sure there's at least one valid archive here
                    for (Archive a : p.getArchives()) {
                        if (a.isCompatible()) {
                            if (r.compareTo(localRev) >= compareThreshold) {
                                localRev = r;
                                localAiMax = null;
                                localAMax = a;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (localAMax != null) {
            if (findMax) {
                rev = localRev;
                aiMax = null;
                aMax = localAMax;
            } else {
                // It's not already in the list of things to install, so add the
                // first compatible archive we can find.
                return insertArchive(localAMax,
                        outArchives,
                        selectedArchives,
                        remotePkgs,
                        remoteSources,
                        localArchives,
                        true /*automated*/);
            }
        }

        if (findMax) {
            if (aMax != null) {
                return insertArchive(aMax,
                        outArchives,
                        selectedArchives,
                        remotePkgs,
                        remoteSources,
                        localArchives,
                        true /*automated*/);
            } else if (aiMax != null) {
                return aiMax;
            }
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this package depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingArchiveInfo(MissingArchiveInfo.TITLE_PLATFORM_TOOL, rev);
    }

    /**
     * Resolves dependencies on platform for an add-on.
     * Resolves dependencies on a system-image on its base platform or add-on.
     *
     * An add-on depends on having a platform with the same API level.
     *
     * Finds the platform dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    public ArchiveInfo findPlatformDependency(
            IPlatformDependency pkg,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {
        // This is the requirement to match.
        AndroidVersion v = pkg.getAndroidVersion();


        // The dependency package must be a PlatformPackage or an AddonPackage.
        // For an add-on, we also need to have the same vendor and name-id.
        Class<? extends Package> expectedClass = PlatformPackage.class;
        IdDisplay addonVendor = null;
        IdDisplay addonTag = null;
        if (pkg instanceof SystemImagePackage && !((SystemImagePackage) pkg).isPlatform()) {
            expectedClass = AddonPackage.class;
            addonVendor = ((SystemImagePackage) pkg).getAddonVendor();
            addonTag = ((SystemImagePackage) pkg).getTag();
        }

        // Find a platform or addon that would satisfy the requirement.

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (expectedClass.isInstance(p)) {
                    if (v.equals(((IAndroidVersionProvider) p).getAndroidVersion())) {
                        if (addonVendor != null && addonTag != null && p instanceof AddonPackage) {
                            if (!((AddonPackage) p).getVendorId().equals(addonVendor.getId()) ||
                                !((AddonPackage) p).getNameId().equals(addonTag.getId())) {
                                continue;
                            }
                        }
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install
        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (expectedClass.isInstance(p)) {
                    if (v.equals(((IAndroidVersionProvider) p).getAndroidVersion())) {
                        if (addonVendor != null && addonTag != null && p instanceof AddonPackage) {
                            if (!((AddonPackage) p).getVendorId().equals(addonVendor.getId()) ||
                                !((AddonPackage) p).getNameId().equals(addonTag.getId())) {
                                continue;
                            }
                        }
                        // The dependency is already scheduled for install, nothing else to do.
                        return ai;
                    }
                }
            }
        }

        // Otherwise look in the selected archives.
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (expectedClass.isInstance(p)) {
                    if (v.equals(((IAndroidVersionProvider) p).getAndroidVersion())) {
                        if (addonVendor != null && addonTag != null && p instanceof AddonPackage) {
                            if (!((AddonPackage) p).getVendorId().equals(addonVendor.getId()) ||
                                !((AddonPackage) p).getNameId().equals(addonTag.getId())) {
                                continue;
                            }
                        }
                        // It's not already in the list of things to install, so add it now
                        return insertArchive(a,
                                outArchives,
                                selectedArchives,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        for (Package p : remotePkgs) {
            if (expectedClass.isInstance(p)) {
                if (v.equals(((IAndroidVersionProvider) p).getAndroidVersion())) {
                    if (addonVendor != null && addonTag != null && p instanceof AddonPackage) {
                        if (!((AddonPackage) p).getVendorId().equals(addonVendor.getId()) ||
                            !((AddonPackage) p).getNameId().equals(addonTag.getId())) {
                            continue;
                        }
                    }
                    // It's not already in the list of things to install, so add the
                    // first compatible archive we can find.
                    for (Archive a : p.getArchives()) {
                        if (a.isCompatible()) {
                            return insertArchive(a,
                                    outArchives,
                                    selectedArchives,
                                    remotePkgs,
                                    remoteSources,
                                    localArchives,
                                    true /*automated*/);
                        }
                    }
                }
            }
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this addon depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingPlatformArchiveInfo(pkg.getAndroidVersion());
    }

    /**
     * Resolves platform dependencies for extras.
     * An extra depends on having a platform with a minimun API level.
     *
     * We try to return the highest API level available above the specified minimum.
     * Note that installed packages have priority so if one installed platform satisfies
     * the dependency, we'll use it even if there's a higher API platform available but
     * not installed yet.
     *
     * Finds the platform dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    protected ArchiveInfo findMinApiLevelDependency(
            IMinApiLevelDependency pkg,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {

        int api = pkg.getMinApiLevel();

        if (api == IMinApiLevelDependency.MIN_API_LEVEL_NOT_SPECIFIED) {
            return null;
        }

        // Find a platform that would satisfy the requirement.

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getAndroidVersion().isGreaterOrEqualThan(api)) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install
        int foundApi = 0;
        ArchiveInfo foundAi = null;

        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getAndroidVersion().isGreaterOrEqualThan(api)) {
                        if (api > foundApi) {
                            foundApi = api;
                            foundAi = ai;
                        }
                    }
                }
            }
        }

        if (foundAi != null) {
            // The dependency is already scheduled for install, nothing else to do.
            return foundAi;
        }

        // Otherwise look in the selected archives *or* available remote packages
        // and takes the best out of the two sets.
        foundApi = 0;
        Archive foundArchive = null;
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getAndroidVersion().isGreaterOrEqualThan(api)) {
                        if (api > foundApi) {
                            foundApi = api;
                            foundArchive = a;
                        }
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        for (Package p : remotePkgs) {
            if (p instanceof PlatformPackage) {
                if (((PlatformPackage) p).getAndroidVersion().isGreaterOrEqualThan(api)) {
                    if (api > foundApi) {
                        // It's not already in the list of things to install, so add the
                        // first compatible archive we can find.
                        for (Archive a : p.getArchives()) {
                            if (a.isCompatible()) {
                                foundApi = api;
                                foundArchive = a;
                            }
                        }
                    }
                }
            }
        }

        if (foundArchive != null) {
            // It's not already in the list of things to install, so add it now
            return insertArchive(foundArchive,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives,
                    true /*automated*/);
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this extra depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingPlatformArchiveInfo(new AndroidVersion(api, null /*codename*/));
    }

    /**
     * Resolves platform dependencies for add-ons.
     * An add-ons depends on having a platform with an exact specific API level.
     *
     * Finds the platform dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    public ArchiveInfo findExactApiLevelDependency(
            IExactApiLevelDependency pkg,
            Collection<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            Collection<Package> remotePkgs,
            SdkSource[] remoteSources,
            ArchiveInfo[] localArchives) {

        int api = pkg.getExactApiLevel();

        if (api == IExactApiLevelDependency.API_LEVEL_INVALID) {
            return null;
        }

        // Find a platform that would satisfy the requirement.

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getAndroidVersion().equals(api)) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install

        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getAndroidVersion().equals(api)) {
                        return ai;
                    }
                }
            }
        }

        // Otherwise look in the selected archives.
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getAndroidVersion().equals(api)) {
                        // It's not already in the list of things to install, so add it now
                        return insertArchive(a,
                                outArchives,
                                selectedArchives,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        for (Package p : remotePkgs) {
            if (p instanceof PlatformPackage) {
                if (((PlatformPackage) p).getAndroidVersion().equals(api)) {
                    // It's not already in the list of things to install, so add the
                    // first compatible archive we can find.
                    for (Archive a : p.getArchives()) {
                        if (a.isCompatible()) {
                            return insertArchive(a,
                                    outArchives,
                                    selectedArchives,
                                    remotePkgs,
                                    remoteSources,
                                    localArchives,
                                    true /*automated*/);
                        }
                    }
                }
            }
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this extra depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingPlatformArchiveInfo(new AndroidVersion(api, null /*codename*/));
    }

    /**
     * Fetch all remote packages only if really needed.
     * <p/>
     * This method takes a list of sources. Each source is only fetched once -- that is each
     * source keeps the list of packages that we fetched from the remote XML file. If the list
     * is null, it means this source has never been fetched so we'll do it once here. Otherwise
     * we rely on the cached list of packages from this source.
     * <p/>
     * This method also takes a remote package list as input, which it will fill out.
     * If a source has already been fetched, we'll add its packages to the remote package list
     * if they are not already present. Otherwise, the source will be fetched and the packages
     * added to the list.
     *
     * @param remotePkgs An in-out list of packages available from remote sources.
     *                   This list must not be null.
     *                   It can be empty or already contain some packages.
     * @param remoteSources A list of available remote sources to fetch from.
     */
    protected void fetchRemotePackages(
            final Collection<Package> remotePkgs,
            final SdkSource[] remoteSources) {
        if (remotePkgs.size() > 0) {
            return;
        }

        // First check if there's any remote source we need to fetch.
        // This will bring the task window, so we rather not display it unless
        // necessary.
        boolean needsFetch = false;
        for (final SdkSource remoteSrc : remoteSources) {
            Package[] pkgs = remoteSrc.getPackages();
            if (pkgs == null) {
                // This source has never been fetched. We'll do it below.
                needsFetch = true;
            } else {
                // This source has already been fetched and we know its package list.
                // We still need to make sure all of its packages are present in the
                // remotePkgs list.

                nextPackage: for (Package pkg : pkgs) {
                    for (Archive a : pkg.getArchives()) {
                        // Only add a package if it contains at least one compatible archive
                        // and is not already in the remote package list.
                        if (a.isCompatible()) {
                            if (!remotePkgs.contains(pkg)) {
                                remotePkgs.add(pkg);
                                continue nextPackage;
                            }
                        }
                    }
                }
            }
        }

        if (!needsFetch) {
            return;
        }

        final boolean forceHttp = mUpdaterData.getSettingsController().getSettings().getForceHttp();

        mUpdaterData.getTaskFactory().start("Refresh Sources", new ITask() {
            @Override
            public void run(ITaskMonitor monitor) {
                for (SdkSource remoteSrc : remoteSources) {
                    Package[] pkgs = remoteSrc.getPackages();

                    if (pkgs == null) {
                        remoteSrc.load(mUpdaterData.getDownloadCache(), monitor, forceHttp);
                        pkgs = remoteSrc.getPackages();
                    }

                    if (pkgs != null) {
                        nextPackage: for (Package pkg : pkgs) {
                            for (Archive a : pkg.getArchives()) {
                                // Only add a package if it contains at least one compatible archive
                                // and is not already in the remote package list.
                                if (a.isCompatible()) {
                                    if (!remotePkgs.contains(pkg)) {
                                        remotePkgs.add(pkg);
                                        continue nextPackage;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }


    /**
     * A {@link LocalArchiveInfo} is an {@link ArchiveInfo} that wraps an already installed
     * "local" package/archive.
     * <p/>
     * In this case, the "new Archive" is still expected to be non null and the
     * "replaced Archive" is null. Installed archives are always accepted and never
     * rejected.
     * <p/>
     * Dependencies are not set.
     */
    private static class LocalArchiveInfo extends ArchiveInfo {

        public LocalArchiveInfo(Archive localArchive) {
            super(localArchive, null /*replaced*/, null /*dependsOn*/);
        }

        /** Installed archives are always accepted. */
        @Override
        public boolean isAccepted() {
            return true;
        }

        /** Installed archives are never rejected. */
        @Override
        public boolean isRejected() {
            return false;
        }
    }

    /**
     * A {@link MissingPlatformArchiveInfo} is an {@link ArchiveInfo} that represents a
     * package/archive that we <em>really</em> need as a dependency but that we don't have.
     * <p/>
     * This is currently used for addons and extras in case we can't find a matching base platform.
     * <p/>
     * This kind of archive has specific properties: the new archive to install is null,
     * there are no dependencies and no archive is being replaced. The info can never be
     * accepted and is always rejected.
     */
    private static class MissingPlatformArchiveInfo extends ArchiveInfo {

        private final AndroidVersion mVersion;

        /**
         * Constructs a {@link MissingPlatformArchiveInfo} that will indicate the
         * given platform version is missing.
         */
        public MissingPlatformArchiveInfo(AndroidVersion version) {
            super(null /*newArchive*/, null /*replaced*/, null /*dependsOn*/);
            mVersion = version;
        }

        /** Missing archives are never accepted. */
        @Override
        public boolean isAccepted() {
            return false;
        }

        /** Missing archives are always rejected. */
        @Override
        public boolean isRejected() {
            return true;
        }

        @Override
        public String getShortDescription() {
            return String.format("Missing SDK Platform Android%1$s, API %2$d",
                    mVersion.isPreview() ? " Preview" : "",
                    mVersion.getApiLevel());
        }
    }

    /**
     * A {@link MissingArchiveInfo} is an {@link ArchiveInfo} that represents a
     * package/archive that we <em>really</em> need as a dependency but that we don't have.
     * <p/>
     * This is currently used for extras in case we can't find a matching tool revision
     * or when a platform-tool is missing.
     * <p/>
     * This kind of archive has specific properties: the new archive to install is null,
     * there are no dependencies and no archive is being replaced. The info can never be
     * accepted and is always rejected.
     */
    private static class MissingArchiveInfo extends ArchiveInfo {

        private final FullRevision mRevision;
        private final String mTitle;

        public static final String TITLE_TOOL = "Tools";
        public static final String TITLE_PLATFORM_TOOL = "Platform-tools";

        /**
         * Constructs a {@link MissingPlatformArchiveInfo} that will indicate the
         * given platform version is missing.
         *
         * @param title Typically "Tools" or "Platform-tools".
         * @param revision The required revision.
         */
        public MissingArchiveInfo(String title, FullRevision revision) {
            super(null /*newArchive*/, null /*replaced*/, null /*dependsOn*/);
            mTitle = title;
            mRevision = revision;
        }

        /** Missing archives are never accepted. */
        @Override
        public boolean isAccepted() {
            return false;
        }

        /** Missing archives are always rejected. */
        @Override
        public boolean isRejected() {
            return true;
        }

        @Override
        public String getShortDescription() {
            return String.format("Missing Android SDK %1$s, revision %2$s",
                    mTitle,
                    mRevision.toShortString());
        }
    }
}
