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

import com.android.repository.Revision;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionHelper;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.local.LocalAddonPkgInfo;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkAddonConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.*;

/**
 * Represents an add-on XML node in an SDK repository.
 */
public class RemoteAddonPkgInfo extends RemotePkgInfo {

  /**
   * The helper handling the layoutlib version.
   */
  private final LayoutlibVersionMixin mLayoutlibVersion;

  /**
   * An add-on library.
   */
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
      Lib other = (Lib)obj;
      if (mDescription == null) {
        if (other.mDescription != null) {
          return false;
        }
      }
      else if (!mDescription.equals(other.mDescription)) {
        return false;
      }
      if (mName == null) {
        if (other.mName != null) {
          return false;
        }
      }
      else if (!mName.equals(other.mName)) {
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
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public RemoteAddonPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    // --- name id/display ---
    // addon-4.xsd introduces the name-id, name-display, vendor-id and vendor-display.
    // These are not optional but we still need to support a fallback for older addons
    // that only provide name and vendor. If the addon provides neither set of fields,
    // it will simply not work as expected.

    String nameId = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_NAME_ID).trim();
    String nameDisp = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_NAME_DISPLAY).trim();
    String name = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_NAME).trim();

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

    // --- vendor id/display ---
    // Same processing for vendor id vs display

    String vendorId = RemotePackageParserUtils.getXmlString(packageNode, SdkAddonConstants.NODE_VENDOR_ID).trim();
    String vendorDisp = RemotePackageParserUtils.getXmlString(packageNode, SdkAddonConstants.NODE_VENDOR_DISPLAY).trim();
    String vendor = RemotePackageParserUtils.getXmlString(packageNode, SdkAddonConstants.NODE_VENDOR).trim();

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

    // --- other attributes

    int apiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkAddonConstants.NODE_API_LEVEL, 0);
    AndroidVersion androidVersion = new AndroidVersion(apiLevel, null /*codeName*/);

    mLibs = parseLibs(RemotePackageParserUtils.findChildElement(packageNode, SdkAddonConstants.NODE_LIBS));

    mLayoutlibVersion = new LayoutlibVersionMixin(packageNode);

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder
      .newAddon(androidVersion, getRevision(), IdDisplay.create(vendorId, vendorDisp), IdDisplay.create(nameId, nameDisp));
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), nameDisp, androidVersion, isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, nameDisp, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();

  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be given to a constructor that takes a {@link Properties} object.
   */
  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);

    AndroidVersionHelper.saveProperties(getAndroidVersion(), props);
    mLayoutlibVersion.saveProperties(props);

    props.setProperty(PkgProps.ADDON_NAME_ID, getPkgDesc().getName().getId());
    props.setProperty(PkgProps.ADDON_NAME_DISPLAY, getPkgDesc().getName().getDisplay());
    props.setProperty(PkgProps.ADDON_VENDOR_ID, getPkgDesc().getVendor().getId());
    props.setProperty(PkgProps.ADDON_VENDOR_DISPLAY, getPkgDesc().getVendor().getDisplay());
  }

  /**
   * Parses a <libs> element.
   */
  private Lib[] parseLibs(Node libsNode) {
    ArrayList<Lib> libs = new ArrayList<Lib>();

    if (libsNode != null) {
      String nsUri = libsNode.getNamespaceURI();
      for (Node child = libsNode.getFirstChild(); child != null; child = child.getNextSibling()) {

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
    return new Lib(RemotePackageParserUtils.getXmlString(libNode, SdkRepoConstants.NODE_NAME),
                   RemotePackageParserUtils.getXmlString(libNode, SdkRepoConstants.NODE_DESCRIPTION));
  }

  /**
   * Returns the version of the platform dependency of this package.
   * <p/>
   * An add-on has the same {@link AndroidVersion} as the platform it depends on.
   */
  @NotNull
  public AndroidVersion getAndroidVersion() {
    return getPkgDesc().getAndroidVersion();
  }

  /**
   * Returns the libs defined in this add-on. Can be an empty array but not null.
   */
  @NotNull
  public Lib[] getLibs() {
    return mLibs;
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For add-ons, we use "addon-vendor-name-N" where N is the base platform API.
   * <p/>
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String installId() {
    return encodeAddonName(getPkgDesc().getName().getId(), getPkgDesc().getVendor().getId(), getAndroidVersion());
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   */
  private static String createListDescription(String listDisplay, String displayName, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    return String.format("%1$s%2$s", displayName, obsolete ? " (Obsolete)" : "");
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay,
                                               Revision revision,
                                               String displayName,
                                               AndroidVersion version,
                                               boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    return String.format("%1$s, Android API %2$s, revision %3$s%4$s", displayName, version.getApiString(), revision.toShortString(),
                         obsolete ? " (Obsolete)" : "");
  }

  private static String encodeAddonName(String nameId, String vendorId, AndroidVersion version) {
    String name = String.format("addon-%s-%s-%s",     //$NON-NLS-1$
                                nameId, vendorId, version.getApiString());
    name = name.toLowerCase(Locale.US);
    name = name.replaceAll("[^a-z0-9_-]+", "_");      //$NON-NLS-1$ //$NON-NLS-2$
    name = name.replaceAll("_+", "_");                //$NON-NLS-1$ //$NON-NLS-2$
    return name;
  }

  @Override
  public boolean sameItemAs(LocalPkgInfo pkg, Revision.PreviewComparison previewComparison) {
    if (pkg instanceof LocalAddonPkgInfo) {
      LocalAddonPkgInfo localPkg = (LocalAddonPkgInfo)pkg;

      String nameId = getPkgDesc().getName().getId();

      // check they are the same add-on.
      if (Objects.equal(nameId, localPkg.getDesc().getName()) &&
          getAndroidVersion().equals(localPkg.getDesc().getAndroidVersion())) {
        // Check the vendor-id field.
        if (getPkgDesc().getVendor().equals(localPkg.getDesc().getVendor())) {
          return true;
        }
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
    String name = getPkgDesc().getName().getDisplay();
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + (getPkgDesc().hasVendor() ? 0 : getPkgDesc().getVendor().hashCode());
    result = prime * result + getAndroidVersion().hashCode();
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
    if (!(obj instanceof RemoteAddonPkgInfo)) {
      return false;
    }
    RemoteAddonPkgInfo other = (RemoteAddonPkgInfo)obj;
    if (mLayoutlibVersion == null) {
      if (other.mLayoutlibVersion != null) {
        return false;
      }
    }
    else if (!mLayoutlibVersion.equals(other.mLayoutlibVersion)) {
      return false;
    }
    if (!Arrays.equals(mLibs, other.mLibs)) {
      return false;
    }
    return true;
  }
}
