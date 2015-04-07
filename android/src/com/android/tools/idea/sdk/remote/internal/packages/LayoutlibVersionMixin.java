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

import com.android.tools.idea.sdk.remote.internal.packages.ILayoutlibVersion;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.RepoConstants;
import com.android.utils.Pair;

import org.w3c.dom.Node;

import java.util.Properties;

/**
 * Helper class to handle the layoutlib version provided by a package.
 */
public class LayoutlibVersionMixin implements ILayoutlibVersion {

    /**
     * The layoutlib version.
     * The first integer is the API of layoublib, which should be > 0.
     * It will be equal to {@link #LAYOUTLIB_API_NOT_SPECIFIED} (0) if the layoutlib
     * version isn't specified.
     * The second integer is the revision for that given API. It is >= 0
     * and works as a minor revision number, incremented for the same API level.
     */
    private final Pair<Integer, Integer> mLayoutlibVersion;

    /**
     * Parses an XML node to process the {@code <layoutlib>} element.
     *
     * The layoutlib element is new in the XSD rev 4, so we need to cope with it missing
     * in earlier XMLs.
     */
    public LayoutlibVersionMixin(Node pkgNode) {

        int api = LAYOUTLIB_API_NOT_SPECIFIED;
        int rev = LAYOUTLIB_REV_NOT_SPECIFIED;

        Node layoutlibNode =
            PackageParserUtils.findChildElement(pkgNode, RepoConstants.NODE_LAYOUT_LIB);

        if (layoutlibNode != null) {
            api = PackageParserUtils.getXmlInt(layoutlibNode, RepoConstants.NODE_API, 0);
            rev = PackageParserUtils.getXmlInt(layoutlibNode, RepoConstants.NODE_REVISION, 0);
        }

        mLayoutlibVersion = Pair.of(api, rev);
    }

    /**
     * Parses the layoutlib version optionally available in the given {@link Properties}.
     */
    public LayoutlibVersionMixin(Properties props) {
        int layoutlibApi = Package.getPropertyInt(props, PkgProps.LAYOUTLIB_API,
                                                         LAYOUTLIB_API_NOT_SPECIFIED);
        int layoutlibRev = Package.getPropertyInt(props, PkgProps.LAYOUTLIB_REV,
                                                         LAYOUTLIB_REV_NOT_SPECIFIED);
        mLayoutlibVersion = Pair.of(layoutlibApi, layoutlibRev);
    }

    /**
     * Stores the layoutlib version in the given {@link Properties}.
     */
    void saveProperties(Properties props) {
        if (mLayoutlibVersion.getFirst().intValue() != LAYOUTLIB_API_NOT_SPECIFIED) {
            props.setProperty(PkgProps.LAYOUTLIB_API, mLayoutlibVersion.getFirst().toString());
            props.setProperty(PkgProps.LAYOUTLIB_REV, mLayoutlibVersion.getSecond().toString());
        }
    }

    /**
     * Returns the layoutlib version.
     * <p/>
     * The first integer is the API of layoublib, which should be > 0.
     * It will be equal to {@link #LAYOUTLIB_API_NOT_SPECIFIED} (0) if the layoutlib
     * version isn't specified.
     * <p/>
     * The second integer is the revision for that given API. It is >= 0
     * and works as a minor revision number, incremented for the same API level.
     *
     * @since sdk-repository-4.xsd and sdk-addon-2.xsd
     */
    @Override
    public Pair<Integer, Integer> getLayoutlibVersion() {
        return mLayoutlibVersion;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mLayoutlibVersion == null) ? 0 : mLayoutlibVersion.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof com.android.tools.idea.sdk.remote.internal.packages.LayoutlibVersionMixin)) {
            return false;
        }
        com.android.tools.idea.sdk.remote.internal.packages.LayoutlibVersionMixin
          other = (com.android.tools.idea.sdk.remote.internal.packages.LayoutlibVersionMixin) obj;
        if (mLayoutlibVersion == null) {
            if (other.mLayoutlibVersion != null) {
                return false;
            }
        } else if (!mLayoutlibVersion.equals(other.mLayoutlibVersion)) {
            return false;
        }
        return true;
    }
}
