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
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import java.util.Set;


/**
 * Helper methods to compute updates available for local packages.
 */
public abstract class Update {

    public static UpdateResult computeUpdates(@NonNull LocalPkgInfo[] localPkgs,
                                        @NonNull Multimap<PkgType, RemotePkgInfo> remotePkgs) {

      UpdateResult result = new UpdateResult();
        Set<RemotePkgInfo> updates = Sets.newTreeSet();

        // Find updates to locally installed packages
        for (LocalPkgInfo local : localPkgs) {
            RemotePkgInfo update = findUpdate(local, remotePkgs, result);
            if (update != null) {
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
            for (LocalPkgInfo local : localPkgs) {
                IPkgDesc localDesc = local.getDesc();
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

    private static RemotePkgInfo findUpdate(@NonNull LocalPkgInfo local,
                                            @NonNull Multimap<PkgType, RemotePkgInfo> remotePkgs,
                                            @NonNull UpdateResult result) {
        RemotePkgInfo currUpdatePkg = null;
        IPkgDesc currUpdateDesc = null;
        IPkgDesc localDesc = local.getDesc();

        for (RemotePkgInfo remote: remotePkgs.get(localDesc.getType())) {
            IPkgDesc remoteDesc = remote.getDesc();
            if ((currUpdateDesc == null && remoteDesc.isUpdateFor(localDesc)) ||
                    (currUpdateDesc != null && remoteDesc.isUpdateFor(currUpdateDesc))) {
                currUpdatePkg = remote;
                currUpdateDesc = remoteDesc;
            }
        }

        local.setUpdate(currUpdatePkg);
        if (currUpdatePkg != null) {
            result.addUpdatedPkgs(local);
        }

        return currUpdatePkg;
    }

}
