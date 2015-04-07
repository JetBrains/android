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

import com.android.tools.idea.sdk.remote.internal.packages.*;
import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkRepoConstants;

import org.w3c.dom.Node;

import java.util.Map;
import java.util.Properties;

/**
 * Represents a package in an SDK repository that has a {@link MajorRevision},
 * which is a single major revision number (not minor, micro or previews).
 */
public abstract class MajorRevisionPackage extends Package {

    private final MajorRevision mRevision;

    /**
     * Creates a new package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    MajorRevisionPackage(SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mRevision = new MajorRevision(
                PackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_REVISION, 0));
    }

    /**
     * Manually create a new package with one archive and the given attributes.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * Properties from props are used first when possible, e.g. if props is non null.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    public MajorRevisionPackage(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            String archiveOsPath) {
        super(source, props, revision, license, description, descUrl, archiveOsPath);

        String revStr = getProperty(props, PkgProps.PKG_REVISION, null);

        MajorRevision rev = null;
        if (revStr != null) {
            try {
                rev = MajorRevision.parseRevision(revStr);
            } catch (NumberFormatException ignore) {}
        }
        if (rev == null) {
            rev = new MajorRevision(revision);
        }

        mRevision = rev;
    }

    /**
     * Returns the revision, an int > 0, for all packages (platform, add-on, tool, doc).
     * Can be 0 if this is a local package of unknown revision.
     */
    @Override
    public FullRevision getRevision() {
        return mRevision;
    }


    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);
        props.setProperty(PkgProps.PKG_REVISION, mRevision.toString());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mRevision == null) ? 0 : mRevision.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof com.android.tools.idea.sdk.remote.internal.packages.MajorRevisionPackage)) {
            return false;
        }
        com.android.tools.idea.sdk.remote.internal.packages.MajorRevisionPackage
          other = (com.android.tools.idea.sdk.remote.internal.packages.MajorRevisionPackage) obj;
        if (mRevision == null) {
            if (other.mRevision != null) {
                return false;
            }
        } else if (!mRevision.equals(other.mRevision)) {
            return false;
        }
        return true;
    }

    @Override
    public UpdateInfo canBeUpdatedBy(Package replacementPackage) {
        if (replacementPackage == null) {
            return UpdateInfo.INCOMPATIBLE;
        }

        // check they are the same item.
        if (!sameItemAs(replacementPackage)) {
            return UpdateInfo.INCOMPATIBLE;
        }

        // check revision number
        if (replacementPackage.getRevision().compareTo(this.getRevision()) > 0) {
            return UpdateInfo.UPDATE;
        }

        // not an upgrade but not incompatible either.
        return UpdateInfo.NOT_UPDATE;
    }


}
