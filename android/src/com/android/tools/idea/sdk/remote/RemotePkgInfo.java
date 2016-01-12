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

package com.android.tools.idea.sdk.remote;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.License;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.io.FileOp;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.packages.RemotePackageParserUtils;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.google.common.base.Objects;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.w3c.dom.Node;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;


/**
 * This class provides information on a remote package available for download
 * via a remote SDK repository server.
 */
public abstract class RemotePkgInfo implements Comparable<RemotePkgInfo> {
  /**
   * Enum for the result of {@link Package#canBeUpdatedBy(Package)}. This is used so that we can
   * differentiate between a package that is totally incompatible, and one that is the same item
   * but just not an update.
   *
   * @see #canBeUpdatedBy(Package)
   */
  public enum UpdateInfo {
    /**
     * Means that the 2 packages are not the same thing
     */
    INCOMPATIBLE,
    /**
     * Means that the 2 packages are the same thing but one does not upgrade the other.
     * </p>
     * TODO: this name is confusing. We need to dig deeper.
     */
    NOT_UPDATE,
    /**
     * Means that the 2 packages are the same thing, and one is the upgrade of the other
     */
    UPDATE
  }


  /**
   * Information on the package provided by the remote server.
   */
  @NonNull protected IPkgDesc mPkgDesc;

  protected final String mObsolete;
  protected final License mLicense;
  protected final String mListDisplay;
  protected final String mDescription;
  protected final String mDescUrl;
  protected Revision mRevision;

  protected final Archive[] mArchives;
  protected final SdkSource mSource;

  // figure if we'll need to set the unix permissions
  private static final boolean sUsingUnixPerm =
    SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN || SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX;

  public RemotePkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    mSource = source;
    mListDisplay = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_LIST_DISPLAY);
    mDescription = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_DESCRIPTION);
    mDescUrl = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_DESC_URL);
    mObsolete = RemotePackageParserUtils.getOptionalXmlString(packageNode, SdkRepoConstants.NODE_OBSOLETE);

    mLicense = parseLicense(packageNode, licenses);
    mArchives = parseArchives(RemotePackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_ARCHIVES));
    mRevision =
      RemotePackageParserUtils
        .parseRevisionElement(RemotePackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_REVISION));
  }

  /**
   * Information on the package provided by the remote server.
   */
  @NonNull
  public IPkgDesc getPkgDesc() {
    return mPkgDesc;
  }

  /**
   * Returns the size (in bytes) of all the archives that make up this package.
   */
  public long getDownloadSize() {
    long size = 0;
    for (Archive archive : mArchives) {
      if (archive.isCompatible()) {
        size += archive.getSize();
      }
    }
    return size;
  }

  //---- Ordering ----

  /**
   * Compares 2 packages by comparing their {@link IPkgDesc}.
   * The source is not used in the comparison.
   */
  @Override
  public int compareTo(@NonNull RemotePkgInfo o) {
    return mPkgDesc.compareTo(o.mPkgDesc);
  }

  /**
   * The remote package hash code is based on the underlying {@link IPkgDesc}.
   * The source is not used in the hash code.
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mPkgDesc == null) ? 0 : mPkgDesc.hashCode());
    return result;
  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be give the constructor that takes a {@link Properties} object.
   */
  public void saveProperties(@NonNull Properties props) {
    if (mLicense != null) {
      String license = mLicense.getValue();
      if (license != null && license.length() > 0) {
        props.setProperty(PkgProps.PKG_LICENSE, license);
      }
      String licenseRef = mLicense.getId();
      if (licenseRef != null && licenseRef.length() > 0) {
        props.setProperty(PkgProps.PKG_LICENSE_REF, licenseRef);
      }
    }
    if (mListDisplay != null && mListDisplay.length() > 0) {
      props.setProperty(PkgProps.PKG_LIST_DISPLAY, mListDisplay);
    }
    if (mDescription != null && mDescription.length() > 0) {
      props.setProperty(PkgProps.PKG_DESC, mDescription);
    }
    if (mDescUrl != null && mDescUrl.length() > 0) {
      props.setProperty(PkgProps.PKG_DESC_URL, mDescUrl);
    }
    if (mObsolete != null) {
      props.setProperty(PkgProps.PKG_OBSOLETE, mObsolete);
    }
    if (mSource != null) {
      props.setProperty(PkgProps.PKG_SOURCE_URL, mSource.getUrl());
    }
    props.setProperty(PkgProps.PKG_REVISION, mRevision.toString());
  }

  /**
   * Parses the uses-licence node of this package, if any, and returns the license
   * definition if there's one. Returns null if there's no uses-license element or no
   * license of this name defined.
   */
  @Nullable
  private License parseLicense(@NonNull Node packageNode, @NonNull Map<String, String> licenses) {
    Node usesLicense = RemotePackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_USES_LICENSE);
    if (usesLicense != null) {
      Node ref = usesLicense.getAttributes().getNamedItem(SdkRepoConstants.ATTR_REF);
      if (ref != null) {
        String licenseRef = ref.getNodeValue();
        CommonFactory f = (CommonFactory)RepoManager.getCommonModule().createLatestFactory();
        License l = f.createLicenseType();
        l.setId(licenseRef);
        l.setValue(licenses.get(licenseRef));
        return l;
      }
    }
    return null;
  }

  /**
   * Parses an XML node to process the <archives> element.
   * Always return a non-null array. The array may be empty.
   */
  @NonNull
  private Archive[] parseArchives(@NonNull Node archivesNode) {
    ArrayList<Archive> archives = new ArrayList<Archive>();

    if (archivesNode != null) {
      String nsUri = archivesNode.getNamespaceURI();
      for (Node child = archivesNode.getFirstChild(); child != null; child = child.getNextSibling()) {

        if (child.getNodeType() == Node.ELEMENT_NODE &&
            nsUri.equals(child.getNamespaceURI()) &&
            SdkRepoConstants.NODE_ARCHIVE.equals(child.getLocalName())) {
          archives.add(parseArchive(child));
        }
      }
    }

    return archives.toArray(new Archive[archives.size()]);
  }

  /**
   * Parses one <archive> element from an <archives> container.
   */
  @NonNull
  private Archive parseArchive(@NonNull Node archiveNode) {
    Archive a = new Archive(this, RemotePackageParserUtils.parseArchFilter(archiveNode),
                            RemotePackageParserUtils.getXmlString(archiveNode, SdkRepoConstants.NODE_URL),
                            RemotePackageParserUtils.getXmlLong(archiveNode, SdkRepoConstants.NODE_SIZE, 0),
                            RemotePackageParserUtils.getXmlString(archiveNode, SdkRepoConstants.NODE_CHECKSUM));

    return a;
  }

  /**
   * Returns the source that created (and owns) this package. Can be null.
   */
  @Nullable
  public SdkSource getParentSource() {
    return mSource;
  }

  /**
   * Returns true if the package is deemed obsolete, that is it contains an
   * actual <code>&lt;obsolete&gt;</code> element.
   */
  public boolean isObsolete() {
    return mObsolete != null;
  }


  /**
   * Returns the revision for this package.
   */
  @NonNull
  public Revision getRevision() {
    return mRevision;
  }

  /**
   * Returns the optional description for all packages (platform, add-on, tool, doc) or
   * for a lib. It is null if the element has not been specified in the repository XML.
   */
  @Nullable
  public License getLicense() {
    return mLicense;
  }

  /**
   * Returns the optional description for all packages (platform, add-on, tool, doc) or
   * for a lib. This is the raw description available from the XML meta data and is typically
   * only used internally.
   * <p/>
   * For actual display in the UI, use the methods from {@link IDescription} instead.
   * <p/>
   * Can be empty but not null.
   */
  @NonNull
  public String getDescription() {
    return mDescription;
  }

  /**
   * Returns the optional list-display for all packages as defined in the XML meta data
   * and is typically only used internally.
   * <p/>
   * For actual display in the UI, use {@link IListDescription} instead.
   * <p/>
   * Can be empty but not null.
   */
  @NonNull
  public String getListDisplay() {
    return mListDisplay;
  }

  /**
   * Returns the optional description URL for all packages (platform, add-on, tool, doc).
   * Can be empty but not null.
   */
  @NonNull
  public String getDescUrl() {
    return mDescUrl;
  }

  /**
   * Returns the archives defined in this package.
   * Can be an empty array but not null.
   */
  @NonNull
  public Archive[] getArchives() {
    return mArchives;
  }

  /**
   * @return true if any of the archives in this package are compatible with the current
   * architecture.
   */
  public boolean hasCompatibleArchive() {
    for (Archive archive : mArchives) {
      if (archive.isCompatible()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a short, reasonably unique string identifier that can be used
   * to identify this package when installing from the command-line interface.
   * {@code 'android list sdk'} will show these IDs and then in turn they can
   * be provided to {@code 'android update sdk --no-ui --filter'} to select
   * some specific packages.
   * <p/>
   * The identifiers must have the following properties: <br/>
   * - They must contain only simple alphanumeric characters. <br/>
   * - Commas, whitespace and any special character that could be obviously problematic
   * to a shell interface should be avoided (so dash/underscore are OK, but things
   * like colon, pipe or dollar should be avoided.) <br/>
   * - The name must be consistent across calls and reasonably unique for the package
   * type. Collisions can occur but should be rare. <br/>
   * - Different package types should have a clearly different name pattern. <br/>
   * - The revision number should not be included, as this would prevent updates
   * from being automated (which is the whole point.) <br/>
   * - It must remain reasonably human readable. <br/>
   * - If no such id can exist (for example for a local package that cannot be installed)
   * then an empty string should be returned. Don't return null.
   * <p/>
   * Important: This is <em>not</em> a strong unique identifier for the package.
   * If you need a strong unique identifier, you should use {@link #comparisonKey()}
   * and the {@link Comparable} interface.
   */
  @NonNull
  // TODO: in each case this should be obtainable from the PkgDesc, so this shouldn't be needed.
  public abstract String installId();

  /**
   * Returns the short description of the source, if not null.
   * Otherwise returns the default Object toString result.
   * <p/>
   * This is mostly helpful for debugging.
   * For UI display, use the {@link IDescription} interface.
   */
  @NonNull
  @Override
  public String toString() {
    String s = getShortDescription();
    if (s != null) {
      return s;
    }
    return super.toString();
  }

  /**
   * Returns a short description for an {@link IDescription}.
   * Can be empty but not null.
   */
  @NonNull
  public final String getShortDescription() {
    return getPkgDesc().getDescriptionShort();
  }


  /**
   * Hook called right after a file has been unzipped (during an install).
   * <p/>
   * The base class implementation makes sure to properly adjust set executable
   * permission on Linux and MacOS system if the zip entry was marked as +x.
   *
   * @param archive      The archive that is being installed.
   * @param monitor      The {@link ITaskMonitor} to display errors.
   * @param fileOp       The {@link FileOp} used by the archive installer.
   * @param unzippedFile The file that has just been unzipped in the install temp directory.
   * @param zipEntry     The {@link ZipArchiveEntry} that has just been unzipped.
   */
  public void postUnzipFileHook(Archive archive, ITaskMonitor monitor, FileOp fileOp, File unzippedFile, ZipArchiveEntry zipEntry) {

    // if needed set the permissions.
    if (sUsingUnixPerm && fileOp.isFile(unzippedFile)) {
      // get the mode and test if it contains the executable bit
      int mode = zipEntry.getUnixMode();
      if ((mode & 0111) != 0) {
        try {
          fileOp.setExecutablePermission(unzippedFile);
        }
        catch (IOException ignore) {
        }
      }
    }

  }

  /**
   * Hook called right after an archive has been installed.
   *
   * @param archive       The archive that has been installed.
   * @param monitor       The {@link ITaskMonitor} to display errors.
   * @param installFolder The folder where the archive was successfully installed.
   *                      Null if the installation failed, in case the archive needs to
   *                      do some cleanup after <code>preInstallHook</code>.
   */
  public void postInstallHook(Archive archive, ITaskMonitor monitor, File installFolder) {
    // Nothing to do in base class.
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof RemotePkgInfo)) {
      return false;
    }
    RemotePkgInfo other = (RemotePkgInfo)obj;
    if (!Arrays.equals(mArchives, other.mArchives)) {
      return false;
    }
    if (mSource == null) {
      if (other.mSource != null) {
        return false;
      }
    }
    else if (!mSource.equals(other.mSource)) {
      return false;
    }
    return getPkgDesc().equals(other.getPkgDesc());
  }

  /**
   * Returns whether the give package represents the same item as the current package.
   * <p/>
   * Two packages are considered the same if they represent the same thing, except for the
   * revision number.
   *
   * @param pkg the package to compare.
   * @return true if the item as equivalent.
   */
  public UpdateInfo canUpdate(LocalPkgInfo localPkg) {
    if (localPkg == null) {
      return UpdateInfo.INCOMPATIBLE;
    }

    // check they are the same item, ignoring the preview bit.
    if (!sameItemAs(localPkg, Revision.PreviewComparison.IGNORE)) {
      return UpdateInfo.INCOMPATIBLE;
    }

    // a preview cannot update a non-preview
    // TODO(jbakermalone): review this logic
    if (getRevision().isPreview() && !localPkg.getDesc().getRevision().isPreview()) {
      return UpdateInfo.INCOMPATIBLE;
    }

    // check revision number
    if (localPkg.getDesc().getRevision().compareTo(this.getRevision()) < 0) {
      return UpdateInfo.UPDATE;
    }

    // not an upgrade but not incompatible either.
    return UpdateInfo.NOT_UPDATE;
  }

  /**
   * Returns whether the give package represents the same item as the current package.
   * <p/>
   * Two packages are considered the same if they represent the same thing, except for the
   * revision number.
   *
   * @param pkg the package to compare.
   * @return true if the item as equivalent.
   */
  protected boolean sameItemAs(LocalPkgInfo pkg, Revision.PreviewComparison comparePreview) {
    IPkgDesc desc = getPkgDesc();
    IPkgDesc other = pkg.getDesc();
    return Objects.equal(desc.getPath(), other.getPath()) &&
           Objects.equal(desc.getTag(), other.getTag()) &&
           Objects.equal(desc.getAndroidVersion(), other.getAndroidVersion()) &&
           Objects.equal(desc.getVendor(), other.getVendor()) &&
           (comparePreview == Revision.PreviewComparison.IGNORE ||
            desc.getRevision().isPreview() == other.getRevision().isPreview());
  }
}
