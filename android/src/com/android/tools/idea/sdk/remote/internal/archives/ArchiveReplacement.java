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

package com.android.tools.idea.sdk.remote.internal.archives;

import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.sdklib.repository.IDescription;
import com.android.tools.idea.sdk.remote.internal.packages.Package;


/**
 * Represents an archive that we want to install and the archive that it is
 * going to replace, if any.
 */
public class ArchiveReplacement implements IDescription {

    private final Archive mNewArchive;
    private final Archive mReplaced;

    /**
     * Creates a new replacement where the {@code newArchive} will replace the
     * currently installed {@code replaced} archive.
     * When {@code newArchive} is not intended to replace anything (e.g. because
     * the user is installing a new package not present on her system yet), then
     * {@code replace} shall be null.
     *
     * @param newArchive A "new archive" to be installed. This is always an archive
     *          that comes from a remote site. This <em>may</em> be null.
     * @param replaced An optional local archive that the new one will replace.
     *          Can be null if this archive does not replace anything.
     */
    public ArchiveReplacement(Archive newArchive, Archive replaced) {
        mNewArchive = newArchive;
        mReplaced = replaced;
    }

    /**
     * Returns the "new archive" to be installed.
     * This <em>may</em> be null for missing archives.
     */
    public Archive getNewArchive() {
        return mNewArchive;
    }

    /**
     * Returns an optional local archive that the new one will replace.
     * Can be null if this archive does not replace anything.
     */
    public Archive getReplaced() {
        return mReplaced;
    }

    /**
     * Returns the long description of the parent package of the new archive, if not null.
     * Otherwise returns an empty string.
     */
    @Override
    public String getLongDescription() {
        if (mNewArchive != null) {
            Package p = mNewArchive.getParentPackage();
            if (p != null) {
                return p.getLongDescription();
            }
        }
        return "";
    }

    /**
     * Returns the short description of the parent package of the new archive, if not null.
     * Otherwise returns an empty string.
     */
    @Override
    public String getShortDescription() {
        if (mNewArchive != null) {
            Package p = mNewArchive.getParentPackage();
            if (p != null) {
                return p.getShortDescription();
            }
        }
        return "";
    }

    /**
     * Returns the short description of the parent package of the new archive, if not null.
     * Otherwise returns the default Object toString result.
     * <p/>
     * This is mostly helpful for debugging. For UI display, use the {@link IDescription}
     * interface.
     */
    @Override
    public String toString() {
        if (mNewArchive != null) {
            Package p = mNewArchive.getParentPackage();
            if (p != null) {
                return p.getShortDescription();
            }
        }
        return super.toString();
    }
}
