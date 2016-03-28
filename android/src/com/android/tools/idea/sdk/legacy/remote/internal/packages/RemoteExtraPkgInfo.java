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

package com.android.tools.idea.sdk.legacy.remote.internal.packages;

import com.android.repository.Revision;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDescExtra;
import com.android.sdklib.repository.legacy.local.LocalExtraPkgInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.sdk.legacy.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.RepoConstants;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * Represents a extra XML node in an SDK repository.
 */
public class RemoteExtraPkgInfo extends RemotePkgInfo implements IMinApiLevelDependency, IMinToolsDependency {

  /**
   * Mixin handling the min-tools dependency.
   */
  private final MinToolsMixin mMinToolsMixin;

  /**
   * The extra display name. Used in the UI to represent the package. It can be anything.
   */
  private final String mDisplayName;

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

  /**
   * Creates a new tool package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public RemoteExtraPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    mMinToolsMixin = new MinToolsMixin(packageNode);

    mPath = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_PATH);

    // Read name-display, vendor-display and vendor-id, introduced in addon-4.xsd.
    // These are not optional, they are mandatory in addon-4 but we still treat them
    // as optional so that we can fallback on using <vendor> which was the only one
    // defined in addon-3.xsd.
    String name = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_NAME_DISPLAY);
    String vname = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_VENDOR_DISPLAY);
    String vid = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_VENDOR_ID);

    if (vid.length() == 0) {
      // If vid is missing, use the old <vendor> attribute.
      // Note that in a valid XML, vendor-id cannot be an empty string.
      // The only reason vid can be empty is when <vendor-id> is missing, which
      // happens in an addon-3 schema, in which case the old <vendor> needs to be used.
      String vendor = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_VENDOR);
      vid = sanitizeLegacyVendor(vendor);
      if (vname.length() == 0) {
        vname = vendor;
      }
    }
    if (vname.length() == 0) {
      // The vendor-display name can be empty, in which case we use the vendor-id.
      vname = vid;
    }
    IdDisplay vendor = IdDisplay.create(vid.trim(), vname.trim());

    if (name.length() == 0) {
      // If name is missing, use the <path> attribute as done in an addon-3 schema.
      name = LocalExtraPkgInfo.getPrettyName(vendor, mPath);
    }
    mDisplayName = name.trim();

    mMinApiLevel = RemotePackageParserUtils.getXmlInt(packageNode, RepoConstants.NODE_MIN_API_LEVEL, MIN_API_LEVEL_NOT_SPECIFIED);

    mProjectFiles = parseProjectFiles(RemotePackageParserUtils.findChildElement(packageNode, RepoConstants.NODE_PROJECT_FILES));

    mOldPaths = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_OLD_PATHS);

    Revision revision = getRevision();
    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newExtra(vendor, mPath, mDisplayName, getOldPaths(), revision);
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), mDisplayName, isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, mDisplayName, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
  }

  private String[] parseProjectFiles(Node projectFilesNode) {
    ArrayList<String> paths = new ArrayList<String>();

    if (projectFilesNode != null) {
      String nsUri = projectFilesNode.getNamespaceURI();
      for (Node child = projectFilesNode.getFirstChild(); child != null; child = child.getNextSibling()) {

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
   * The minimal revision of the tools package required by this extra package, if > 0,
   * or {@link #MIN_TOOLS_REV_NOT_SPECIFIED} if there is no such requirement.
   */
  @Override
  public Revision getMinToolsRevision() {
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

  public String getDisplayName() {
    return mDisplayName;
  }

  /**
   * Transforms the legacy vendor name into a usable vendor id.
   */
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
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   */
  private static String createListDescription(String listDisplay, String displayName, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    String s = String.format("%1$s%2$s", displayName, obsolete ? " (Obsolete)" : "");  //$NON-NLS-2$

    return s;
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, Revision revision, String displayName, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    String s =
      String.format("%1$s, revision %2$s%3$s", displayName, revision.toShortString(), obsolete ? " (Obsolete)" : "");  //$NON-NLS-2$

    return s;
  }

  // ---

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = mMinToolsMixin.hashCode(super.hashCode());
    result = prime * result + mMinApiLevel;
    result = prime * result + ((mPath == null) ? 0 : mPath.hashCode());
    result = prime * result + Arrays.hashCode(mProjectFiles);
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
    if (!(obj instanceof RemoteExtraPkgInfo)) {
      return false;
    }
    RemoteExtraPkgInfo other = (RemoteExtraPkgInfo)obj;
    if (mMinApiLevel != other.mMinApiLevel) {
      return false;
    }
    if (mPath == null) {
      if (other.mPath != null) {
        return false;
      }
    }
    else if (!mPath.equals(other.mPath)) {
      return false;
    }
    if (!Arrays.equals(mProjectFiles, other.mProjectFiles)) {
      return false;
    }
    return mMinToolsMixin.equals(obj);
  }
}
