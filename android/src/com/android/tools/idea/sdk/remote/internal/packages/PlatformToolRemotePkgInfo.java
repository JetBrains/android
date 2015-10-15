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
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.AdbWrapper;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;

/**
 * Represents a platform-tool XML node in an SDK repository.
 */
public class PlatformToolRemotePkgInfo extends RemotePkgInfo {

  /**
   * The value returned by {@link PlatformToolRemotePkgInfo#installId()}.
   */
  public static final String INSTALL_ID = "platform-tools";                       //$NON-NLS-1$
  /**
   * The value returned by {@link PlatformToolRemotePkgInfo#installId()}.
   */
  public static final String INSTALL_ID_PREVIEW = "platform-tools-preview";       //$NON-NLS-1$

  /**
   * Creates a new platform-tool package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public PlatformToolRemotePkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newPlatformTool(getRevision());
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For platform-tools, we use "platform-tools" or "platform-tools-preview" since
   * this package type is unique.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    if (getRevision().isPreview()) {
      return INSTALL_ID_PREVIEW;
    }
    else {
      return INSTALL_ID;
    }
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   * {@inheritDoc}
   */
  private static String createListDescription(String listDisplay, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    return String.format("Android SDK Platform-tools%1$s", obsolete ? " (Obsolete)" : "");
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, FullRevision revision, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    return String.format("Android SDK Platform-tools, revision %1$s%2$s", revision.toShortString(), obsolete ? " (Obsolete)" : "");
  }

  /**
   * Computes a potential installation folder if an archive of this package were
   * to be installed right away in the given SDK root.
   * <p/>
   * A "platform-tool" package should always be located in SDK/platform-tools.
   * There can be only one installed at once.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
    return new File(osSdkRoot, SdkConstants.FD_PLATFORM_TOOLS);
  }

  /**
   * Hook called right before an archive is installed.
   * This is used here to stop ADB before trying to replace the platform-tool package.
   *
   * @param archive       The archive that will be installed
   * @param monitor       The {@link ITaskMonitor} to display errors.
   * @param osSdkRoot     The OS path of the SDK root folder.
   * @param installFolder The folder where the archive will be installed. Note that this
   *                      is <em>not</em> the folder where the archive was temporary
   *                      unzipped. The installFolder, if it exists, contains the old
   *                      archive that will soon be replaced by the new one.
   * @return True if installing this archive shall continue, false if it should be skipped.
   */
  @Override
  public boolean preInstallHook(Archive archive, ITaskMonitor monitor, String osSdkRoot, File installFolder) {
    AdbWrapper aw = new AdbWrapper(osSdkRoot, monitor);
    aw.stopAdb();
    return super.preInstallHook(archive, monitor, osSdkRoot, installFolder);
  }

}
