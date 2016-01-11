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
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.util.Map;
import java.util.Properties;

/**
 * Represents a doc XML node in an SDK repository.
 */
public class RemoteDocPkgInfo extends RemotePkgInfo {

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
  public RemoteDocPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    int apiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    AndroidVersion version = new AndroidVersion(apiLevel, codeName);

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newDoc(version, getRevision());
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), version, isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, version, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be give the constructor that takes a {@link Properties} object.
   */
  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);

    AndroidVersionHelper.saveProperties(getPkgDesc().getAndroidVersion(), props);
  }

  /**
   * Returns the version, for platform, add-on and doc packages.
   * Can be 0 if this is a local package of unknown api-level.
   */
  @NotNull
  public AndroidVersion getAndroidVersion() {
    return getPkgDesc().getAndroidVersion();
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For docs, we use "doc-N" where N is the API or the preview codename.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    return "doc";    //$NON-NLS-1$
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   */
  private static String createListDescription(String listDisplay, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return listDisplay;
    }
   return "Documentation for Android SDK";
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, Revision revision, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    if (version.isPreview()) {
      return String
        .format("Documentation for Android '%1$s' Preview SDK, revision %2$s%3$s", version.getCodename(), revision.toShortString(),
                obsolete ? " (Obsolete)" : "");
    }
    else {
      return String
        .format("Documentation for Android SDK, API %1$d, revision %2$s%3$s", version.getApiLevel(), revision.toShortString(),
                obsolete ? " (Obsolete)" : "");
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (getPkgDesc().hasAndroidVersion() ? 0 : getPkgDesc().getAndroidVersion().hashCode());
    return result;
  }
}
