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
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.sdklib.SdkManager;
import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.*;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.local.LocalSysImgPkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Represents a system-image XML node in an SDK repository.
 */
public class SystemImagePackage extends MajorRevisionPackage implements IAndroidVersionProvider, IPlatformDependency {

  /**
   * The package version, for platform, add-on and doc packages.
   */
  private final AndroidVersion mVersion;

  /**
   * The ABI of the system-image. Must not be null nor empty.
   */
  private final String mAbi;

  private final IPkgDesc mPkgDesc;

  private final IdDisplay mTag;
  private final IdDisplay mAddonVendor;

  /**
   * Creates a new system-image package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public SystemImagePackage(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    int apiLevel = PackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = PackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    mVersion = new AndroidVersion(apiLevel, codeName);

    mAbi = PackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_ABI);

    // tag id
    String tagId = PackageParserUtils.getXmlString(packageNode, SdkSysImgConstants.ATTR_TAG_ID, SystemImage.DEFAULT_TAG.getId());
    String tagDisp = PackageParserUtils.getOptionalXmlString(packageNode, SdkSysImgConstants.ATTR_TAG_DISPLAY);
    if (tagDisp == null || tagDisp.isEmpty()) {
      tagDisp = LocalSysImgPkgInfo.tagIdToDisplay(tagId);
    }
    assert tagId != null;
    assert tagDisp != null;
    mTag = new IdDisplay(tagId, tagDisp);


    Node addonNode = PackageParserUtils.findChildElement(packageNode, SdkSysImgConstants.NODE_ADD_ON);

    IPkgDesc desc = null;
    IdDisplay vendor = null;

    if (addonNode == null) {
      // A platform system-image
      desc = setDescriptions(PkgDesc.Builder.newSysImg(mVersion, mTag, mAbi, (MajorRevision)getRevision())).create();
    }
    else {
      // An add-on system-image
      String vendorId = PackageParserUtils.getXmlString(addonNode, SdkAddonConstants.NODE_VENDOR_ID);
      String vendorDisp = PackageParserUtils.getXmlString(addonNode, SdkAddonConstants.NODE_VENDOR_DISPLAY, vendorId);

      assert vendorId.length() > 0;
      assert vendorDisp.length() > 0;

      vendor = new IdDisplay(vendorId, vendorDisp);

      desc = setDescriptions(PkgDesc.Builder.newAddonSysImg(mVersion, vendor, mTag, mAbi, (MajorRevision)getRevision())).create();
    }

    mPkgDesc = desc;
    mAddonVendor = vendor;
  }

  @VisibleForTesting(visibility = Visibility.PRIVATE)
  public SystemImagePackage(AndroidVersion platformVersion, int revision, String abi, Properties props, String localOsPath) {
    this(null /*source*/, platformVersion, revision, abi, props, localOsPath);
  }

  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected SystemImagePackage(SdkSource source,
                               AndroidVersion platformVersion,
                               int revision,
                               String abi,
                               Properties props,
                               String localOsPath) {
    super(source,                     //source
          props,                      //properties
          revision,                   //revision
          null,                       //license
          null,                       //description
          null,                       //descUrl
          localOsPath                 //archiveOsPath
    );
    mVersion = platformVersion;
    if (abi == null && props != null) {
      abi = props.getProperty(PkgProps.SYS_IMG_ABI);
    }
    assert abi != null : "To use this SystemImagePackage constructor you must pass an ABI as a parameter or as a PROP_ABI property";
    mAbi = abi;

    mTag = LocalSysImgPkgInfo.extractTagFromProps(props);

    String vendorId = getProperty(props, PkgProps.ADDON_VENDOR_ID, null);
    String vendorDisp = getProperty(props, PkgProps.ADDON_VENDOR_DISPLAY, vendorId);

    IPkgDesc desc = null;
    IdDisplay vendor = null;

    if (vendorId == null) {
      // A platform system-image
      desc = setDescriptions(PkgDesc.Builder.newSysImg(mVersion, mTag, mAbi, (MajorRevision)getRevision())).create();
    }
    else {
      // An add-on system-image
      assert vendorId.length() > 0;
      assert vendorDisp.length() > 0;

      vendor = new IdDisplay(vendorId, vendorDisp);

      desc = setDescriptions(PkgDesc.Builder.newAddonSysImg(mVersion, vendor, mTag, mAbi, (MajorRevision)getRevision())).create();
    }

    mPkgDesc = desc;
    mAddonVendor = vendor;
  }

  /**
   * Creates a {@link BrokenPackage} representing a system image that failed to load
   * with the regular {@link SdkManager} workflow.
   *
   * @param abiDir The SDK/system-images/android-N/tag/abi folder
   * @param props  The properties located in {@code abiDir} or null if not found.
   * @return A new {@link BrokenPackage} that represents this installed package.
   */
  public static com.android.tools.idea.sdk.remote.internal.packages.Package createBroken(File abiDir, Properties props) {
    AndroidVersion version = null;
    String abiType = abiDir.getName();
    String error = null;
    IdDisplay tag = null;

    // Try to load the android version, tag & ABI from the sources.props.
    // If we don't find them, it would explain why this package is broken.
    if (props == null) {
      error = String.format("Missing file %1$s", SdkConstants.FN_SOURCE_PROP);
    }
    else {
      try {
        version = new AndroidVersion(props);

        tag = LocalSysImgPkgInfo.extractTagFromProps(props);
        String abi = props.getProperty(PkgProps.SYS_IMG_ABI);
        if (abi != null) {
          abiType = abi;
        }
        else {
          error = String.format("Invalid file %1$s: Missing property %2$s", SdkConstants.FN_SOURCE_PROP, PkgProps.SYS_IMG_ABI);
        }
      }
      catch (AndroidVersionException e) {
        error = String.format("Invalid file %1$s: %2$s", SdkConstants.FN_SOURCE_PROP, e.getMessage());
      }
    }

    try {
      // Try to parse the first number out of the platform folder name.
      // Also try to parse the tag if not known yet.
      // Folder structure should be:
      // Tools < 22.6 / API < 20: sdk/system-images/android-N/abi/
      // Tools >=22.6 / API >=20: sdk/system-images/android-N/tag/abi/
      String[] segments = abiDir.getAbsolutePath().split(Pattern.quote(File.separator));
      int len = segments.length;
      for (int i = len - 2; version == null && i >= 0; i--) {
        if (SdkConstants.FD_SYSTEM_IMAGES.equals(segments[i])) {
          String platform = segments[i + 1];
          try {
            platform = platform.replaceAll("[^0-9]+", " ").trim();  //$NON-NLS-1$ //$NON-NLS-2$
            int pos = platform.indexOf(' ');
            if (pos >= 0) {
              platform = platform.substring(0, pos);
            }
            int apiLevel = Integer.parseInt(platform);
            version = new AndroidVersion(apiLevel, null /*codename*/);
          }
          catch (Exception ignore) {
          }
          if (tag == null && i + 2 < len) {
            // If we failed to find a tag id in the properties, check whether
            // we can guess one from the system image folder path. It should
            // match the limited tag id character set and not be one of the
            // known ABIs.
            String abiOrTag = segments[i + 2].trim();
            if (abiOrTag.matches("[A-Za-z0-9_-]+")) {
              if (Abi.getEnum(abiOrTag) == null) {
                tag = new IdDisplay(abiOrTag, LocalSysImgPkgInfo.tagIdToDisplay(abiOrTag));
              }
            }
          }
        }
      }
    }
    catch (Exception ignore) {
    }

    String vendorId = getProperty(props, PkgProps.ADDON_VENDOR_ID, null);
    String vendorDisp = getProperty(props, PkgProps.ADDON_VENDOR_DISPLAY, vendorId);

    StringBuilder sb = new StringBuilder("Broken ");
    sb.append(getAbiDisplayNameInternal(abiType)).append(' ');
    if (tag != null && !tag.getId().equals(SystemImage.DEFAULT_TAG.getId())) {
      sb.append(tag).append(' ');
    }
    sb.append("System Image");
    if (vendorDisp != null) {
      sb.append(", by ").append(vendorDisp);
    }
    if (version != null) {
      sb.append(String.format(", API %1$s", version.getApiString()));
    }

    String shortDesc = sb.toString();

    if (error != null) {
      sb.append('\n').append(error);
    }

    String longDesc = sb.toString();

    if (tag == null) {
      // No tag? Use the default.
      tag = SystemImage.DEFAULT_TAG;
    }
    assert tag != null;

    IPkgDesc desc = PkgDesc.Builder
      .newSysImg(version != null ? version : new AndroidVersion(0, null), tag, abiType, new MajorRevision(MajorRevision.MISSING_MAJOR_REV))
      .setDescriptionShort(shortDesc).create();

    return new BrokenPackage(props, shortDesc, longDesc, IMinApiLevelDependency.MIN_API_LEVEL_NOT_SPECIFIED,
                             version == null ? IExactApiLevelDependency.API_LEVEL_INVALID : version.getApiLevel(), abiDir.getAbsolutePath(),
                             desc);
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
    props.setProperty(PkgProps.SYS_IMG_ABI, mAbi);
    props.setProperty(PkgProps.SYS_IMG_TAG_ID, mTag.getId());
    props.setProperty(PkgProps.SYS_IMG_TAG_DISPLAY, mTag.getDisplay());

    if (mAddonVendor != null) {
      props.setProperty(PkgProps.ADDON_VENDOR_ID, mAddonVendor.getId());
      props.setProperty(PkgProps.ADDON_VENDOR_DISPLAY, mAddonVendor.getDisplay());
    }
  }

  /**
   * Returns the tag of the system-image.
   */
  @NonNull
  public IdDisplay getTag() {
    return mTag;
  }

  /**
   * Returns the ABI of the system-image. Cannot be null nor empty.
   */
  public String getAbi() {
    return mAbi;
  }

  /**
   * Returns a display-friendly name for the ABI of the system-image.
   */
  public String getAbiDisplayName() {
    return getAbiDisplayNameInternal(mAbi);
  }

  private static String getAbiDisplayNameInternal(String abi) {
    return abi.replace("armeabi", "ARM EABI")          //$NON-NLS-1$  //$NON-NLS-2$
      .replace("arm64", "ARM 64")            //$NON-NLS-1$  //$NON-NLS-2$
      .replace("x86", "Intel x86 Atom")    //$NON-NLS-1$  //$NON-NLS-2$
      .replace("x86_64", "Intel x86_64 Atom") //$NON-NLS-1$  //$NON-NLS-2$
      .replace("mips", "MIPS")              //$NON-NLS-1$  //$NON-NLS-2$
      .replace("-", " ");                      //$NON-NLS-1$  //$NON-NLS-2$
  }

  /**
   * Returns the version of the platform dependency of this package.
   * <p/>
   * A system-image has the same {@link AndroidVersion} as the platform it depends on.
   */
  @NonNull
  @Override
  public AndroidVersion getAndroidVersion() {
    return mVersion;
  }

  /**
   * Returns true if the system-image belongs to a standard Android platform.
   * In this case {@link #getAddonVendor()} returns null.
   * <p/.
   * Returns false if the system-image belongs to an add-on.
   * In this case {@link #getAndroidVersion()} returns a non-null {@link IdDisplay}.
   */
  public boolean isPlatform() {
    return mAddonVendor == null;
  }

  /**
   * Returns the add-on vendor if this is an add-on system image.
   * Returns null if this is a platform system-image.
   */
  @Nullable
  public IdDisplay getAddonVendor() {
    return mAddonVendor;
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For system images, we use "sysimg-N" where N is the API or the preview codename.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    StringBuilder sb = new StringBuilder("sys-img-");   //$NON-NLS-1$
    sb.append(getAbi()).append('-');
    if (!isPlatform()) {
      sb.append("addon-");
    }
    sb.append(SystemImage.DEFAULT_TAG.equals(getTag()) ? "android" : getTag().getId());
    sb.append('-');
    if (!isPlatform()) {
      sb.append(getAddonVendor().getId()).append('-');
    }
    sb.append(getAndroidVersion().getApiString());

    String s = sb.toString();
    s = s.toLowerCase(Locale.US).replaceAll("[^a-z0-9_.-]+", "_").replaceAll("_+", "_");
    return s;

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

    boolean isDefaultTag = SystemImage.DEFAULT_TAG.equals(mTag);
    return String.format("%1$s%2$s System Image%3$s", isDefaultTag ? "" : (mTag.getDisplay() + " "), getAbiDisplayName(),
                         isObsolete() ? " (Obsolete)" : "");
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  @Override
  public String getShortDescription() {
    String ld = getListDisplay();
    if (!ld.isEmpty()) {
      return String.format("%1$s, %2$s API %3$s, revision %4$s%5$s", ld, mAddonVendor == null ? "Android" : mAddonVendor.getDisplay(),
                           mVersion.getApiString(), getRevision().toShortString(), isObsolete() ? " (Obsolete)" : "");
    }

    boolean isDefaultTag = SystemImage.DEFAULT_TAG.equals(mTag);
    return String
      .format("%1$s%2$s System Image, %3$s API %4$s, revision %5$s%6$s", isDefaultTag ? "" : (mTag.getDisplay() + " "), getAbiDisplayName(),
              mAddonVendor == null ? "Android" : mAddonVendor.getDisplay(), mVersion.getApiString(), getRevision().toShortString(),
              isObsolete() ? " (Obsolete)" : "");
  }

  /**
   * Returns a long description for an {@link IDescription}.
   * <p/>
   * The long description is whatever the XML contains for the {@code description} field,
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

    s += String.format("\nRequires SDK Platform Android API %1$s", mVersion.getApiString());
    return s;
  }

  /**
   * Computes a potential installation folder if an archive of this package were
   * to be installed right away in the given SDK root.
   * <p/>
   * A system-image package is typically installed in SDK/systems/platform/tag/abi.
   * The name needs to be sanitized to be acceptable as a directory name.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
    File folder = new File(osSdkRoot, SdkConstants.FD_SYSTEM_IMAGES);
    folder = new File(folder, AndroidTargetHash.getPlatformHashString(mVersion));

    // Computes a folder directory using the sanitized tag & abi strings.
    String tag = mTag.getId();
    tag = tag.toLowerCase(Locale.US);
    tag = tag.replaceAll("[^a-z0-9_-]+", "_");      //$NON-NLS-1$ //$NON-NLS-2$
    tag = tag.replaceAll("_+", "_");                //$NON-NLS-1$ //$NON-NLS-2$
    tag = tag.replaceAll("-+", "-");                //$NON-NLS-1$ //$NON-NLS-2$

    folder = new File(folder, tag);

    String abi = mAbi;
    abi = abi.toLowerCase(Locale.US);
    abi = abi.replaceAll("[^a-z0-9_-]+", "_");      //$NON-NLS-1$ //$NON-NLS-2$
    abi = abi.replaceAll("_+", "_");                //$NON-NLS-1$ //$NON-NLS-2$
    abi = abi.replaceAll("-+", "-");                //$NON-NLS-1$ //$NON-NLS-2$

    folder = new File(folder, abi);
    return folder;
  }

  @Override
  public boolean sameItemAs(com.android.tools.idea.sdk.remote.internal.packages.Package pkg) {
    if (pkg instanceof SystemImagePackage) {
      SystemImagePackage newPkg = (SystemImagePackage)pkg;

      // check they are the same tag, abi and version.
      return getTag().equals(newPkg.getTag()) &&
             getAbi().equals(newPkg.getAbi()) &&
             getAndroidVersion().equals(newPkg.getAndroidVersion()) &&
             (mAddonVendor == newPkg.mAddonVendor || (mAddonVendor != null && mAddonVendor.equals(newPkg.mAddonVendor)));
    }

    return false;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((mAddonVendor == null) ? 0 : mAddonVendor.hashCode());
    result = prime * result + ((mTag == null) ? 0 : mTag.hashCode());
    result = prime * result + ((mAbi == null) ? 0 : mAbi.hashCode());
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
    if (!(obj instanceof SystemImagePackage)) {
      return false;
    }
    SystemImagePackage other = (SystemImagePackage)obj;
    if (mAddonVendor == null) {
      if (other.mAddonVendor != null) {
        return false;
      }
    }
    else if (!mAddonVendor.equals(other.mAddonVendor)) {
      return false;
    }
    if (mTag == null) {
      if (other.mTag != null) {
        return false;
      }
    }
    else if (!mTag.equals(other.mTag)) {
      return false;
    }
    if (mAbi == null) {
      if (other.mAbi != null) {
        return false;
      }
    }
    else if (!mAbi.equals(other.mAbi)) {
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
    return true;
  }

  /**
   * For sys img packages, we want to add tag/abi to the sorting key
   * <em>before<em/> the revision number.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  protected String comparisonKey() {
    String s = super.comparisonKey();
    int pos = s.indexOf("|r:");                 //$NON-NLS-1$
    assert pos > 0;
    s = s.substring(0, pos) +
        "|vend:" + (mAddonVendor == null ? "" : mAddonVendor.getId()) + //$NON-NLS-1$ //$NON-NLS-2$
        "|tag:" + getTag().getId() +            //$NON-NLS-1$
        "|abi:" + getAbiDisplayName() +         //$NON-NLS-1$
        s.substring(pos);
    return s;
  }
}
