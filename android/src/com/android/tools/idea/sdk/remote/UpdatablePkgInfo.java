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
package com.android.tools.idea.sdk.remote;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a (revisionless) package, either local, remote, or both. If both a local and remote package are specified,
 * they should represent exactly the same package, excepting the revision. That is, the result of installing the remote package
 * should be (a possibly updated version of) the local package.
 */
public class UpdatablePkgInfo implements Comparable<UpdatablePkgInfo> {
  private LocalPkgInfo myLocalInfo;
  private RemotePkgInfo myRemoteInfo;
  private RemotePkgInfo myRemotePreviewInfo;

  private final Logger LOG = Logger.getInstance(getClass());

  public UpdatablePkgInfo(@NonNull LocalPkgInfo localInfo) {
    init(localInfo, null);
  }

  public UpdatablePkgInfo(@NonNull RemotePkgInfo remoteInfo) {
    init(null, remoteInfo);
  }

  public UpdatablePkgInfo(@NonNull LocalPkgInfo localInfo, @NonNull RemotePkgInfo remoteInfo) {
    init(localInfo, remoteInfo);
  }

  private void init(@Nullable LocalPkgInfo localPkg, @Nullable RemotePkgInfo remotePkg) {
    assert localPkg != null || remotePkg != null;
    myLocalInfo = localPkg;
    if (remotePkg != null) {
      addRemote(remotePkg);
    }
  }

  /**
   * Adds the given remote package if this package doesn't already have a remote, or if the given remote is more recent.
   * If it is a preview, it will be returned by {@link #getRemote()} only if it is specified that preview packages are desired.
   *
   * @param remote The remote package.
   */
  public void addRemote(@NonNull RemotePkgInfo remote) {
    checkInstallId(remote);
    if (remote.getPkgDesc().isPreview()) {
      if (myRemotePreviewInfo == null ||
          remote.getPkgDesc().isUpdateFor(myRemotePreviewInfo.getPkgDesc(), FullRevision.PreviewComparison.IGNORE)) {
        myRemotePreviewInfo = remote;
      }
    }
    else {
      if (myRemoteInfo == null || remote.getPkgDesc().isUpdateFor(myRemoteInfo.getPkgDesc(), FullRevision.PreviewComparison.IGNORE)) {
        myRemoteInfo = remote;
      }
    }
  }

  private void checkInstallId(RemotePkgInfo remote) {
    if (myLocalInfo == null) {
      return;
    }
    String localIid = myLocalInfo.getDesc().getBaseInstallId();
    String remoteIid = remote.getPkgDesc().getBaseInstallId();
    if (remoteIid.equals(localIid)) {
      return;
    }
    assert false : String.format("%s doesn't match %s", remoteIid, localIid);
  }

  @Nullable
  public LocalPkgInfo getLocalInfo() {
    return myLocalInfo;
  }

  public RemotePkgInfo getRemote(boolean includePreview) {
    // If includePreview is true, and we don't have a non-preview remote or the preview is newer than
    // the non-preview, return the preview.
    if (includePreview &&
        (!hasRemote(false) ||
         (hasPreview() &&
          myRemotePreviewInfo.getPkgDesc().isUpdateFor(myRemoteInfo.getPkgDesc(), FullRevision.PreviewComparison.IGNORE)))) {
      return myRemotePreviewInfo;
    }
    // Else return the non-preview, possibly null.
    return myRemoteInfo;
  }

  public boolean hasPreview() {
    return myRemotePreviewInfo != null;
  }

  public boolean hasRemote(boolean includePreview) {
    return myRemoteInfo != null || (includePreview && myRemotePreviewInfo != null);
  }

  public boolean hasLocal() {
    return myLocalInfo != null;
  }

  @Override
  public int compareTo(UpdatablePkgInfo o) {
    return getPkgDesc(true).compareTo(o.getPkgDesc(true));
  }

  /**
   * Gets a IPkgDesc corresponding to this updatable package. This will be:
   * - The local pkg desc if the package is installed
   * - The remote preview package if there is a remote preview and includePreview is true
   * - The remote package otherwise, or null if there is no non-preview remote.
   * @param includePreview
   */
  public IPkgDesc getPkgDesc(boolean includePreview) {
    if (hasLocal()) {
      return myLocalInfo.getDesc();
    }
    if (includePreview && hasPreview()) {
      return myRemotePreviewInfo.getPkgDesc();
    }
    if (hasRemote(false)) {
      return getRemote(false).getPkgDesc();
    }
    return null;
  }

  public boolean isUpdate(boolean includePreview) {
    RemotePkgInfo remote = getRemote(includePreview);
    return myLocalInfo != null && remote != null &&
           remote.getPkgDesc().getPreciseRevision().compareTo(myLocalInfo.getDesc().getPreciseRevision()) > 0;
  }

  public List<RemotePkgInfo> getAllRemotes() {
    List<RemotePkgInfo> result = new SmartList<RemotePkgInfo>();
    if (myRemoteInfo != null) {
      result.add(myRemoteInfo);
    }
    if (myRemotePreviewInfo != null) {
      result.add(myRemotePreviewInfo);
    }
    return result;
  }
}
