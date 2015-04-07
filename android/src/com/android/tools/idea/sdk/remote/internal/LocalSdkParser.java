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

package com.android.tools.idea.sdk.remote.internal;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.io.FileWrapper;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.ISystemImage.LocationType;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.androidTarget.PlatformTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.sdk.remote.internal.*;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.packages.AddonPackage;
import com.android.tools.idea.sdk.remote.internal.packages.BuildToolPackage;
import com.android.tools.idea.sdk.remote.internal.packages.DocPackage;
import com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.PlatformPackage;
import com.android.tools.idea.sdk.remote.internal.packages.PlatformToolPackage;
import com.android.tools.idea.sdk.remote.internal.packages.SamplePackage;
import com.android.tools.idea.sdk.remote.internal.packages.SourcePackage;
import com.android.tools.idea.sdk.remote.internal.packages.SystemImagePackage;
import com.android.tools.idea.sdk.remote.internal.packages.ToolPackage;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.repository.AddonManifestIniProps;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Scans a local SDK to find which packages are currently installed.
 */
public class LocalSdkParser {

    private Package[] mPackages;

    /** Parse all SDK folders. */
    public static final int PARSE_ALL            = PkgType.PKG_ALL_INT;
    /** Parse the SDK/tools folder. */
    public static final int PARSE_TOOLS          = PkgType.PKG_TOOLS.getIntValue();
    /** Parse the SDK/platform-tools folder */
    public static final int PARSE_PLATFORM_TOOLS = PkgType.PKG_PLATFORM_TOOLS.getIntValue();
    /** Parse the SDK/docs folder. */
    public static final int PARSE_DOCS           = PkgType.PKG_DOC.getIntValue();
    /**
     * Equivalent to parsing the SDK/platforms folder but does so
     * by using the <em>valid</em> targets loaded by the {@link SdkManager}.
     * Parsing the platforms also parses the SDK/system-images folder.
     */
    public static final int PARSE_PLATFORMS      = PkgType.PKG_PLATFORM.getIntValue();
    /**
     * Equivalent to parsing the SDK/addons folder but does so
     * by using the <em>valid</em> targets loaded by the {@link SdkManager}.
     */
    public static final int PARSE_ADDONS         = PkgType.PKG_ADDON.getIntValue();
    /** Parse the SDK/samples folder.
     * Note: this will not detect samples located in the SDK/extras packages. */
    public static final int PARSE_SAMPLES        = PkgType.PKG_SAMPLE.getIntValue();
    /** Parse the SDK/sources folder. */
    public static final int PARSE_SOURCES        = PkgType.PKG_SOURCE.getIntValue();
    /** Parse the SDK/extras folder. */
    public static final int PARSE_EXTRAS         = PkgType.PKG_EXTRA.getIntValue();
    /** Parse the SDK/build-tools folder. */
    public static final int PARSE_BUILD_TOOLS    = PkgType.PKG_BUILD_TOOLS.getIntValue();

    public LocalSdkParser() {
        // pass
    }

    /**
     * Returns the packages found by the last call to {@link #parseSdk}.
     * <p/>
     * This returns initially returns null.
     * Once the parseSdk() method has been called, this returns a possibly empty but non-null array.
     */
    public Package[] getPackages() {
        return mPackages;
    }

    /**
     * Clear the internal packages list. After this call, {@link #getPackages()} will return
     * null till {@link #parseSdk} is called.
     */
    public void clearPackages() {
        mPackages = null;
    }

    /**
     * Scan the give SDK to find all the packages already installed at this location.
     * <p/>
     * Store the packages internally. You can use {@link #getPackages()} to retrieve them
     * at any time later.
     * <p/>
     * Equivalent to calling {@code parseSdk(..., PARSE_ALL, ...); }
     *
     * @param osSdkRoot The path to the SDK folder, typically {@code sdkManager.getLocation()}.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @param monitor A monitor to track progress. Cannot be null.
     * @return The packages found. Can be retrieved later using {@link #getPackages()}.
     */
    @NonNull
    public Package[] parseSdk(
            @NonNull String osSdkRoot,
            @NonNull SdkManager sdkManager,
            @NonNull com.android.tools.idea.sdk.remote.internal.ITaskMonitor monitor) {
        return parseSdk(osSdkRoot, sdkManager, PARSE_ALL, monitor);
    }

    /**
     * Scan the give SDK to find all the packages already installed at this location.
     * <p/>
     * Store the packages internally. You can use {@link #getPackages()} to retrieve them
     * at any time later.
     *
     * @param osSdkRoot The path to the SDK folder, typically {@code sdkManager.getLocation()}.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @param parseFilter Either {@link #PARSE_ALL} or an ORed combination of the other
     *      {@code PARSE_} constants to indicate what should be parsed.
     * @param monitor A monitor to track progress. Cannot be null.
     * @return The packages found. Can be retrieved later using {@link #getPackages()}.
     */
    @NonNull
    public Package[] parseSdk(
            @NonNull String osSdkRoot,
            @NonNull SdkManager sdkManager,
            int parseFilter,
            @NonNull ITaskMonitor monitor) {
        ArrayList<Package> packages = new ArrayList<Package>();
        HashSet<File> visited = new HashSet<File>();

        monitor.setProgressMax(11);

        File dir = null;
        Package pkg = null;

        if ((parseFilter & PARSE_DOCS) != 0) {
            dir = new File(osSdkRoot, SdkConstants.FD_DOCS);
            pkg = scanDoc(dir, monitor);
            if (pkg != null) {
                packages.add(pkg);
                visited.add(dir);
            }
        }
        monitor.incProgress(1);

        if ((parseFilter & PARSE_TOOLS) != 0) {
            dir = new File(osSdkRoot, SdkConstants.FD_TOOLS);
            pkg = scanTools(dir, monitor);
            if (pkg != null) {
                packages.add(pkg);
                visited.add(dir);
            }
        }
        monitor.incProgress(1);

        if ((parseFilter & PARSE_PLATFORM_TOOLS) != 0) {
            dir = new File(osSdkRoot, SdkConstants.FD_PLATFORM_TOOLS);
            pkg = scanPlatformTools(dir, monitor);
            if (pkg != null) {
                packages.add(pkg);
                visited.add(dir);
            }
        }
        monitor.incProgress(1);

        if ((parseFilter & PARSE_BUILD_TOOLS) != 0) {
            scanBuildTools(sdkManager, visited, packages, monitor);
        }
        monitor.incProgress(1);

        // for platforms, add-ons and samples, rely on the SdkManager parser
        if ((parseFilter & (PARSE_ADDONS | PARSE_PLATFORMS)) != 0) {
            File samplesRoot = new File(osSdkRoot, SdkConstants.FD_SAMPLES);

            for(IAndroidTarget target : sdkManager.getTargets()) {
                Properties props = parseProperties(new File(target.getLocation(),
                        SdkConstants.FN_SOURCE_PROP));

                try {
                    pkg = null;
                    if (target.isPlatform() && (parseFilter & PARSE_PLATFORMS) != 0) {
                        pkg = PlatformPackage.create(target, props);

                        if (samplesRoot.isDirectory()) {
                            // Get the samples dir for a platform if it is located in the new
                            // root /samples dir. We purposely ignore "old" samples that are
                            // located under the platform dir.
                            File samplesDir = new File(target.getPath(IAndroidTarget.SAMPLES));
                            if (samplesDir.exists() &&
                                    samplesDir.getParentFile().equals(samplesRoot)) {
                                Properties samplesProps = parseProperties(
                                        new File(samplesDir, SdkConstants.FN_SOURCE_PROP));
                                if (samplesProps != null) {
                                    Package pkg2 = SamplePackage.create(target, samplesProps);
                                    packages.add(pkg2);
                                }
                                visited.add(samplesDir);
                            }
                        }
                    } else if ((parseFilter & PARSE_ADDONS) != 0) {
                        pkg = AddonPackage.create(target, props);
                    }

                    if (pkg != null) {
                        for (ISystemImage systemImage : target.getSystemImages()) {
                            if (systemImage.getLocationType() == LocationType.IN_SYSTEM_IMAGE) {
                                File siDir = systemImage.getLocation();
                                if (siDir.isDirectory()) {
                                    Properties siProps = parseProperties(
                                            new File(siDir, SdkConstants.FN_SOURCE_PROP));
                                    Package pkg2 = new SystemImagePackage(
                                            target.getVersion(),
                                            0 /*rev*/,   // use the one from siProps
                                            systemImage.getAbiType(),
                                            siProps,
                                            siDir.getAbsolutePath());
                                    packages.add(pkg2);
                                    visited.add(siDir);
                                }
                            }
                        }
                    }

                } catch (Exception e) {
                    monitor.error(e, null);
                }

                if (pkg != null) {
                    packages.add(pkg);
                    visited.add(new File(target.getLocation()));
                }
            }
        }
        monitor.incProgress(1);

        if ((parseFilter & PARSE_PLATFORMS) != 0) {
            scanMissingSystemImages(sdkManager, visited, packages, monitor);
        }
        monitor.incProgress(1);
        if ((parseFilter & PARSE_ADDONS) != 0) {
            scanMissingAddons(sdkManager, visited, packages, monitor);
        }
        monitor.incProgress(1);
        if ((parseFilter & PARSE_SAMPLES) != 0) {
            scanMissingSamples(sdkManager, visited, packages, monitor);
        }
        monitor.incProgress(1);
        if ((parseFilter & PARSE_EXTRAS) != 0) {
            scanExtras(sdkManager, visited, packages, monitor);
        }
        monitor.incProgress(1);
        if ((parseFilter & PARSE_EXTRAS) != 0) {
            scanExtrasDirectory(osSdkRoot, visited, packages, monitor);
        }
        monitor.incProgress(1);
        if ((parseFilter & PARSE_SOURCES) != 0) {
            scanSources(sdkManager, visited, packages, monitor);
        }
        monitor.incProgress(1);

        Collections.sort(packages);

        mPackages = packages.toArray(new Package[packages.size()]);
        return mPackages;
    }

    /**
     * Find any directory in the /extras/vendors/path folders for extra packages.
     * This isn't a recursive search.
     */
    private void scanExtras(SdkManager sdkManager,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File root = new File(sdkManager.getLocation(), SdkConstants.FD_EXTRAS);

        for (File vendor : listFilesNonNull(root)) {
            if (vendor.isDirectory()) {
                scanExtrasDirectory(vendor.getAbsolutePath(), visited, packages, log);
            }
        }
    }

    /**
     * Find any other directory in the given "root" directory that hasn't been visited yet
     * and assume they contain extra packages. This is <em>not</em> a recursive search.
     */
    private void scanExtrasDirectory(String extrasRoot,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File root = new File(extrasRoot);

        for (File dir : listFilesNonNull(root)) {
            if (dir.isDirectory() && !visited.contains(dir)) {
                Properties props = parseProperties(new File(dir, SdkConstants.FN_SOURCE_PROP));
                if (props != null) {
                    try {
                        Package pkg = ExtraPackage.create(
                                null,                       //source
                                props,                      //properties
                                null,                       //vendor
                                dir.getName(),              //path
                                0,                          //revision
                                null,                       //license
                                null,                       //description
                                null,                       //descUrl
                                dir.getPath()               //archiveOsPath
                                );

                        packages.add(pkg);
                        visited.add(dir);
                    } catch (Exception e) {
                        log.error(e, null);
                    }
                }
            }
        }
    }

    /**
     * Find any other sub-directories under the /samples root that hasn't been visited yet
     * and assume they contain sample packages. This is <em>not</em> a recursive search.
     * <p/>
     * The use case is to find samples dirs under /samples when their target isn't loaded.
     */
    private void scanMissingSamples(SdkManager sdkManager,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File root = new File(sdkManager.getLocation());
        root = new File(root, SdkConstants.FD_SAMPLES);

        for (File dir : listFilesNonNull(root)) {
            if (dir.isDirectory() && !visited.contains(dir)) {
                Properties props = parseProperties(new File(dir, SdkConstants.FN_SOURCE_PROP));
                if (props != null) {
                    try {
                        Package pkg = SamplePackage.create(dir.getAbsolutePath(), props);
                        packages.add(pkg);
                        visited.add(dir);
                    } catch (Exception e) {
                        log.error(e, null);
                    }
                }
            }
        }
    }

    /**
     * The sdk manager only lists valid addons. However here we also want to find "broken"
     * addons, i.e. addons that failed to load for some reason.
     * <p/>
     * Find any other sub-directories under the /add-ons root that hasn't been visited yet
     * and assume they contain broken addons.
     */
    private void scanMissingAddons(SdkManager sdkManager,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File addons = new File(new File(sdkManager.getLocation()), SdkConstants.FD_ADDONS);

        for (File dir : listFilesNonNull(addons)) {
            if (dir.isDirectory() && !visited.contains(dir)) {
                Pair<Map<String, String>, String> infos =
                    parseAddonProperties(dir, sdkManager.getTargets(), log);
                Properties sourceProps =
                    parseProperties(new File(dir, SdkConstants.FN_SOURCE_PROP));

                Map<String, String> addonProps = infos.getFirst();
                String error = infos.getSecond();
                try {
                    Package pkg = AddonPackage.createBroken(dir.getAbsolutePath(),
                                                            sourceProps,
                                                            addonProps,
                                                            error);
                    packages.add(pkg);
                    visited.add(dir);
                } catch (Exception e) {
                    log.error(e, null);
                }
            }
        }
    }

    /**
     * Parses the add-on properties and decodes any error that occurs when
     * loading an addon.
     *
     * @param addonDir the location of the addon directory.
     * @param targetList The list of Android target that were already loaded
     *        from the SDK.
     * @param log the ILogger object receiving warning/error from the parsing.
     * @return A pair with the property map and an error string. Both can be
     *         null but not at the same time. If a non-null error is present
     *         then the property map must be ignored. The error should be
     *         translatable as it might show up in the SdkManager UI.
     */
    @Deprecated // Copied from SdkManager.java, dup of LocalAddonPkgInfo.parseAddonProperties.
    @NonNull
    public static Pair<Map<String, String>, String> parseAddonProperties(
            @NonNull File addonDir, @NonNull IAndroidTarget[] targetList,
            @NonNull ILogger log) {
        Map<String, String> propertyMap = null;
        String error = null;

        FileWrapper addOnManifest = new FileWrapper(addonDir,
                SdkConstants.FN_MANIFEST_INI);

        do {
            if (!addOnManifest.isFile()) {
                error = String.format("File not found: %1$s",
                        SdkConstants.FN_MANIFEST_INI);
                break;
            }

            propertyMap = ProjectProperties.parsePropertyFile(addOnManifest,
                    log);
            if (propertyMap == null) {
                error = String.format("Failed to parse properties from %1$s",
                        SdkConstants.FN_MANIFEST_INI);
                break;
            }

            // look for some specific values in the map.
            // we require name, vendor, and api
            String name = propertyMap.get(AddonManifestIniProps.ADDON_NAME);
            if (name == null) {
                error = String.format("'%1$s' is missing from %2$s.",
                        AddonManifestIniProps.ADDON_NAME,
                        SdkConstants.FN_MANIFEST_INI);
                break;
            }

            String vendor = propertyMap.get(AddonManifestIniProps.ADDON_VENDOR);
            if (vendor == null) {
                error = String.format("'%1$s' is missing from %2$s.",
                        AddonManifestIniProps.ADDON_VENDOR,
                        SdkConstants.FN_MANIFEST_INI);
                break;
            }

            String api = propertyMap.get(AddonManifestIniProps.ADDON_API);
            if (api == null) {
                error = String.format("'%1$s' is missing from %2$s.",
                        AddonManifestIniProps.ADDON_API,
                        SdkConstants.FN_MANIFEST_INI);
                break;
            }

            // Look for a platform that has a matching api level or codename.
            PlatformTarget baseTarget = null;
            for (IAndroidTarget target : targetList) {
                if (target.isPlatform() && target.getVersion().equals(api)) {
                    baseTarget = (PlatformTarget) target;
                    break;
                }
            }

            if (baseTarget == null) {
                error = String.format(
                        "Unable to find base platform with API level '%1$s'",
                        api);
                break;
            }

            // get the add-on revision
            String revision = propertyMap.get(AddonManifestIniProps.ADDON_REVISION);
            if (revision == null) {
                revision = propertyMap.get(AddonManifestIniProps.ADDON_REVISION_OLD);
            }
            if (revision != null) {
                try {
                    Integer.parseInt(revision);
                } catch (NumberFormatException e) {
                    // looks like revision does not parse to a number.
                    error = String.format(
                            "%1$s is not a valid number in %2$s.",
                            AddonManifestIniProps.ADDON_REVISION,
                            SdkConstants.FN_BUILD_PROP);
                    break;
                }
            }

        } while (false);

        return Pair.of(propertyMap, error);
    }


    /**
     * The sdk manager only lists valid system image via its addons or platform targets.
     * However here we also want to find "broken" system images, that is system images
     * that are located in the sdk/system-images folder but somehow not loaded properly.
     */
    private void scanMissingSystemImages(SdkManager sdkManager,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File siRoot = new File(sdkManager.getLocation(), SdkConstants.FD_SYSTEM_IMAGES);

        // The system-images folder contains a list of platform folders.
        for (File platformDir : listFilesNonNull(siRoot)) {
            if (platformDir.isDirectory() && !visited.contains(platformDir)) {
                visited.add(platformDir);

                // In the platform directory, we expect a list of abi folders
                // or a list of tag/abi folders. Basically parse any folder that has
                // a source.prop file within 2 levels.
                List<File> propFiles = Lists.newArrayList();

                for (File dir1 : listFilesNonNull(platformDir)) {
                    if (dir1.isDirectory() && !visited.contains(dir1)) {
                        visited.add(dir1);
                        File prop1 = new File(dir1, SdkConstants.FN_SOURCE_PROP);
                        if (prop1.isFile()) {
                            propFiles.add(prop1);
                        } else {
                            for (File dir2 : listFilesNonNull(dir1)) {
                                if (dir2.isDirectory() && !visited.contains(dir2)) {
                                    visited.add(dir2);
                                    File prop2 = new File(dir2, SdkConstants.FN_SOURCE_PROP);
                                    if (prop2.isFile()) {
                                        propFiles.add(prop2);
                                    }
                                }
                            }
                        }
                    }
                }

                for (File propFile : propFiles) {
                    Properties props = parseProperties(propFile);
                    try {
                        Package pkg = SystemImagePackage.createBroken(propFile.getParentFile(),
                                                                      props);
                        packages.add(pkg);
                    } catch (Exception e) {
                        log.error(e, null);
                    }
                }
            }
        }
    }

    /**
     * Scan the sources/folders and register valid as well as broken source packages.
     */
    private void scanSources(SdkManager sdkManager,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File srcRoot = new File(sdkManager.getLocation(), SdkConstants.FD_PKG_SOURCES);

        // The sources folder contains a list of platform folders.
        for (File platformDir : listFilesNonNull(srcRoot)) {
            if (platformDir.isDirectory() && !visited.contains(platformDir)) {
                visited.add(platformDir);

                // Ignore empty directories
                File[] srcFiles = platformDir.listFiles();
                if (srcFiles != null && srcFiles.length > 0) {
                    Properties props =
                        parseProperties(new File(platformDir, SdkConstants.FN_SOURCE_PROP));

                    try {
                        Package pkg = SourcePackage.create(platformDir, props);
                        packages.add(pkg);
                    } catch (Exception e) {
                        log.error(e, null);
                    }
                }
            }
        }
    }

    /**
     * Try to find a tools package at the given location.
     * Returns null if not found.
     */
    private Package scanTools(File toolFolder, ILogger log) {
        // Can we find some properties?
        Properties props = parseProperties(new File(toolFolder, SdkConstants.FN_SOURCE_PROP));

        // We're not going to check that all tools are present. At the very least
        // we should expect to find android and an emulator adapted to the current OS.
        boolean hasEmulator = false;
        boolean hasAndroid = false;
        String android1 = SdkConstants.androidCmdName().replace(".bat", ".exe");
        String android2 = android1.indexOf('.') == -1 ? null : android1.replace(".exe", ".bat");
        for (File file : listFilesNonNull(toolFolder)) {
            String name = file.getName();
            if (SdkConstants.FN_EMULATOR.equals(name)) {
                hasEmulator = true;
            }
            if (android1.equals(name) || (android2 != null && android2.equals(name))) {
                hasAndroid = true;
            }
        }

        if (!hasAndroid || !hasEmulator) {
            return null;
        }

        // Create our package. use the properties if we found any.
        try {
            Package pkg = ToolPackage.create(
                    null,                       //source
                    props,                      //properties
                    0,                          //revision
                    null,                       //license
                    "Tools",                    //description
                    null,                       //descUrl
                    toolFolder.getPath()        //archiveOsPath
                    );

            return pkg;
        } catch (Exception e) {
            log.error(e, null);
        }
        return null;
    }

    /**
     * Try to find a platform-tools package at the given location.
     * Returns null if not found.
     */
    private Package scanPlatformTools(File platformToolsFolder, ILogger log) {
        // Can we find some properties?
        Properties props = parseProperties(new File(platformToolsFolder,
                SdkConstants.FN_SOURCE_PROP));

        // We're not going to check that all tools are present. At the very least
        // we should expect to find adb, aidl, aapt and dx (adapted to the current OS).

        if (platformToolsFolder.listFiles() == null) {
            // ListFiles is null if the directory doesn't even exist.
            // Not going to find anything in there...
            return null;
        }

        // Create our package. use the properties if we found any.
        try {
            Package pkg = PlatformToolPackage.create(
                    null,                           //source
                    props,                          //properties
                    0,                              //revision
                    null,                           //license
                    "Platform Tools",               //description
                    null,                           //descUrl
                    platformToolsFolder.getPath()   //archiveOsPath
                    );

            return pkg;
        } catch (Exception e) {
            log.error(e, null);
        }
        return null;
    }

    /**
     * Scan the build-tool/folders and register valid as well as broken build tool packages.
     */
    private void scanBuildTools(
            SdkManager sdkManager,
            HashSet<File> visited,
            ArrayList<Package> packages,
            ILogger log) {
        File buildToolRoot = new File(sdkManager.getLocation(), SdkConstants.FD_BUILD_TOOLS);

        // The build-tool root folder contains a list of revisioned folders.
        for (File buildToolDir : listFilesNonNull(buildToolRoot)) {
            if (buildToolDir.isDirectory() && !visited.contains(buildToolDir)) {
                visited.add(buildToolDir);

                // Ignore empty directories
                File[] srcFiles = buildToolDir.listFiles();
                if (srcFiles != null && srcFiles.length > 0) {
                    Properties props =
                        parseProperties(new File(buildToolDir, SdkConstants.FN_SOURCE_PROP));

                    try {
                        Package pkg = BuildToolPackage.create(buildToolDir, props);
                        packages.add(pkg);
                    } catch (Exception e) {
                        log.error(e, null);
                    }
                }
            }
        }
    }

    /**
     * Try to find a docs package at the given location.
     * Returns null if not found.
     */
    private Package scanDoc(File docFolder, ILogger log) {
        // Can we find some properties?
        Properties props = parseProperties(new File(docFolder, SdkConstants.FN_SOURCE_PROP));

        // To start with, a doc folder should have an "index.html" to be acceptable.
        // We don't actually check the content of the file.
        if (new File(docFolder, "index.html").isFile()) {
            try {
                Package pkg = DocPackage.create(
                        null,                       //source
                        props,                      //properties
                        0,                          //apiLevel
                        null,                       //codename
                        0,                          //revision
                        null,                       //license
                        null,                       //description
                        null,                       //descUrl
                        docFolder.getPath()         //archiveOsPath
                        );

                return pkg;
            } catch (Exception e) {
                log.error(e, null);
            }
        }

        return null;
    }

    /**
     * Parses the given file as properties file if it exists.
     * Returns null if the file does not exist, cannot be parsed or has no properties.
     */
    private Properties parseProperties(File propsFile) {
        FileInputStream fis = null;
        try {
            if (propsFile.exists()) {
                fis = new FileInputStream(propsFile);

                Properties props = new Properties();
                props.load(fis);

                // To be valid, there must be at least one property in it.
                if (props.size() > 0) {
                    return props;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    /**
     * Helper method that calls {@link File#listFiles()} and returns
     * a non-null empty list if the input is not a directory or has
     * no files.
     */
    @NonNull
    private static File[] listFilesNonNull(@NonNull File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                return files;
            }
        }
        return FileOp.EMPTY_FILE_ARRAY;
    }
}
