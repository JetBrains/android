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
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Represents a sample XML node in an SDK repository.
 */
public class RemoteSamplePkgInfo extends RemoteMinToolsPkgInfo implements IMinApiLevelDependency {

  /**
   * The minimal API level required by this extra package, if > 0,
   * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
   */
  private final int mMinApiLevel;

  /**
   * Creates a new sample package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public RemoteSamplePkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    int apiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    AndroidVersion version = new AndroidVersion(apiLevel, codeName);

    mMinApiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_MIN_API_LEVEL, MIN_API_LEVEL_NOT_SPECIFIED);

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newSample(version, getRevision(), getMinToolsRevision());
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), version, isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, version, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
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
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   */
  private static String createListDescription(String listDisplay, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    String s = String
      .format("Samples for SDK API %1$s%2$s%3$s", version.getApiString(), version.isPreview() ? " Preview" : "",
              obsolete ? " (Obsolete)" : "");
    return s;
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, Revision revision, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    String s = String
      .format("Samples for SDK API %1$s%2$s, revision %3$s%4$s", version.getApiString(), version.isPreview() ? " Preview" : "",
              revision.toShortString(), obsolete ? " (Obsolete)" : "");
    return s;
  }
}
