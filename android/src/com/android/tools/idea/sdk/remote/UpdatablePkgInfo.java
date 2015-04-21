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
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.local.LocalPkgInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a (revisionless) package, either local, remote, or both. If both a local and remote package are specified,
 * they should represent exactly the same package, excepting the revision. That is, the result of installing the remote package
 * should be (a possibly updated version of) the local package.
 */
public class UpdatablePkgInfo implements Comparable<UpdatablePkgInfo> {
  private LocalPkgInfo myLocalInfo;
  private RemotePkgInfo myRemoteInfo;

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
    myRemoteInfo = remotePkg;
  }

  public void setRemote(@NonNull RemotePkgInfo remote) {
    assert myRemoteInfo == null;
    myRemoteInfo = remote;
  }

  @Nullable
  public LocalPkgInfo getLocalInfo() {
    return myLocalInfo;
  }

  @Nullable
  public RemotePkgInfo getRemote() {
    return myRemoteInfo;
  }

  public boolean hasRemote() {
    return myRemoteInfo != null;
  }

  public boolean hasLocal() {
    return myLocalInfo != null;
  }

  @Override
  public int compareTo(UpdatablePkgInfo o) {
    return getPkgDesc().compareTo(o.getPkgDesc());
  }

  public IPkgDesc getPkgDesc() {
    return myLocalInfo == null ? myRemoteInfo.getPkgDesc() : myLocalInfo.getDesc();
  }

  public boolean isUpdate() {
    return myLocalInfo != null && myRemoteInfo != null &&
           myRemoteInfo.getPkgDesc().getPreciseRevision().compareTo(myLocalInfo.getDesc().getPreciseRevision()) > 0;
  }
}
