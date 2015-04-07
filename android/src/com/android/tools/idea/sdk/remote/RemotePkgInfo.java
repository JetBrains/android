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
import com.android.sdklib.repository.IDescription;
import com.android.sdklib.repository.IListDescription;
import com.android.sdklib.repository.descriptors.IPkgDesc;


/**
 * This class provides information on a remote package available for download
 * via a remote SDK repository server.
 */
public class RemotePkgInfo
        implements IDescription, IListDescription, Comparable<RemotePkgInfo> {

    /** Information on the package provided by the remote server. */
    @NonNull
    private final IPkgDesc mPkgDesc;

    /** Source identifier of the package. */
    @NonNull
    private final IDescription mSourceUri;

    /** Size of the archives that make up this package */
    private final long mDownloadSize;

    public RemotePkgInfo(@NonNull IPkgDesc pkgDesc, @NonNull IDescription sourceUri, long downloadSize) {
        mPkgDesc = pkgDesc;
        mSourceUri = sourceUri;
        mDownloadSize = downloadSize;
    }

    /** Information on the package provided by the remote server. */
    @NonNull
    public IPkgDesc getDesc() {
        return mPkgDesc;
    }

    /**
     * Returns the source identifier of the remote package.
     * This is an opaque object that can return its own description.
     */
    @NonNull
    public IDescription getSourceUri() {
        return mSourceUri;
    }

    /**
     * Returns the size (in bytes) of all the archives that make up this package.
     */
    public long getDownloadSize() {
        return mDownloadSize;
    }

    //---- Ordering ----

    /**
     * Compares 2 packages by comparing their {@link IPkgDesc}.
     * The source is not used in the comparison.
     */
    @Override
    public int compareTo(@NonNull RemotePkgInfo o) {
        return mPkgDesc.compareTo(o.mPkgDesc);
    }

    /**
     * The remote package hash code is based on the underlying {@link IPkgDesc}.
     * The source is not used in the hash code.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mPkgDesc == null) ? 0 : mPkgDesc.hashCode());
        return result;
    }

    /**
     * Compares 2 packages by comparing their {@link IPkgDesc}.
     * The source is not used in the comparison.
     */
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof RemotePkgInfo) && this.compareTo((RemotePkgInfo) obj) == 0;
    }

    /** String representation for debugging purposes. */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("<RemotePkgInfo Source:").append(mSourceUri.getShortDescription());
        builder.append(' ').append(mPkgDesc.toString()).append('>');
        return builder.toString();
    }

    @Override
    public String getListDescription() {
        return getDesc().getListDescription();
    }

    @Override
    public String getShortDescription() {
        // TODO revisit to differentiate from list-description depending
        // on how we'll use it in the sdkman UI.
        return getListDescription();
    }

    @Override
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder();
        IPkgDesc desc = getDesc();

        sb.append(desc.getListDescription()).append('\n');

        if (desc.hasVendor()) {
            assert desc.getVendor() != null;
            sb.append("By ").append(desc.getVendor().getDisplay()).append('\n');
        }

        if (desc.hasMinPlatformToolsRev()) {
            assert desc.getMinPlatformToolsRev() != null;
            sb.append("Requires Platform-Tools revision ").append(desc.getMinPlatformToolsRev().toShortString()).append('\n');
        }

        if (desc.hasMinToolsRev()) {
            assert desc.getMinToolsRev() != null;
            sb.append("Requires Tools revision ").append(desc.getMinToolsRev().toShortString()).append('\n');
        }

        return sb.toString();
    }
}
