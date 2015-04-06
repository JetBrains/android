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
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.Set;


/**
 * Helper methods to compute updates available for local packages.
 */
public abstract class Update {

  public static UpdateResult computeUpdates(@NonNull LocalPkgInfo[] localPkgs,
                                            @NonNull Multimap<PkgType, RemotePkgInfo> remotePkgs) {
    UpdatablePkgInfo[] updatablePkgInfos = new UpdatablePkgInfo[localPkgs.length];
    for (int i = 0; i < localPkgs.length; i++) {
      updatablePkgInfos[i] = new UpdatablePkgInfo(localPkgs[i], null);
    }
    UpdateResult result = new UpdateResult();
    Set<RemotePkgInfo> updates = Sets.newTreeSet();

    // Find updates to locally installed packages
    for (UpdatablePkgInfo info : updatablePkgInfos) {
      RemotePkgInfo update = findUpdate(info, remotePkgs, result);
      if (update != null) {
        info.setUpdate(update);
        updates.add(update);
      }
    }

    // Find new packages not yet installed
    nextRemote: for (RemotePkgInfo remote : remotePkgs.values()) {
      if (updates.contains(remote)) {
        // if package is already a known update, it's not new.
        continue nextRemote;
      }
      IPkgDesc remoteDesc = remote.getDesc();
      for (UpdatablePkgInfo info : updatablePkgInfos) {
        IPkgDesc localDesc = info.getLocalInfo().getDesc();
        if (remoteDesc.compareTo(localDesc) == 0 || remoteDesc.isUpdateFor(localDesc)) {
          // if package is same as an installed or is an update for an installed
          // one, then it's not new.
          continue nextRemote;
        }
      }

      result.addNewPkgs(remote);
    }

    return result;
  }

  private static RemotePkgInfo findUpdate(@NonNull UpdatablePkgInfo info,
                                          @NonNull Multimap<PkgType, RemotePkgInfo> remotePkgs,
                                          @NonNull UpdateResult result) {
    RemotePkgInfo currUpdatePkg = null;
    IPkgDesc currUpdateDesc = null;
    IPkgDesc localDesc = info.getLocalInfo().getDesc();

    for (RemotePkgInfo remote: remotePkgs.get(localDesc.getType())) {
      IPkgDesc remoteDesc = remote.getDesc();
      if ((currUpdateDesc == null && remoteDesc.isUpdateFor(localDesc)) ||
          (currUpdateDesc != null && remoteDesc.isUpdateFor(currUpdateDesc))) {
        currUpdatePkg = remote;
        currUpdateDesc = remoteDesc;
      }
    }

    if (currUpdatePkg != null) {
      result.addUpdatedPkgs(info);
    }

    return currUpdatePkg;
  }

}
