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

package com.android.tools.idea.sdk;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.google.common.base.Predicate;
import com.google.common.collect.*;

import java.util.Map;
import java.util.Set;


/**
 * Store of current local and remote packages, in convenient forms.
 */
public final class SdkPackages {
  private final Set<UpdatablePkgInfo> myUpdatedPkgs = Sets.newTreeSet();
  private final Set<RemotePkgInfo> myNewPkgs = Sets.newTreeSet();
  private final long myTimestampMs;
  private Map<String, UpdatablePkgInfo> myConsolidatedPkgs = Maps.newTreeMap();
  private LocalPkgInfo[] myLocalPkgInfos = new LocalPkgInfo[0];
  private Multimap<PkgType, RemotePkgInfo> myRemotePkgInfos = TreeMultimap.create();

  SdkPackages() {
    myTimestampMs = System.currentTimeMillis();
  }

  public SdkPackages(LocalPkgInfo[] localPkgs, Multimap<PkgType, RemotePkgInfo> remotePkgs) {
    this();
    setLocalPkgInfos(localPkgs);
    setRemotePkgInfos(remotePkgs);
  }

  /**
   * Returns the timestamp (in {@link System#currentTimeMillis()} time) when this object was created.
   */
  public long getTimestampMs() {
    return myTimestampMs;
  }

  /**
   * Returns the set of packages that have local updates available.
   * Use {@link LocalPkgInfo#getUpdate()} to retrieve the computed updated candidate.
   *
   * @return A non-null, possibly empty Set of update candidates.
   */
  @NonNull
  public Set<UpdatablePkgInfo> getUpdatedPkgs() {
    return myUpdatedPkgs;
  }

  /**
   * Returns the set of new remote packages that are not locally present
   * and that the user could install.
   *
   * @return A non-null, possibly empty Set of new install candidates.
   */
  @NonNull
  public Set<RemotePkgInfo> getNewPkgs() {
    return myNewPkgs;
  }

  /**
   * Returns a map of package install ids to {@link UpdatablePackageInfo}s representing all known
   * local and remote packages. Remote packages corresponding to local packages will be represented
   * by a single item containing both the local and remote info.
   * {@see IPkgDesc#getInstallId()}
   */
  @NonNull
  public Map<String, UpdatablePkgInfo> getConsolidatedPkgs() {
    return myConsolidatedPkgs;
  }

  @NonNull
  public LocalPkgInfo[] getLocalPkgInfos() {
    return myLocalPkgInfos;
  }

  public Multimap<PkgType, RemotePkgInfo> getRemotePkgInfos() {
    return myRemotePkgInfos;
  }

  void setLocalPkgInfos(LocalPkgInfo[] packages) {
    myLocalPkgInfos = packages;
    computeUpdates();
  }

  void setRemotePkgInfos(Multimap<PkgType, RemotePkgInfo> packages) {
    myRemotePkgInfos = Multimaps.filterValues(packages, new Predicate<RemotePkgInfo>() {
      @Override
      public boolean apply(RemotePkgInfo input) {
        return input.hasCompatibleArchive();
      }
    });
    computeUpdates();
  }

  private void computeUpdates() {
    Map<String, UpdatablePkgInfo> newConsolidatedPkgs = Maps.newTreeMap();
    UpdatablePkgInfo[] updatablePkgInfos = new UpdatablePkgInfo[myLocalPkgInfos.length];
    for (int i = 0; i < myLocalPkgInfos.length; i++) {
      updatablePkgInfos[i] = new UpdatablePkgInfo(myLocalPkgInfos[i]);
    }
    Set<RemotePkgInfo> updates = Sets.newTreeSet();

    // Find updates to locally installed packages
    for (UpdatablePkgInfo info : updatablePkgInfos) {
      IPkgDesc localDesc = info.getLocalInfo().getDesc();
      for (RemotePkgInfo remote : myRemotePkgInfos.get(localDesc.getType())) {
        if (remote.getPkgDesc().isUpdateFor(localDesc, FullRevision.PreviewComparison.IGNORE)) {
          info.addRemote(remote);
          myUpdatedPkgs.add(info);
          updates.add(remote);
        }
      }
      // the consolidated packages map is always keyed by the non-preview installid, whether or not the UpdatablePackage happens to
      // contain a preview package.
      newConsolidatedPkgs.put(info.getPkgDesc(true).getBaseInstallId(), info);
    }

    // Find new packages not yet installed
    nextRemote: for (RemotePkgInfo remote : myRemotePkgInfos.values()) {
      if (updates.contains(remote)) {
        // if package is already a known update, it's not new.
        continue nextRemote;
      }
      IPkgDesc remoteDesc = remote.getPkgDesc();
      for (UpdatablePkgInfo info : updatablePkgInfos) {
        IPkgDesc localDesc = info.getLocalInfo().getDesc();
        if (remoteDesc.compareTo(localDesc) == 0 || remoteDesc.isUpdateFor(localDesc, FullRevision.PreviewComparison.IGNORE) ||
            localDesc.isUpdateFor(remoteDesc, FullRevision.PreviewComparison.IGNORE) /* shouldn't happen in the normal case */) {
          // if package is same as an installed or is an update for an installed
          // one, then it's not new.
          continue nextRemote;
        }
      }

      myNewPkgs.add(remote);
      String key = remoteDesc.getBaseInstallId();
      UpdatablePkgInfo existing = newConsolidatedPkgs.get(key);
      if (existing != null) {
        existing.addRemote(remote);
      }
      else {
        newConsolidatedPkgs.put(key, new UpdatablePkgInfo(remote));
      }
    }
    myConsolidatedPkgs = newConsolidatedPkgs;
  }
}
