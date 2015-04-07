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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.sdk.remote.internal.packages.*;
import com.android.tools.idea.sdk.remote.internal.packages.IAndroidVersionProvider;
import com.android.tools.idea.sdk.remote.internal.packages.IExactApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.ILayoutlibVersion;
import com.android.tools.idea.sdk.remote.internal.packages.IMinApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.MajorRevisionPackage;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.sdklib.repository.AddonManifestIniProps;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkAddonConstants;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.local.LocalAddonPkgInfo;
import com.android.utils.Pair;

import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Represents an add-on XML node in an SDK repository.
 */
public class AddonPackage extends MajorRevisionPackage
        implements IAndroidVersionProvider, IPlatformDependency,
                   IExactApiLevelDependency, ILayoutlibVersion {

    private final String mVendorId;
    private final String mVendorDisplay;
    private final String mNameId;
    private final String mDisplayName;
    private final AndroidVersion mVersion;
    private final IPkgDesc mPkgDesc;

    /**
     * The helper handling the layoutlib version.
     */
    private final LayoutlibVersionMixin mLayoutlibVersion;

    /** An add-on library. */
    public static class Lib {
        private final String mName;
        private final String mDescription;

        public Lib(String name, String description) {
            mName = name;
            mDescription = description;
        }

        public String getName() {
            return mName;
        }

        public String getDescription() {
            return mDescription;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mDescription == null) ? 0 : mDescription.hashCode());
            result = prime * result + ((mName == null) ? 0 : mName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Lib)) {
                return false;
            }
            Lib other = (Lib) obj;
            if (mDescription == null) {
                if (other.mDescription != null) {
                    return false;
                }
            } else if (!mDescription.equals(other.mDescription)) {
                return false;
            }
            if (mName == null) {
                if (other.mName != null) {
                    return false;
                }
            } else if (!mName.equals(other.mName)) {
                return false;
            }
            return true;
        }
    }

    private final Lib[] mLibs;

    /**
     * Creates a new add-on package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    public AddonPackage(
            SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        // --- name id/display ---
        // addon-4.xsd introduces the name-id, name-display, vendor-id and vendor-display.
        // These are not optional but we still need to support a fallback for older addons
        // that only provide name and vendor. If the addon provides neither set of fields,
        // it will simply not work as expected.

        String nameId   = PackageParserUtils.getXmlString(packageNode,
                                                          SdkRepoConstants.NODE_NAME_ID);
        String nameDisp = PackageParserUtils.getXmlString(packageNode,
                                                          SdkRepoConstants.NODE_NAME_DISPLAY);
        String name     = PackageParserUtils.getXmlString(packageNode,
                                                          SdkRepoConstants.NODE_NAME);

        // The old <name> is equivalent to the new <name-display>
        if (nameDisp.length() == 0) {
            nameDisp = name;
        }

        // For a missing id, we simply use a sanitized version of the display name
        if (nameId.length() == 0) {
            nameId = LocalAddonPkgInfo.sanitizeDisplayToNameId(name.length() > 0 ? name : nameDisp);
        }

        assert nameId.length() > 0;
        assert nameDisp.length() > 0;

        mNameId = nameId.trim();
        mDisplayName = nameDisp.trim();

        // --- vendor id/display ---
        // Same processing for vendor id vs display

        String vendorId   = PackageParserUtils.getXmlString(packageNode,
                                                            SdkAddonConstants.NODE_VENDOR_ID);
        String vendorDisp = PackageParserUtils.getXmlString(packageNode,
                                                            SdkAddonConstants.NODE_VENDOR_DISPLAY);
        String vendor     = PackageParserUtils.getXmlString(packageNode,
                                                            SdkAddonConstants.NODE_VENDOR);

        // The old <vendor> is equivalent to the new <vendor-display>
        if (vendorDisp.length() == 0) {
            vendorDisp = vendor;
        }

        // For a missing id, we simply use a sanitized version of the display vendor
        if (vendorId.length() == 0) {
            boolean hasVendor = vendor.length() > 0;
            vendorId = LocalAddonPkgInfo.sanitizeDisplayToNameId(hasVendor ? vendor : vendorDisp);
        }

        assert vendorId.length() > 0;
        assert vendorDisp.length() > 0;

        mVendorId      = vendorId.trim();
        mVendorDisplay = vendorDisp.trim();

        // --- other attributes

        int apiLevel =
            PackageParserUtils.getXmlInt(packageNode, SdkAddonConstants.NODE_API_LEVEL, 0);
        mVersion = new AndroidVersion(apiLevel, null /*codeName*/);

        mLibs = parseLibs(
                PackageParserUtils.findChildElement(packageNode, SdkAddonConstants.NODE_LIBS));

        mLayoutlibVersion = new LayoutlibVersionMixin(packageNode);

        mPkgDesc = setDescriptions(PkgDesc.Builder
                                     .newAddon(mVersion, (MajorRevision)getRevision(), new IdDisplay(mVendorId, mVendorDisplay),
                                               new IdDisplay(mNameId, mDisplayName)))
                .create();
    }

    /**
     * Creates a new platform package based on an actual {@link IAndroidTarget} (which
     * {@link IAndroidTarget#isPlatform()} false) from the {@link SdkManager}.
     * This is used to list local SDK folders in which case there is one archive which
     * URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public static Package create(IAndroidTarget target, Properties props) {
        return new com.android.tools.idea.sdk.remote.internal.packages.AddonPackage(target, props);
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected AddonPackage(IAndroidTarget target, Properties props) {
        this(null /*source*/, target, props);
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected AddonPackage(SdkSource source, IAndroidTarget target, Properties props) {
        super(  source,                     //source
                props,                      //properties
                target.getRevision(),       //revision
                null,                       //license
                target.getDescription(),    //description
                null,                       //descUrl
                target.getLocation()        //archiveOsPath
                );

        // --- name id/display ---
        // addon-4.xsd introduces the name-id, name-display, vendor-id and vendor-display.
        // These are not optional but we still need to support a fallback for older addons
        // that only provide name and vendor. If the addon provides neither set of fields,
        // it will simply not work as expected.

        String nameId   = getProperty(props, PkgProps.ADDON_NAME_ID, "");           //$NON-NLS-1$
        String nameDisp = getProperty(props, PkgProps.ADDON_NAME_DISPLAY, "");      //$NON-NLS-1$
        String name     = getProperty(props, PkgProps.ADDON_NAME, target.getName());

        // The old <name> is equivalent to the new <name-display>
        if (nameDisp.length() == 0) {
            nameDisp = name;
        }

        // For a missing id, we simply use a sanitized version of the display name
        if (nameId.length() == 0) {
            nameId = LocalAddonPkgInfo.sanitizeDisplayToNameId(name.length() > 0 ? name : nameDisp);
        }

        assert nameId.length() > 0;
        assert nameDisp.length() > 0;

        mNameId = nameId.trim();
        mDisplayName = nameDisp.trim();

        // --- vendor id/display ---
        // Same processing for vendor id vs display

        String vendorId   = getProperty(props, PkgProps.ADDON_VENDOR_ID, "");       //$NON-NLS-1$
        String vendorDisp = getProperty(props, PkgProps.ADDON_VENDOR_DISPLAY, "");  //$NON-NLS-1$
        String vendor     = getProperty(props, PkgProps.ADDON_VENDOR, target.getVendor());

        // The old <vendor> is equivalent to the new <vendor-display>
        if (vendorDisp.length() == 0) {
            vendorDisp = vendor;
        }

        // For a missing id, we simply use a sanitized version of the display vendor
        if (vendorId.length() == 0) {
            boolean hasVendor = vendor.length() > 0;
            vendorId = LocalAddonPkgInfo.sanitizeDisplayToNameId(hasVendor ? vendor : vendorDisp);
        }

        assert vendorId.length() > 0;
        assert vendorDisp.length() > 0;

        mVendorId = vendorId.trim();
        mVendorDisplay = vendorDisp.trim();

        // --- other attributes

        mVersion = target.getVersion();
        mLayoutlibVersion = new LayoutlibVersionMixin(props);

        IOptionalLibrary[] optLibs = target.getOptionalLibraries();
        if (optLibs == null || optLibs.length == 0) {
            mLibs = new Lib[0];
        } else {
            mLibs = new Lib[optLibs.length];
            for (int i = 0; i < optLibs.length; i++) {
                mLibs[i] = new Lib(optLibs[i].getName(), optLibs[i].getDescription());
            }
        }

        mPkgDesc = setDescriptions(PkgDesc.Builder
                                     .newAddon(mVersion, (MajorRevision)getRevision(), new IdDisplay(mVendorId, mVendorDisplay),
                                               new IdDisplay(mNameId, mDisplayName)))
                .create();
    }

    @Override
    @NonNull
    public IPkgDesc getPkgDesc() {
        return mPkgDesc;
    }

    /**
     * Creates a broken addon which we know failed to load properly.
     *
     * @param archiveOsPath The absolute OS path of the addon folder.
     * @param sourceProps The properties parsed from the addon's source.properties. Can be null.
     * @param addonProps The properties parsed from the addon manifest (NOT the source.properties).
     * @param error The error indicating why this addon failed to be loaded.
     */
    public static Package createBroken(
            String archiveOsPath,
            Properties sourceProps,
            Map<String, String> addonProps,
            String error) {


        String nameId   = getProperty(sourceProps, PkgProps.ADDON_NAME_ID, null);
        String nameDisp = getProperty(sourceProps, PkgProps.ADDON_NAME_DISPLAY, null);
        if (nameDisp == null) {
            nameDisp = getProperty(sourceProps, PkgProps.ADDON_NAME_DISPLAY, null);
        }
        if (nameDisp == null) {
            nameDisp = addonProps.get(AddonManifestIniProps.ADDON_NAME);
        }
        if (nameDisp == null) {
            nameDisp = "Unknown";
        }
        if (nameId == null) {
            nameId = LocalAddonPkgInfo.sanitizeDisplayToNameId(nameDisp);
        }

        String vendorId   = getProperty(sourceProps, PkgProps.ADDON_VENDOR_ID, null);
        String vendorDisp = getProperty(sourceProps, PkgProps.ADDON_VENDOR_DISPLAY, null);
        if (vendorDisp == null) {
            vendorDisp = getProperty(sourceProps, PkgProps.ADDON_VENDOR_DISPLAY, null);
        }
        if (vendorDisp == null) {
            vendorDisp = addonProps.get(AddonManifestIniProps.ADDON_VENDOR);
        }
        if (vendorDisp == null) {
            vendorDisp = "Unknown";
        }
        if (vendorId == null) {
            vendorId = LocalAddonPkgInfo.sanitizeDisplayToNameId(vendorDisp);
        }

        String api      = addonProps.get(AddonManifestIniProps.ADDON_API);
        String revision = addonProps.get(AddonManifestIniProps.ADDON_REVISION);

        String shortDesc = String.format("%1$s by %2$s, Android API %3$s, revision %4$s [*]",
                nameDisp,
                vendorDisp,
                api,
                revision);

        String longDesc = String.format(
                "%1$s\n" +
                "[*] Addon failed to load: %2$s",
                shortDesc,
                error);

        int apiLevel = IExactApiLevelDependency.API_LEVEL_INVALID;

        try {
            apiLevel = Integer.parseInt(api);
        } catch(NumberFormatException ignore) {}

        int intRevision = MajorRevision.MISSING_MAJOR_REV;
        try {
            intRevision = Integer.parseInt(revision);
        } catch (NumberFormatException ignore) {}

        IPkgDesc desc = PkgDesc.Builder
                .newAddon(new AndroidVersion(apiLevel, null),
                          new MajorRevision(intRevision),
                          new IdDisplay(vendorId, vendorDisp),
                          new IdDisplay(nameId, nameDisp))
                .setDescriptionShort(shortDesc)
                .create();

        return new BrokenPackage(null/*props*/, shortDesc, longDesc,
                IMinApiLevelDependency.MIN_API_LEVEL_NOT_SPECIFIED,
                apiLevel,
                archiveOsPath,
                desc);
    }

    @Override
    public int getExactApiLevel() {
        return mVersion.getApiLevel();
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be given to a constructor that takes a {@link Properties} object.
     */
    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);

        mVersion.saveProperties(props);
        mLayoutlibVersion.saveProperties(props);

        props.setProperty(PkgProps.ADDON_NAME_ID,        mNameId);
        props.setProperty(PkgProps.ADDON_NAME_DISPLAY,   mDisplayName);
        props.setProperty(PkgProps.ADDON_VENDOR_ID,      mVendorId);
        props.setProperty(PkgProps.ADDON_VENDOR_DISPLAY, mVendorDisplay);
    }

    /**
     * Parses a <libs> element.
     */
    private Lib[] parseLibs(Node libsNode) {
        ArrayList<Lib> libs = new ArrayList<Lib>();

        if (libsNode != null) {
            String nsUri = libsNode.getNamespaceURI();
            for(Node child = libsNode.getFirstChild();
                child != null;
                child = child.getNextSibling()) {

                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        nsUri.equals(child.getNamespaceURI()) &&
                        SdkRepoConstants.NODE_LIB.equals(child.getLocalName())) {
                    libs.add(parseLib(child));
                }
            }
        }

        return libs.toArray(new Lib[libs.size()]);
    }

    /**
     * Parses a <lib> element from a <libs> container.
     */
    private Lib parseLib(Node libNode) {
        return new Lib(PackageParserUtils.getXmlString(libNode, SdkRepoConstants.NODE_NAME),
                       PackageParserUtils.getXmlString(libNode, SdkRepoConstants.NODE_DESCRIPTION));
    }

    /** Returns the vendor id, a string, for add-on packages. */
    @NonNull
    public String getVendorId() {
        return mVendorId;
    }

    /** Returns the vendor, a string for display purposes. */
    @NonNull
    public String getDisplayVendor() {
        return mVendorDisplay;
    }

    /** Returns the name id, a string, for add-on packages or for libraries. */
    @NonNull
    public String getNameId() {
        return mNameId;
    }

    /** Returns the name, a string for display purposes. */
    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    /**
     * Returns the version of the platform dependency of this package.
     * <p/>
     * An add-on has the same {@link AndroidVersion} as the platform it depends on.
     */
    @Override @NonNull
    public AndroidVersion getAndroidVersion() {
        return mVersion;
    }

    /** Returns the libs defined in this add-on. Can be an empty array but not null. */
    @NonNull
    public Lib[] getLibs() {
        return mLibs;
    }

    /**
     * Returns the layoutlib version.
     * <p/>
     * The first integer is the API of layoublib, which should be > 0.
     * It will be equal to {@link ILayoutlibVersion#LAYOUTLIB_API_NOT_SPECIFIED} (0)
     * if the layoutlib version isn't specified.
     * <p/>
     * The second integer is the revision for that given API. It is >= 0
     * and works as a minor revision number, incremented for the same API level.
     *
     * @since sdk-addon-2.xsd
     */
    @NonNull
    @Override
    public Pair<Integer, Integer> getLayoutlibVersion() {
        return mLayoutlibVersion.getLayoutlibVersion();
    }

    /**
     * Returns a string identifier to install this package from the command line.
     * For add-ons, we use "addon-vendor-name-N" where N is the base platform API.
     * <p/>
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String installId() {
        return encodeAddonName();
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

        return String.format("%1$s%2$s",
                getDisplayName(),
                isObsolete() ? " (Obsolete)" : "");
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

        return String.format("%1$s, Android API %2$s, revision %3$s%4$s",
                getDisplayName(),
                mVersion.getApiString(),
                getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "");
    }

    /**
     * Returns a long description for an {@link IDescription}.
     *
     * The long description is whatever the XML contains for the &lt;description&gt; field,
     * or the short description if the former is empty.
     */
    @Override
    public String getLongDescription() {
        String s = String.format("%1$s, Android API %2$s, revision %3$s%4$s\nBy %5$s",
                getDisplayName(),
                mVersion.getApiString(),
                getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "",  //$NON-NLS-2$
                getDisplayVendor());

        String d = getDescription();
        if (d != null && d.length() > 0) {
            s += '\n' + d;
        }

        s += String.format("\nRequires SDK Platform Android API %1$s",
                mVersion.getApiString());
        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * An add-on package is typically installed in SDK/add-ons/"addon-name"-"api-level".
     * The name needs to be sanitized to be acceptable as a directory name.
     * However if we can find a different directory under SDK/add-ons that already
     * has this add-ons installed, we'll use that one.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
        File addons = new File(osSdkRoot, SdkConstants.FD_ADDONS);

        // First find if this add-on is already installed. If so, reuse the same directory.
        for (IAndroidTarget target : sdkManager.getTargets()) {
            if (!target.isPlatform() && target.getVersion().equals(mVersion)) {
                // Starting with addon-4.xsd, the addon source.properties differentiate
                // between ids and display strings. However the addon target which relies
                // on the manifest.ini does not so we need to cover both cases.
                // TODO fix when we get rid of manifest.ini for addons
                if ((target.getName().equals(getNameId()) &&
                     target.getVendor().equals(getVendorId())) ||
                    (target.getName().equals(getDisplayName()) &&
                     target.getVendor().equals(getDisplayVendor()))) {
                    return new File(target.getLocation());
                }
            }
        }

        // Compute a folder directory using the addon declared name and vendor strings.
        String name = encodeAddonName();

        for (int i = 0; i < 100; i++) {
            String name2 = i == 0 ? name : String.format("%s-%d", name, i); //$NON-NLS-1$
            File folder = new File(addons, name2);
            if (!folder.exists()) {
                return folder;
            }
        }

        // We shouldn't really get here. I mean, seriously, we tried hard enough.
        return null;
    }

    private String encodeAddonName() {
        String name = String.format("addon-%s-%s-%s",     //$NON-NLS-1$
                                    getNameId(), getVendorId(), mVersion.getApiString());
        name = name.toLowerCase(Locale.US);
        name = name.replaceAll("[^a-z0-9_-]+", "_");      //$NON-NLS-1$ //$NON-NLS-2$
        name = name.replaceAll("_+", "_");                //$NON-NLS-1$ //$NON-NLS-2$
        return name;
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        if (pkg instanceof com.android.tools.idea.sdk.remote.internal.packages.AddonPackage) {
            com.android.tools.idea.sdk.remote.internal.packages.AddonPackage
              newPkg = (com.android.tools.idea.sdk.remote.internal.packages.AddonPackage)pkg;

            // check they are the same add-on.
            if (getNameId().equals(newPkg.getNameId()) &&
                    getAndroidVersion().equals(newPkg.getAndroidVersion())) {
                // Check the vendor-id field.
                if (getVendorId().equals(newPkg.getVendorId())) {
                    return true;
                }

                // When loading addons from the v3 schema that only had a <vendor>
                // field, the vendor field has been converted to vendor-display so
                // as a transition mechanism we should test this also.
                // TODO: in a couple iterations of the SDK Manager, remove this check
                // and only compare using the vendor-id field.
                return getDisplayVendor().equals(newPkg.getDisplayVendor());
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mLayoutlibVersion == null) ? 0 : mLayoutlibVersion.hashCode());
        result = prime * result + Arrays.hashCode(mLibs);
        result = prime * result + ((mDisplayName == null) ? 0 : mDisplayName.hashCode());
        result = prime * result + ((mVendorDisplay == null) ? 0 : mVendorDisplay.hashCode());
        result = prime * result + ((mVersion == null) ? 0 : mVersion.hashCode());
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
        if (!(obj instanceof com.android.tools.idea.sdk.remote.internal.packages.AddonPackage)) {
            return false;
        }
        com.android.tools.idea.sdk.remote.internal.packages.AddonPackage other = (com.android.tools.idea.sdk.remote.internal.packages.AddonPackage) obj;
        if (mLayoutlibVersion == null) {
            if (other.mLayoutlibVersion != null) {
                return false;
            }
        } else if (!mLayoutlibVersion.equals(other.mLayoutlibVersion)) {
            return false;
        }
        if (!Arrays.equals(mLibs, other.mLibs)) {
            return false;
        }
        if (mNameId == null) {
            if (other.mNameId != null) {
                return false;
            }
        } else if (!mNameId.equals(other.mNameId)) {
            return false;
        }
        if (mVendorId == null) {
            if (other.mVendorId != null) {
                return false;
            }
        } else if (!mVendorId.equals(other.mVendorId)) {
            return false;
        }
        if (mVersion == null) {
            if (other.mVersion != null) {
                return false;
            }
        } else if (!mVersion.equals(other.mVersion)) {
            return false;
        }
        return true;
    }

    /**
     * For addon packages, we want to add vendor|name to the sorting key
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
            "|vid:" + getVendorId() +          //$NON-NLS-1$
            "|nid:" + getNameId() +            //$NON-NLS-1$
            s.substring(pos);
        return s;
    }
}
