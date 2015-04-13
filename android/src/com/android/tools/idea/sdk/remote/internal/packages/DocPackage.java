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
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a doc XML node in an SDK repository.
 * <p/>
 * Note that a doc package has a version and thus implements {@link IAndroidVersionProvider}.
 * However there is no mandatory dependency that limits installation so this does not
 * implement {@link IPlatformDependency}.
 */
public class DocPackage extends MajorRevisionPackage implements IAndroidVersionProvider {

  private final AndroidVersion mVersion;
  private final IPkgDesc mPkgDesc;

  /**
   * Creates a new doc package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public DocPackage(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    int apiLevel = PackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = PackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    mVersion = new AndroidVersion(apiLevel, codeName);

    mPkgDesc = setDescriptions(PkgDesc.Builder.newDoc(mVersion, (MajorRevision)getRevision())).create();
  }

  @Override
  @NonNull
  public IPkgDesc getPkgDesc() {
    return mPkgDesc;
  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be give the constructor that takes a {@link Properties} object.
   */
  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);

    mVersion.saveProperties(props);
  }

  /**
   * Returns the version, for platform, add-on and doc packages.
   * Can be 0 if this is a local package of unknown api-level.
   */
  @Override
  @NonNull
  public AndroidVersion getAndroidVersion() {
    return mVersion;
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For docs, we use "doc-N" where N is the API or the preview codename.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    return "doc-" + mVersion.getApiString();    //$NON-NLS-1$
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
    if (mVersion.isPreview()) {
      return String.format("Documentation for Android '%1$s' Preview SDK%2$s", mVersion.getCodename(), isObsolete() ? " (Obsolete)" : "");
    }
    else {
      return String.format("Documentation for Android SDK%2$s", mVersion.getApiLevel(), isObsolete() ? " (Obsolete)" : "");
    }
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

    if (mVersion.isPreview()) {
      return String
        .format("Documentation for Android '%1$s' Preview SDK, revision %2$s%3$s", mVersion.getCodename(), getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "");
    }
    else {
      return String
        .format("Documentation for Android SDK, API %1$d, revision %2$s%3$s", mVersion.getApiLevel(), getRevision().toShortString(),
                isObsolete() ? " (Obsolete)" : "");
    }
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
   * A "doc" package should always be located in SDK/docs.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
    return new File(osSdkRoot, SdkConstants.FD_DOCS);
  }

  /**
   * Consider doc packages to be the same if they cover the same API level,
   * regardless of their revision number.
   */
  @Override
  public boolean sameItemAs(Package pkg) {
    if (pkg instanceof DocPackage) {
      AndroidVersion rev2 = ((DocPackage)pkg).getAndroidVersion();
      return this.getAndroidVersion().equals(rev2);
    }

    return false;
  }

  /**
   * {@inheritDoc}
   * <hr>
   * Doc packages are a bit different since there can only be one doc installed at
   * the same time.
   * <p/>
   * We now consider that docs for different APIs are NOT updates, e.g. doc for API N+1
   * is no longer considered an update for doc API N.
   * However docs that have the same API version (API level + codename) are considered
   * updates if they have a higher revision number (so 15 rev 2 is an update for 15 rev 1,
   * but is not an update for 14 rev 1.)
   */
  @Override
  public UpdateInfo canBeUpdatedBy(Package replacementPackage) {
    // check they are the same kind of object
    if (!(replacementPackage instanceof DocPackage)) {
      return UpdateInfo.INCOMPATIBLE;
    }

    DocPackage replacementDoc = (DocPackage)replacementPackage;

    AndroidVersion replacementVersion = replacementDoc.getAndroidVersion();

    // Check if they're the same exact (api and codename)
    if (replacementVersion.equals(mVersion)) {
      // exact same version, so check the revision level
      if (replacementPackage.getRevision().compareTo(this.getRevision()) > 0) {
        return UpdateInfo.UPDATE;
      }
    }
    else {
      // not the same version? we check if they have the same api level and the new one
      // is a preview, in which case it's also an update (since preview have the api level
      // of the _previous_ version.)
      if (replacementVersion.getApiLevel() == mVersion.getApiLevel() && replacementVersion.isPreview()) {
        return UpdateInfo.UPDATE;
      }
    }

    // not an upgrade but not incompatible either.
    return UpdateInfo.NOT_UPDATE;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
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
    if (!(obj instanceof DocPackage)) {
      return false;
    }
    DocPackage other = (DocPackage)obj;
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
}
