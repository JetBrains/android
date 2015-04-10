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
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.utils.Pair;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a platform XML node in an SDK repository.
 */
public class PlatformPackage extends MinToolsPackage implements IAndroidVersionProvider, ILayoutlibVersion {

  /**
   * The package version, for platform, add-on and doc packages.
   */
  private final AndroidVersion mVersion;

  /**
   * The version, a string, for platform packages.
   */
  private final String mVersionName;

  /**
   * The ABI of the system-image included in this platform. Can be null but not empty.
   */
  private final String mIncludedAbi;

  /**
   * The helper handling the layoutlib version.
   */
  private final LayoutlibVersionMixin mLayoutlibVersion;

  private final IPkgDesc mPkgDesc;

  /**
   * Creates a new platform package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public PlatformPackage(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    mVersionName = PackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_VERSION);

    int apiLevel = PackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = PackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    mVersion = new AndroidVersion(apiLevel, codeName);

    mIncludedAbi = PackageParserUtils.getOptionalXmlString(packageNode, SdkRepoConstants.NODE_ABI_INCLUDED);

    mLayoutlibVersion = new LayoutlibVersionMixin(packageNode);

    mPkgDesc = setDescriptions(PkgDesc.Builder.newPlatform(mVersion, (MajorRevision)getRevision(), getMinToolsRevision())).create();
  }

  @Override
  @NonNull
  public IPkgDesc getPkgDesc() {
    return mPkgDesc;
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

    if (mVersionName != null) {
      props.setProperty(PkgProps.PLATFORM_VERSION, mVersionName);
    }

    if (mIncludedAbi != null) {
      props.setProperty(PkgProps.PLATFORM_INCLUDED_ABI, mIncludedAbi);
    }

  }

  /**
   * Returns the version, a string, for platform packages.
   */
  public String getVersionName() {
    return mVersionName;
  }

  /**
   * Returns the package version, for platform, add-on and doc packages.
   */
  @Override
  @NonNull
  public AndroidVersion getAndroidVersion() {
    return mVersion;
  }

  /**
   * Returns the ABI of the system-image included in this platform.
   *
   * @return Null if the platform does not include any system-image.
   * Otherwise should be a valid non-empty ABI string (e.g. "x86" or "armeabi-v7a").
   */
  public String getIncludedAbi() {
    return mIncludedAbi;
  }

  /**
   * Returns the layoutlib version. Mandatory starting with repository XSD rev 4.
   * <p/>
   * The first integer is the API of layoublib, which should be > 0.
   * It will be equal to {@link ILayoutlibVersion#LAYOUTLIB_API_NOT_SPECIFIED} (0)
   * if the layoutlib version isn't specified.
   * <p/>
   * The second integer is the revision for that given API. It is >= 0
   * and works as a minor revision number, incremented for the same API level.
   *
   * @since sdk-repository-4.xsd
   */
  @Override
  public Pair<Integer, Integer> getLayoutlibVersion() {
    return mLayoutlibVersion.getLayoutlibVersion();
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For platforms, we use "android-N" where N is the API or the preview codename.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    return AndroidTargetHash.getPlatformHashString(mVersion);
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

    String s;
    if (mVersion.isPreview()) {
      s = String.format("SDK Platform Android %1$s Preview%2$s", getVersionName(), isObsolete() ? " (Obsolete)" : "");  //$NON-NLS-2$
    }
    else {
      s = String.format("SDK Platform Android %1$s%2$s", getVersionName(), isObsolete() ? " (Obsolete)" : "");      //$NON-NLS-2$
    }

    return s;
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  @Override
  public String getShortDescription() {
    String ld = getListDisplay();
    if (!ld.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", ld, getRevision().toShortString(), isObsolete() ? " (Obsolete)" : "");
    }

    String s;
    if (mVersion.isPreview()) {
      s = String.format("SDK Platform Android %1$s Preview, revision %2$s%3$s", getVersionName(), getRevision().toShortString(),
                        isObsolete() ? " (Obsolete)" : "");  //$NON-NLS-2$
    }
    else {
      s = String.format("SDK Platform Android %1$s, API %2$d, revision %3$s%4$s", getVersionName(), mVersion.getApiLevel(),
                        getRevision().toShortString(), isObsolete() ? " (Obsolete)" : "");      //$NON-NLS-2$
    }

    return s;
  }

  /**
   * Returns a long description for an {@link IDescription}.
   * <p/>
   * The long description is whatever the XML contains for the &lt;description&gt; field,
   * or the short description if the former is empty.
   */
  @Override
  public String getLongDescription() {
    String s = getDescription();
    if (s == null || s.length() == 0) {
      s = getShortDescription();
    }

    if (s.indexOf("revision") == -1) {
      s += String.format("\nRevision %1$s%2$s", getRevision().toShortString(), isObsolete() ? " (Obsolete)" : "");
    }

    return s;
  }

  /**
   * Computes a potential installation folder if an archive of this package were
   * to be installed right away in the given SDK root.
   * <p/>
   * A platform package is typically installed in SDK/platforms/android-"version".
   * However if we can find a different directory under SDK/platform that already
   * has this platform version installed, we'll use that one.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {

    // First find if this platform is already installed. If so, reuse the same directory.
    for (IAndroidTarget target : sdkManager.getTargets()) {
      if (target.isPlatform() && target.getVersion().equals(mVersion)) {
        return new File(target.getLocation());
      }
    }

    File platforms = new File(osSdkRoot, SdkConstants.FD_PLATFORMS);
    File folder = new File(platforms, String.format("android-%s", getAndroidVersion().getApiString())); //$NON-NLS-1$

    return folder;
  }

  @Override
  public boolean sameItemAs(Package pkg) {
    if (pkg instanceof PlatformPackage) {
      PlatformPackage newPkg = (PlatformPackage)pkg;

      // check they are the same version.
      return newPkg.getAndroidVersion().equals(this.getAndroidVersion());
    }

    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((mLayoutlibVersion == null) ? 0 : mLayoutlibVersion.hashCode());
    result = prime * result + ((mVersion == null) ? 0 : mVersion.hashCode());
    result = prime * result + ((mVersionName == null) ? 0 : mVersionName.hashCode());
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
    if (!(obj instanceof PlatformPackage)) {
      return false;
    }
    PlatformPackage other = (PlatformPackage)obj;
    if (mLayoutlibVersion == null) {
      if (other.mLayoutlibVersion != null) {
        return false;
      }
    }
    else if (!mLayoutlibVersion.equals(other.mLayoutlibVersion)) {
      return false;
    }
    if (mVersion == null) {
      if (other.mVersion != null) {
        return false;
      }
    }
    else if (!mVersion.equals(other.mVersion)) {
      return false;
    }
    if (mVersionName == null) {
      if (other.mVersionName != null) {
        return false;
      }
    }
    else if (!mVersionName.equals(other.mVersionName)) {
      return false;
    }
    return true;
  }
}
