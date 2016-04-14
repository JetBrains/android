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

import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionHelper;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.local.LocalSysImgPkgInfo;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.RepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSysImgConstants;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a system-image XML node in an SDK repository.
 */
public class RemoteSystemImagePkgInfo extends RemotePkgInfo {

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
  public RemoteSystemImagePkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    int apiLevel = RemotePackageParserUtils.getXmlInt(packageNode, RepoConstants.NODE_API_LEVEL, 0);
    String codeName = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    AndroidVersion version = new AndroidVersion(apiLevel, codeName);

    String abi = RemotePackageParserUtils.getXmlString(packageNode, RepoConstants.NODE_ABI);

    // tag id
    String tagId = RemotePackageParserUtils.getXmlString(packageNode, SdkSysImgConstants.ATTR_TAG_ID, SystemImage.DEFAULT_TAG.getId());
    String tagDisp = RemotePackageParserUtils.getOptionalXmlString(packageNode, SdkSysImgConstants.ATTR_TAG_DISPLAY);
    if (tagDisp == null || tagDisp.isEmpty()) {
      tagDisp = LocalSysImgPkgInfo.tagIdToDisplay(tagId);
    }
    assert tagId != null;
    IdDisplay tag = IdDisplay.create(tagId, tagDisp);


    Node addonNode = RemotePackageParserUtils.findChildElement(packageNode, SdkSysImgConstants.NODE_ADD_ON);

    PkgDesc.Builder descBuilder;
    IdDisplay vendor = null;

    if (addonNode == null) {
      // A platform system-image
      descBuilder = PkgDesc.Builder.newSysImg(version, tag, abi, getRevision());
    }
    else {
      // An add-on system-image
      String vendorId = RemotePackageParserUtils.getXmlString(addonNode, RepoConstants.NODE_VENDOR_ID);
      String vendorDisp = RemotePackageParserUtils.getXmlString(addonNode, RepoConstants.NODE_VENDOR_DISPLAY, vendorId);

      assert vendorId.length() > 0;
      assert vendorDisp.length() > 0;

      vendor = IdDisplay.create(vendorId, vendorDisp);

      descBuilder = PkgDesc.Builder.newAddonSysImg(version, vendor, tag, abi, getRevision());
    }
    descBuilder
      .setDescriptionShort(LocalSysImgPkgInfo.createShortDescription(getListDisplay(), abi, vendor, tag, version, getRevision(),
                                                                     isObsolete()));
    descBuilder.setDescriptionUrl(getDescUrl());
    descBuilder.setListDisplay(
      LocalSysImgPkgInfo.createListDescription(mListDisplay, tag, LocalSysImgPkgInfo.getAbiDisplayNameInternal(abi), isObsolete()));
    descBuilder.setIsObsolete(isObsolete());
    descBuilder.setLicense(getLicense());

    mPkgDesc = descBuilder.create();
  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be given to a constructor that takes a {@link Properties} object.
   */
  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);

    AndroidVersionHelper.saveProperties(getAndroidVersion(), props);
    props.setProperty(PkgProps.SYS_IMG_ABI, getAbi());
    props.setProperty(PkgProps.SYS_IMG_TAG_ID, getTag().getId());
    props.setProperty(PkgProps.SYS_IMG_TAG_DISPLAY, getTag().getDisplay());

    IdDisplay addonVendor = getAddonVendor();
    if (addonVendor != null) {
      props.setProperty(PkgProps.ADDON_VENDOR_ID, addonVendor.getId());
      props.setProperty(PkgProps.ADDON_VENDOR_DISPLAY, addonVendor.getDisplay());
    }
  }

  /**
   * Returns the tag of the system-image.
   */
  public IdDisplay getTag() {
    return getPkgDesc().getTag();
  }

  /**
   * Returns the ABI of the system-image. Cannot be null nor empty.
   */
  public String getAbi() {
    return getPkgDesc().getPath();
  }

  /**
   * Returns the version of the platform dependency of this package.
   * <p/>
   * A system-image has the same {@link AndroidVersion} as the platform it depends on.
   */
  @NotNull
  public AndroidVersion getAndroidVersion() {
    return getPkgDesc().getAndroidVersion();
  }

  /**
   * Returns true if the system-image belongs to a standard Android platform.
   * In this case {@link #getAddonVendor()} returns null.
   * <p/.
   * Returns false if the system-image belongs to an add-on.
   * In this case {@link #getAndroidVersion()} returns a non-null {@link IdDisplay}.
   */
  public boolean isPlatform() {
    return getAddonVendor() == null;
  }

  /**
   * Returns the add-on vendor if this is an add-on system image.
   * Returns null if this is a platform system-image.
   */
  @Nullable
  public IdDisplay getAddonVendor() {
    return getPkgDesc().getVendor();
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

}
