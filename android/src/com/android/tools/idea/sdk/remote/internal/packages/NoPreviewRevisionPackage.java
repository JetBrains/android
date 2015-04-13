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

import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.Map;
import java.util.Properties;

/**
 * Represents a package in an SDK repository that has a {@link NoPreviewRevision},
 * which is a single major.minor.micro revision number and no preview.
 */
public abstract class NoPreviewRevisionPackage extends Package {

  private final NoPreviewRevision mRevision;

  /**
   * Creates a new package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  NoPreviewRevisionPackage(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    mRevision =
      PackageParserUtils.parseNoPreviewRevisionElement(PackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_REVISION));
  }

  /**
   * Returns the revision, an int > 0, for all packages (platform, add-on, tool, doc).
   * Can be 0 if this is a local package of unknown revision.
   */
  @Override
  public NoPreviewRevision getRevision() {
    return mRevision;
  }


  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);
    props.setProperty(PkgProps.PKG_REVISION, mRevision.toString());
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((mRevision == null) ? 0 : mRevision.hashCode());
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
    if (!(obj instanceof NoPreviewRevisionPackage)) {
      return false;
    }
    NoPreviewRevisionPackage other = (NoPreviewRevisionPackage)obj;
    if (mRevision == null) {
      if (other.mRevision != null) {
        return false;
      }
    }
    else if (!mRevision.equals(other.mRevision)) {
      return false;
    }
    return true;
  }

  @Override
  public UpdateInfo canBeUpdatedBy(Package replacementPackage) {
    if (replacementPackage == null) {
      return UpdateInfo.INCOMPATIBLE;
    }

    // check they are the same item.
    if (!sameItemAs(replacementPackage)) {
      return UpdateInfo.INCOMPATIBLE;
    }

    // check revision number
    if (replacementPackage.getRevision().compareTo(this.getRevision()) > 0) {
      return UpdateInfo.UPDATE;
    }

    // not an upgrade but not incompatible either.
    return UpdateInfo.NOT_UPDATE;
  }


}
