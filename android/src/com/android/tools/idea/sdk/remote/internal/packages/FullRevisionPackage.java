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
import com.android.tools.idea.sdk.remote.internal.packages.PackageParserUtils;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkRepoConstants;

import org.w3c.dom.Node;

import java.util.Map;
import java.util.Properties;

/**
 * Represents a package in an SDK repository that has a {@link FullRevision},
 * which is a multi-part revision number (major.minor.micro) and an optional preview revision.
 */
public abstract class FullRevisionPackage extends Package
        implements IFullRevisionProvider {

    private final FullRevision mPreviewVersion;

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
    FullRevisionPackage(SdkSource source,
            Node packageNode,
            String nsUri,
            Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mPreviewVersion = PackageParserUtils.parseFullRevisionElement(
                PackageParserUtils.findChildElement(packageNode, SdkRepoConstants.NODE_REVISION));
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
    public FullRevisionPackage(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            String archiveOsPath) {
        super(source, props, revision, license, description, descUrl, archiveOsPath);

        FullRevision rev = PackageParserUtils.getPropertyFull(props, PkgProps.PKG_REVISION);
        if (rev == null) {
            rev = new FullRevision(revision);
        }
        mPreviewVersion = rev;
    }

    @Override
    public FullRevision getRevision() {
        return mPreviewVersion;
    }

    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);
        props.setProperty(PkgProps.PKG_REVISION, mPreviewVersion.toShortString());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((mPreviewVersion == null) ? 0 : mPreviewVersion.hashCode());
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
        if (!(obj instanceof com.android.tools.idea.sdk.remote.internal.packages.FullRevisionPackage)) {
            return false;
        }
        com.android.tools.idea.sdk.remote.internal.packages.FullRevisionPackage
          other = (com.android.tools.idea.sdk.remote.internal.packages.FullRevisionPackage) obj;
        if (mPreviewVersion == null) {
            if (other.mPreviewVersion != null) {
                return false;
            }
        } else if (!mPreviewVersion.equals(other.mPreviewVersion)) {
            return false;
        }
        return true;
    }

    /**
     * Computes whether the given package is a suitable update for the current package.
     * <p/>
     * A specific case here is that a release package can update a preview, whereas
     * a preview can only update another preview.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public UpdateInfo canBeUpdatedBy(Package replacementPackage) {
        if (replacementPackage == null) {
            return UpdateInfo.INCOMPATIBLE;
        }

        // check they are the same item, ignoring the preview bit.
        if (!sameItemAs(replacementPackage, PreviewComparison.IGNORE)) {
            return UpdateInfo.INCOMPATIBLE;
        }

        // a preview cannot update a non-preview
        if (!getRevision().isPreview() && replacementPackage.getRevision().isPreview()) {
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
