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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.sdk.remote.internal.packages.BuildToolPackage;
import com.android.tools.idea.sdk.remote.internal.packages.IExactApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.IMinApiLevelDependency;
import com.android.tools.idea.sdk.remote.internal.packages.MajorRevisionPackage;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.sdklib.repository.IDescription;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;

import java.io.File;
import java.util.Properties;

/**
 * Represents an SDK repository package that is incomplete.
 * It has a distinct icon and a specific error that is supposed to help the user on how to fix it.
 */
public class BrokenPackage extends MajorRevisionPackage
        implements IExactApiLevelDependency, IMinApiLevelDependency {

    /**
     * The minimal API level required by this package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    private final int mMinApiLevel;

    /**
     * The exact API level required by this package, if > 0,
     * or {@link #API_LEVEL_INVALID} if there is no such requirement.
     */
    private final int mExactApiLevel;

    private final String mShortDescription;
    private final String mLongDescription;
    private final IPkgDesc mPkgDesc;

    /**
     * Creates a new "broken" package that represents a package that we failed to load,
     * for whatever error indicated in {@code longDescription}.
     * There is also an <em>optional</em> API level dependency that can be specified.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    BrokenPackage(@Nullable Properties props,
            @NonNull String shortDescription,
            @NonNull String longDescription,
            int minApiLevel,
            int exactApiLevel,
            @Nullable String archiveOsPath,
            @NonNull IPkgDesc pkgDesc) {
        super(  null,                                   //source
                props,                                  //properties
                0,                                      //revision will be taken from props
                null,                                   //license
                longDescription,                        //description
                null,                                   //descUrl
                archiveOsPath                           //archiveOsPath
                );
        mShortDescription = shortDescription;
        mLongDescription = longDescription;
        mMinApiLevel = minApiLevel;
        mExactApiLevel = exactApiLevel;
        mPkgDesc = pkgDesc;
    }

    @Override
    @NonNull
    public IPkgDesc getPkgDesc() {
        return mPkgDesc;
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be given to a constructor that takes a {@link Properties} object.
     * <p/>
     * Base implementation override: We don't actually save properties for a broken package.
     */
    @Override
    public void saveProperties(Properties props) {
        // Nop. We don't actually save properties for a broken package.
    }

    /**
     * Returns the minimal API level required by this package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    @Override
    public int getMinApiLevel() {
        return mMinApiLevel;
    }

    /**
     * Returns the exact API level required by this package, if > 0,
     * or {@link #API_LEVEL_INVALID} if the value was missing.
     */
    @Override
    public int getExactApiLevel() {
        return mExactApiLevel;
    }

    /**
     * Returns a string identifier to install this package from the command line.
     * For broken packages, we return an empty string. These are not installable.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String installId() {
        return "";    //$NON-NLS-1$
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

        return mShortDescription;
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    @Override
    public String getShortDescription() {
        return mShortDescription;
    }

    /**
     * Returns a long description for an {@link IDescription}.
     *
     * The long description uses what was given to the constructor.
     * If it's missing, it will use whatever the XML contains for the &lt;description&gt; field,
     * or the short description if the former is empty.
     */
    @Override
    public String getLongDescription() {

        String s = mLongDescription;
        if (s != null && s.length() != 0) {
            return s;
        }

        s = getDescription();
        if (s != null && s.length() != 0) {
            return s;
        }
        return getShortDescription();
    }

    /**
     * We should not be attempting to install a broken package.
     *
     * {@inheritDoc}
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
        // We should not be attempting to install a broken package.
        return null;
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        if (pkg instanceof com.android.tools.idea.sdk.remote.internal.packages.BrokenPackage) {
            return mShortDescription.equals(((com.android.tools.idea.sdk.remote.internal.packages.BrokenPackage) pkg).mShortDescription) &&
                getDescription().equals(pkg.getDescription()) &&
                getMinApiLevel() == ((com.android.tools.idea.sdk.remote.internal.packages.BrokenPackage) pkg).getMinApiLevel();
        }

        return false;
    }

    @Override
    public boolean preInstallHook(Archive archive,
            ITaskMonitor monitor,
            String osSdkRoot,
            File installFolder) {
        // Nothing specific to do.
        return super.preInstallHook(archive, monitor, osSdkRoot, installFolder);
    }

    /**
     * Computes a hash of the installed content (in case of successful install.)
     *
     * {@inheritDoc}
     */
    @Override
    public void postInstallHook(Archive archive, ITaskMonitor monitor, File installFolder) {
        // Nothing specific to do.
        super.postInstallHook(archive, monitor, installFolder);
    }

    /**
     * Similar to {@link BuildToolPackage#comparisonKey()}, but we need to use
     * {@link #getPkgDesc} instead of {@link #getRevision()}
     */
    @Override
    protected String comparisonKey() {
        String s = super.comparisonKey();
        FullRevision rev = getPkgDesc().getFullRevision();
        if (rev != null) {
            int pos = s.indexOf("|r:");         //$NON-NLS-1$
            assert pos > 0;
            String reverseSort = String.format("|rr:%1$04d.%2$04d.%3$04d.",         //$NON-NLS-1$
                    9999 - rev.getMajor(),
                    9999 - rev.getMinor(),
                    9999 - rev.getMicro());

            s = s.substring(0, pos) + reverseSort + s.substring(pos);
        }
        return s;
    }
}
