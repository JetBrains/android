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
import com.android.sdklib.repository.PkgProps;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import org.w3c.dom.Node;

import java.util.Properties;

/**
 * Represents an XML node in an SDK repository that has a min-tools-rev requirement.
 */
class MinToolsMixin implements IMinToolsDependency {

  /**
   * The minimal revision of the tools package required by this extra package, if > 0,
   * or {@link #MIN_TOOLS_REV_NOT_SPECIFIED} if there is no such requirement.
   */
  private final Revision mMinToolsRevision;

  /**
   * Creates a new mixin from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param packageNode The XML element being parsed.
   */
  MinToolsMixin(Node packageNode) {

    mMinToolsRevision =
      RemotePackageParserUtils
        .parseRevisionElement(RemotePackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_MIN_TOOLS_REV));
  }

  /**
   * The minimal revision of the tools package required by this extra package, if > 0,
   * or {@link #MIN_TOOLS_REV_NOT_SPECIFIED} if there is no such requirement.
   */
  @Override
  public Revision getMinToolsRevision() {
    return mMinToolsRevision;
  }

  public void saveProperties(Properties props) {
    if (!getMinToolsRevision().equals(MIN_TOOLS_REV_NOT_SPECIFIED)) {
      props.setProperty(PkgProps.MIN_TOOLS_REV, getMinToolsRevision().toShortString());
    }
  }

  @Override
  public int hashCode() {
    return hashCode(super.hashCode());
  }

  int hashCode(int superHashCode) {
    final int prime = 31;
    int result = superHashCode;
    result = prime * result + ((mMinToolsRevision == null) ? 0 : mMinToolsRevision.hashCode());
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
    if (!(obj instanceof IMinToolsDependency)) {
      return false;
    }
    IMinToolsDependency other = (IMinToolsDependency)obj;
    if (mMinToolsRevision == null) {
      if (other.getMinToolsRevision() != null) {
        return false;
      }
    }
    else if (!mMinToolsRevision.equals(other.getMinToolsRevision())) {
      return false;
    }
    return true;
  }
}
