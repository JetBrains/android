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
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.legacy.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Represents a tool XML node in an SDK repository.
 */
public class RemoteToolPkgInfo extends RemotePkgInfo {

  /**
   * The value of {@link #getMinPlatformToolsRevision()} when the
   * {@link SdkRepoConstants#NODE_MIN_PLATFORM_TOOLS_REV} was not specified in the XML source.
   * Since this is a required attribute in the XML schema, it can only happen when dealing
   * with an invalid repository XML.
   */
  Revision MIN_PLATFORM_TOOLS_REV_INVALID = new Revision(Revision.MISSING_MAJOR_REV);

  /**
   * The minimal revision of the platform-tools package required by this package
   * or {@link #MIN_PLATFORM_TOOLS_REV_INVALID} if the value was missing.
   */
  private final Revision mMinPlatformToolsRevision;

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
  public RemoteToolPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    mMinPlatformToolsRevision = RemotePackageParserUtils
      .parseRevisionElement(RemotePackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_MIN_PLATFORM_TOOLS_REV));

    if (mMinPlatformToolsRevision.equals(MIN_PLATFORM_TOOLS_REV_INVALID)) {
      // This revision number is mandatory starting with sdk-repository-3.xsd
      // and did not exist before. Complain if the URI has level >= 3.
      if (SdkRepoConstants.versionGreaterOrEqualThan(nsUri, 3)) {
        throw new IllegalArgumentException(String
                                             .format("Missing %1$s element in %2$s package", SdkRepoConstants.NODE_MIN_PLATFORM_TOOLS_REV,
                                                     SdkRepoConstants.NODE_PLATFORM_TOOL));
      }
    }

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newTool(getRevision(), mMinPlatformToolsRevision);
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   * {@inheritDoc}
   */
  private static String createListDescription(String listDisplay, boolean obsolete) {
    return String.format("%1$s%2$s", listDisplay.isEmpty() ? "Android SDK Tools" : listDisplay, obsolete ? " (Obsolete)" : "");
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, Revision revision, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    return String.format("Android SDK Tools, revision %1$s%2$s", revision.toShortString(), obsolete ? " (Obsolete)" : "");
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((mMinPlatformToolsRevision == null) ? 0 : mMinPlatformToolsRevision.hashCode());
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
    if (!(obj instanceof RemoteToolPkgInfo)) {
      return false;
    }
    RemoteToolPkgInfo other = (RemoteToolPkgInfo)obj;
    if (mMinPlatformToolsRevision == null) {
      if (other.mMinPlatformToolsRevision != null) {
        return false;
      }
    }
    else if (!mMinPlatformToolsRevision.equals(other.mMinPlatformToolsRevision)) {
      return false;
    }
    return true;
  }
}
