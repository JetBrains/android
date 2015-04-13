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
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Represents a build-tool XML node in an SDK repository.
 */
public class BuildToolPackage extends FullRevisionPackage {

  /**
   * The base value returned by {@link BuildToolPackage#installId()}.
   */
  private static final String INSTALL_ID_BASE = SdkConstants.FD_BUILD_TOOLS + '-';

  private final IPkgDesc mPkgDesc;

  /**
   * Creates a new build-tool package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public BuildToolPackage(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    mPkgDesc = setDescriptions(PkgDesc.Builder.newBuildTool(getRevision())).create();
  }

  @Override
  @NonNull
  public IPkgDesc getPkgDesc() {
    return mPkgDesc;
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For build-tools, we use "build-tools-" followed by the full revision string
   * where spaces are changed to underscore to be more script-friendly.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    return INSTALL_ID_BASE + getRevision().toString().replace(' ', '_');
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

    return String.format("Android SDK Build-tools%1$s", isObsolete() ? " (Obsolete)" : "");
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

    return String.format("Android SDK Build-tools, revision %1$s%2$s", getRevision().toShortString(), isObsolete() ? " (Obsolete)" : "");
  }

  /**
   * Returns a long description for an {@link IDescription}.
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
   * A build-tool package is typically installed in SDK/build-tools/revision.
   * Revision spaces are replaced by underscores for ease of use in command-line.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
    File folder = new File(osSdkRoot, SdkConstants.FD_BUILD_TOOLS);
    folder = new File(folder, getRevision().toString().replace(' ', '_'));
    return folder;
  }

  /**
   * Check whether 2 platform-tool packages are the same <em>and</em> have the
   * same preview bit.
   */
  @Override
  public boolean sameItemAs(Package pkg) {
    // Implementation note: here we don't want to care about the preview number
    // so we ignore the preview when calling sameItemAs(); however we do care
    // about both packages being either previews or not previews (i.e. the type
    // must match but the preview number doesn't need to.)
    // The end result is that a package such as "1.2 rc 4" will be an update for "1.2 rc 3".
    return sameItemAs(pkg, PreviewComparison.COMPARE_TYPE);
  }

  @Override
  public boolean sameItemAs(Package pkg, PreviewComparison comparePreview) {
    // Contrary to other package types, build-tools do not "update themselves"
    // so 2 build tools with 2 different revisions are not the same item.
    if (pkg instanceof BuildToolPackage) {
      BuildToolPackage rhs = (BuildToolPackage)pkg;
      return rhs.getRevision().compareTo(getRevision(), comparePreview) == 0;
    }
    return false;
  }

  /**
   * For build-tool package use their revision number like version numbers and
   * we want them sorted from higher to lower. To do that, insert a fake revision
   * number using 9999-value into the sorting key.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  protected String comparisonKey() {
    String s = super.comparisonKey();
    int pos = s.indexOf("|r:");         //$NON-NLS-1$
    assert pos > 0;

    FullRevision rev = getRevision();
    String reverseSort = String.format("|rr:%1$04d.%2$04d.%3$04d.",         //$NON-NLS-1$
                                       9999 - rev.getMajor(), 9999 - rev.getMinor(), 9999 - rev.getMicro());

    s = s.substring(0, pos) + reverseSort + s.substring(pos);
    return s;
  }

}
