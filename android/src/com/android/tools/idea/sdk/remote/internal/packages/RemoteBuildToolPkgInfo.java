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
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.PreciseRevision;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.local.LocalBuildToolPkgInfo;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;

/**
 * Represents a build-tool XML node in an SDK repository.
 */
public class RemoteBuildToolPkgInfo extends RemotePkgInfo {

  /**
   * The base value returned by {@link RemoteBuildToolPkgInfo#installId()}.
   */
  private static final String INSTALL_ID_BASE = SdkConstants.FD_BUILD_TOOLS + '-';

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
  public RemoteBuildToolPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);
    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newBuildTool(getRevision());
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For build-tools, we use "build-tools-" followed by the full revision string
   * where spaces are changed to underscore to be more script-friendly.
   * <p/>
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String installId() {
    return getPkgDesc().getInstallId();
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   */
  private static String createListDescription(String listDisplay, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    return String.format("Android SDK Build-tools%1$s", obsolete ? " (Obsolete)" : "");
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, FullRevision revision, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    return String.format("Android SDK Build-tools, revision %1$s%2$s", revision.toShortString(), obsolete ? " (Obsolete)" : "");
  }

  /**
   * Computes a potential installation folder if an archive of this package were
   * to be installed right away in the given SDK root.
   * <p/>
   * A build-tool package is typically installed in SDK/build-tools/revision.
   * Revision spaces are replaced by underscores for ease of use in command-line.
   * Preview versions will have -preview appended. The RC number is not included.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @NotNull
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
    File folder = new File(osSdkRoot, SdkConstants.FD_BUILD_TOOLS);
    StringBuilder sb = new StringBuilder();

    PreciseRevision revision = getPkgDesc().getPreciseRevision();
    int[] version = revision.toIntArray(false);
    for (int i = 0; i < version.length; i++) {
      sb.append(version[i]);
      if (i != version.length - 1) {
        sb.append('.');
      }
    }
    if (getPkgDesc().getPreciseRevision().isPreview()) {
      sb.append(PkgDesc.PREVIEW_SUFFIX);
    }

    folder = new File(folder, sb.toString());
    return folder;
  }

  @Override
  public boolean sameItemAs(LocalPkgInfo pkg, PreviewComparison comparePreview) {
    // Contrary to other package types, build-tools do not "update themselves"
    // so 2 build tools with 2 different revisions are not the same item.
    if (pkg instanceof LocalBuildToolPkgInfo) {
      LocalBuildToolPkgInfo rhs = (LocalBuildToolPkgInfo)pkg;
      return rhs.getDesc().getFullRevision().compareTo(getRevision(), comparePreview) == 0;
    }
    return false;
  }
}
