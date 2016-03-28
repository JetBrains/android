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

package com.android.tools.idea.sdk.legacy.remote;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;
import com.android.repository.api.License;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.CommonFactory;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.tools.idea.sdk.legacy.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.legacy.remote.internal.packages.RemotePackageParserUtils;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;


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
}
