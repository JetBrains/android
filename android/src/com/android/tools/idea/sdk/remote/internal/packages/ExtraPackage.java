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

package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.sdk.remote.internal.LocalSdkParser;
import com.android.tools.idea.sdk.remote.internal.NullTaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.IMinApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.IMinToolsDependency;
import com.android.tools.idea.sdk.remote.internal.packages.NoPreviewRevisionPackage;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.RepoConstants;
import com.android.sdklib.repository.descriptors.IPkgDescExtra;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgDescExtra;
import com.android.sdklib.repository.local.LocalExtraPkgInfo;
import com.android.utils.NullLogger;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Represents a extra XML node in an SDK repository.
 */
public class ExtraPackage extends NoPreviewRevisionPackage
    implements IMinApiLevelDependency, IMinToolsDependency {

    /** Mixin handling the min-tools dependency. */
    private final com.android.tools.idea.sdk.remote.internal.packages.MinToolsMixin mMinToolsMixin;

    /**
     * The extra display name. Used in the UI to represent the package. It can be anything.
     */
    private final String mDisplayName;

    /**
     * The vendor id + name.
     * The id is a simple alphanumeric string [a-zA-Z0-9_-].
     * The display name is used in the UI to represent the vendor. It can be anything.
     */
    private final IdDisplay mVendor;

    /**
     * The sub-folder name. It must be a non-empty single-segment path.
     */
    private final String mPath;

    /**
     * The optional old_paths, if any. If present, this is a list of old "path" values that
     * we'd like to migrate to the current "path" name for this extra.
     */
    private final String mOldPaths;

    /**
     * The minimal API level required by this extra package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    private final int mMinApiLevel;

    /**
     * The project-files listed by this extra package.
     * The array can be empty but not null.
     */
    private final String[] mProjectFiles;

    private final IPkgDescExtra mPkgDesc;

    /**
     * Creates a new tool package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    public ExtraPackage(
            SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mMinToolsMixin = new com.android.tools.idea.sdk.remote.internal.packages.MinToolsMixin(packageNode);

        mPath   = PackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_PATH);

        // Read name-display, vendor-display and vendor-id, introduced in addon-4.xsd.
        // These are not optional, they are mandatory in addon-4 but we still treat them
        // as optional so that we can fallback on using <vendor> which was the only one
        // defined in addon-3.xsd.
        String name  =
            PackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_NAME_DISPLAY);
        String vname =
            PackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_VENDOR_DISPLAY);
        String vid   =
            PackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_VENDOR_ID);

        if (vid.length() == 0) {
            // If vid is missing, use the old <vendor> attribute.
            // Note that in a valid XML, vendor-id cannot be an empty string.
            // The only reason vid can be empty is when <vendor-id> is missing, which
            // happens in an addon-3 schema, in which case the old <vendor> needs to be used.
            String vendor = PackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_VENDOR);
            vid = sanitizeLegacyVendor(vendor);
            if (vname.length() == 0) {
                vname = vendor;
            }
        }
        if (vname.length() == 0) {
            // The vendor-display name can be empty, in which case we use the vendor-id.
            vname = vid;
        }
        mVendor = new IdDisplay(vid.trim(), vname.trim());

        if (name.length() == 0) {
            // If name is missing, use the <path> attribute as done in an addon-3 schema.
            name = LocalExtraPkgInfo.getPrettyName(mVendor, mPath);
        }
        mDisplayName   = name.trim();

        mMinApiLevel = PackageParserUtils.getXmlInt(
                packageNode, RepoConstants.NODE_MIN_API_LEVEL, MIN_API_LEVEL_NOT_SPECIFIED);

        mProjectFiles = parseProjectFiles(
                PackageParserUtils.findChildElement(packageNode, RepoConstants.NODE_PROJECT_FILES));

        mOldPaths = PackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_OLD_PATHS);

        mPkgDesc =
          (IPkgDescExtra)setDescriptions(PkgDesc.Builder.newExtra(mVendor, mPath, mDisplayName, getOldPaths(), getRevision())).create();
    }

    private String[] parseProjectFiles(Node projectFilesNode) {
        ArrayList<String> paths = new ArrayList<String>();

        if (projectFilesNode != null) {
            String nsUri = projectFilesNode.getNamespaceURI();
            for(Node child = projectFilesNode.getFirstChild();
                     child != null;
                     child = child.getNextSibling()) {

                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        nsUri.equals(child.getNamespaceURI()) &&
                        RepoConstants.NODE_PATH.equals(child.getLocalName())) {
                    String path = child.getTextContent();
                    if (path != null) {
                        path = path.trim();
                        if (path.length() > 0) {
                            paths.add(path);
                        }
                    }
                }
            }
        }

        return paths.toArray(new String[paths.size()]);
    }

    /**
     * Manually create a new package with one archive and the given attributes or properties.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public static Package create(SdkSource source,
            Properties props,
            String vendor,
            String path,
            int revision,
            String license,
            String description,
            String descUrl,
            String archiveOsPath) {
        com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage
          ep = new com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage(source, props, vendor, path, revision, license,
                description, descUrl, archiveOsPath);
        return ep;
    }

    /**
     * Constructor used to create a mock {@link com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage}.
     * Most of the attributes here are optional.
     * When not defined, they will be extracted from the {@code props} properties.
     */
    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected ExtraPackage(SdkSource source,
            Properties props,
            String vendorId,
            String path,
            int revision,
            String license,
            String description,
            String descUrl,
            String archiveOsPath) {
        super(source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOsPath);

        mMinToolsMixin = new com.android.tools.idea.sdk.remote.internal.packages.MinToolsMixin(
                source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOsPath);

        // The path argument comes before whatever could be in the properties
        mPath   = path != null ? path : getProperty(props, PkgProps.EXTRA_PATH, path);

        String name  = getProperty(props, PkgProps.EXTRA_NAME_DISPLAY, "");     //$NON-NLS-1$
        String vname = getProperty(props, PkgProps.EXTRA_VENDOR_DISPLAY, "");   //$NON-NLS-1$
        String vid   = vendorId != null ? vendorId :
                              getProperty(props, PkgProps.EXTRA_VENDOR_ID, ""); //$NON-NLS-1$

        if (vid == null || vid.length() == 0) {
            // If vid is missing, use the old <vendor> attribute.
            // <vendor> did not exist prior to schema repo-v3 and tools r8.
            String vendor = getProperty(props, PkgProps.EXTRA_VENDOR, "");      //$NON-NLS-1$
            vid = sanitizeLegacyVendor(vendor);
            if (vname == null || vname.length() == 0) {
                vname = vendor;
            }
        }
        if (vname == null || vname.length() == 0) {
            // The vendor-display name can be empty, in which case we use the vendor-id.
            vname = vid;
        }
        mVendor = new IdDisplay(vid.trim(), vname.trim());

        if (name == null || name.length() == 0) {
            // If name is missing, use the <path> attribute as done in an addon-3 schema.
            name = LocalExtraPkgInfo.getPrettyName(mVendor, mPath);
        }
        mDisplayName   = name.trim();

        mOldPaths = getProperty(props, PkgProps.EXTRA_OLD_PATHS, null);

        mMinApiLevel = getPropertyInt(props, PkgProps.EXTRA_MIN_API_LEVEL,
                MIN_API_LEVEL_NOT_SPECIFIED);

        String projectFiles = getProperty(props, PkgProps.EXTRA_PROJECT_FILES, null);
        ArrayList<String> filePaths = new ArrayList<String>();
        if (projectFiles != null && projectFiles.length() > 0) {
            for (String filePath : projectFiles.split(Pattern.quote(File.pathSeparator))) {
                filePath = filePath.trim();
                if (filePath.length() > 0) {
                    filePaths.add(filePath);
                }
            }
        }

        mProjectFiles = filePaths.toArray(new String[filePaths.size()]);

        mPkgDesc = (IPkgDescExtra) setDescriptions(PkgDesc.Builder.newExtra(mVendor, mPath, mDisplayName, getOldPaths(), getRevision()))
                .create();
    }

    @Override
    @NonNull
    public IPkgDescExtra getPkgDesc() {
        return mPkgDesc;
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be give the constructor that takes a {@link Properties} object.
     */
    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);
        mMinToolsMixin.saveProperties(props);

        props.setProperty(PkgProps.EXTRA_PATH, mPath);
        props.setProperty(PkgProps.EXTRA_NAME_DISPLAY, mDisplayName);
        props.setProperty(PkgProps.EXTRA_VENDOR_DISPLAY, mVendor.getDisplay());
        props.setProperty(PkgProps.EXTRA_VENDOR_ID, mVendor.getId());

        if (getMinApiLevel() != MIN_API_LEVEL_NOT_SPECIFIED) {
            props.setProperty(PkgProps.EXTRA_MIN_API_LEVEL, Integer.toString(getMinApiLevel()));
        }

        if (mProjectFiles.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mProjectFiles.length; i++) {
                if (i > 0) {
                    sb.append(File.pathSeparatorChar);
                }
                sb.append(mProjectFiles[i]);
            }
            props.setProperty(PkgProps.EXTRA_PROJECT_FILES, sb.toString());
        }

        if (mOldPaths != null && mOldPaths.length() > 0) {
            props.setProperty(PkgProps.EXTRA_OLD_PATHS, mOldPaths);
        }
    }

    /**
     * The minimal revision of the tools package required by this extra package, if > 0,
     * or {@link #MIN_TOOLS_REV_NOT_SPECIFIED} if there is no such requirement.
     */
    @Override
    public FullRevision getMinToolsRevision() {
        return mMinToolsMixin.getMinToolsRevision();
    }

    /**
     * Returns the minimal API level required by this extra package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    @Override
    public int getMinApiLevel() {
        return mMinApiLevel;
    }

    /**
     * The project-files listed by this extra package.
     * The array can be empty but not null.
     * <p/>
     * IMPORTANT: directory separators are NOT translated and may not match
     * the {@link File#separatorChar} of the current platform. It's up to the
     * user to adequately interpret the paths.
     * Similarly, no guarantee is made on the validity of the paths.
     * Users are expected to apply all usual sanity checks such as removing
     * "./" and "../" and making sure these paths don't reference files outside
     * of the installed archive.
     *
     * @since sdk-repository-4.xsd or sdk-addon-2.xsd
     */
    public String[] getProjectFiles() {
        return mProjectFiles;
    }

    /**
     * Returns the old_paths, a list of obsolete path names for the extra package.
     * <p/>
     * These can be used by the installer to migrate an extra package using one of the
     * old paths into the new path.
     * <p/>
     * These can also be used to recognize "old" renamed packages as the same as
     * the current one.
     *
     * @return A list of old paths. Can be empty but not null.
     */
    public String[] getOldPaths() {
        return PkgDescExtra.convertOldPaths(mOldPaths);
    }

    /**
     * Returns the sanitized path folder name. It is a single-segment path.
     * <p/>
     * The package is installed in SDK/extras/vendor_name/path_name.
     */
    public String getPath() {
        // The XSD specifies the XML vendor and path should only contain [a-zA-Z0-9]+
        // and cannot be empty. Let's be defensive and enforce that anyway since things
        // like "____" are still valid values that we don't want to allow.

        // Sanitize the path
        String path = mPath.replaceAll("[^a-zA-Z0-9-]+", "_");      //$NON-NLS-1$
        if (path.length() == 0 || path.equals("_")) {               //$NON-NLS-1$
            int h = path.hashCode();
            path = String.format("extra%08x", h);                   //$NON-NLS-1$
        }

        return path;
    }

    /**
     * Returns the vendor id.
     */
    public String getVendorId() {
        return mVendor.getId();
    }

    public String getVendorDisplay() {
        return mVendor.getDisplay();
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    /** Transforms the legacy vendor name into a usable vendor id. */
    private String sanitizeLegacyVendor(String vendorDisplay) {
        // The XSD specifies the XML vendor and path should only contain [a-zA-Z0-9]+
        // and cannot be empty. Let's be defensive and enforce that anyway since things
        // like "____" are still valid values that we don't want to allow.

        if (vendorDisplay != null && vendorDisplay.length() > 0) {
            String vendor = vendorDisplay.trim();
            // Sanitize the vendor
            vendor = vendor.replaceAll("[^a-zA-Z0-9-]+", "_");      //$NON-NLS-1$
            if (vendor.equals("_")) {                               //$NON-NLS-1$
                int h = vendor.hashCode();
                vendor = String.format("vendor%08x", h);            //$NON-NLS-1$
            }

            return vendor;
        }

        return ""; //$NON-NLS-1$

    }

    /**
     * Returns a string identifier to install this package from the command line.
     * For extras, we use "extra-vendor-path".
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String installId() {
        return String.format("extra-%1$s-%2$s",     //$NON-NLS-1$
                getVendorId(),
                getPath());
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String getListDescription() {
        String ld = getListDisplay();
        if (!ld.isEmpty()) {
            return String.format("%1$s%2$s", ld, isObsolete() ? " (Obsolete)" : "");
        }

        String s = String.format("%1$s%2$s",
                getDisplayName(),
                isObsolete() ? " (Obsolete)" : "");  //$NON-NLS-2$

        return s;
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    @Override
    public String getShortDescription() {
        String ld = getListDisplay();
        if (!ld.isEmpty()) {
            return String.format("%1$s, revision %2$s%3$s",
                    ld,
                    getRevision().toShortString(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        String s = String.format("%1$s, revision %2$s%3$s",
                getDisplayName(),
                getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "");  //$NON-NLS-2$

        return s;
    }

    /**
     * Returns a long description for an {@link IDescription}.
     *
     * The long description is whatever the XML contains for the &lt;description&gt; field,
     * or the short description if the former is empty.
     */
    @Override
    public String getLongDescription() {
        String s = String.format("%1$s, revision %2$s%3$s\nBy %4$s",
                getDisplayName(),
                getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "",  //$NON-NLS-2$
                getVendorDisplay());

        String d = getDescription();
        if (d != null && d.length() > 0) {
            s += '\n' + d;
        }

        if (!getMinToolsRevision().equals(MIN_TOOLS_REV_NOT_SPECIFIED)) {
            s += String.format("\nRequires tools revision %1$s",
                               getMinToolsRevision().toShortString());
        }

        if (getMinApiLevel() != MIN_API_LEVEL_NOT_SPECIFIED) {
            s += String.format("\nRequires SDK Platform Android API %1$s", getMinApiLevel());
        }

        File localPath = getLocalArchivePath();
        if (localPath != null) {
            // For a local archive, also put the install path in the long description.
            // This should help users locate the extra on their drive.
            s += String.format("\nLocation: %1$s", localPath.getAbsolutePath());
        } else {
            // For a non-installed archive, indicate where it would be installed.
            s += String.format("\nInstall path: %1$s",
                    getInstallSubFolder(null/*sdk root*/).getPath());
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A "tool" package should always be located in SDK/tools.
     *
     * @param osSdkRoot The OS path of the SDK root folder. Must NOT be null.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     *                   Not used in this implementation.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {

        // First find if this extra is already installed. If so, reuse the same directory.
        LocalSdkParser localParser = new LocalSdkParser();
        Package[] pkgs = localParser.parseSdk(
                osSdkRoot,
                sdkManager,
                LocalSdkParser.PARSE_EXTRAS,
                new NullTaskMonitor(NullLogger.getLogger()));

        for (Package pkg : pkgs) {
            if (sameItemAs(pkg) && pkg instanceof com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage) {
                File localPath = ((com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage) pkg).getLocalArchivePath();
                if (localPath != null) {
                    return localPath;
                }
            }
        }

        return getInstallSubFolder(osSdkRoot);
    }

    /**
     * Computes the "sub-folder" install path, relative to the given SDK root.
     * For an extra package, this is generally ".../extra/vendor-id/path".
     *
     * @param osSdkRoot The OS path of the SDK root folder if known.
     *   This CAN be null, in which case the path will start at /extra.
     * @return Either /extra/vendor/path or sdk-root/extra/vendor-id/path.
     */
    private File getInstallSubFolder(@Nullable String osSdkRoot) {
        // The /extras dir at the root of the SDK
        File path = new File(osSdkRoot, SdkConstants.FD_EXTRAS);

        String vendor = getVendorId();
        if (vendor != null && vendor.length() > 0) {
            path = new File(path, vendor);
        }

        String name = getPath();
        if (name != null && name.length() > 0) {
            path = new File(path, name);
        }

        return path;
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        // Extra packages are similar if they have the same path and vendor
        if (pkg instanceof com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage) {
            com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage ep = (com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage) pkg;
            return PkgDescExtra.compatibleVendorAndPath(mPkgDesc, ep.mPkgDesc);
        }

        return false;
    }

    /**
     * For extra packages, we want to add vendor|path to the sorting key
     * <em>before<em/> the revision number.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    protected String comparisonKey() {
        String s = super.comparisonKey();
        int pos = s.indexOf("|r:");         //$NON-NLS-1$
        assert pos > 0;
        s = s.substring(0, pos) +
            "|ve:" + getVendorId() +        //$NON-NLS-1$
            "|pa:" + getPath() +            //$NON-NLS-1$
            s.substring(pos);
        return s;
    }

    // ---

    /**
     * If this package is installed, returns the install path of the archive if valid.
     * Returns null if not installed or if the path does not exist.
     */
    private File getLocalArchivePath() {
        Archive[] archives = getArchives();
        if (archives.length == 1 && archives[0].isLocal()) {
            File path = new File(archives[0].getLocalOsPath());
            if (path.isDirectory()) {
                return path;
            }
        }

        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = mMinToolsMixin.hashCode(super.hashCode());
        result = prime * result + mMinApiLevel;
        result = prime * result + ((mPath == null) ? 0 : mPath.hashCode());
        result = prime * result + Arrays.hashCode(mProjectFiles);
        result = prime * result + ((mVendor == null) ? 0 : mVendor.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage)) {
            return false;
        }
        com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage other = (com.android.tools.idea.sdk.remote.internal.packages.ExtraPackage) obj;
        if (mMinApiLevel != other.mMinApiLevel) {
            return false;
        }
        if (mPath == null) {
            if (other.mPath != null) {
                return false;
            }
        } else if (!mPath.equals(other.mPath)) {
            return false;
        }
        if (!Arrays.equals(mProjectFiles, other.mProjectFiles)) {
            return false;
        }
        if (mVendor == null) {
            if (other.mVendor != null) {
                return false;
            }
        } else if (!mVendor.equals(other.mVendor)) {
            return false;
        }
        return mMinToolsMixin.equals(obj);
    }
}
