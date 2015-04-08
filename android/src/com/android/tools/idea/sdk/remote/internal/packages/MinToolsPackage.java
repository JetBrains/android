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

import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.Map;
import java.util.Properties;

/**
 * Represents an XML node in an SDK repository that has a min-tools-rev requirement.
 */
public abstract class MinToolsPackage extends MajorRevisionPackage implements IMinToolsDependency {

    private final com.android.tools.idea.sdk.remote.internal.packages.MinToolsMixin mMinToolsMixin;

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
    MinToolsPackage(SdkSource source, Node packageNode, String nsUri, Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mMinToolsMixin = new com.android.tools.idea.sdk.remote.internal.packages.MinToolsMixin(packageNode);
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
    public MinToolsPackage(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            String archiveOsPath) {
        super(source, props, revision, license, description, descUrl, archiveOsPath);

        mMinToolsMixin = new com.android.tools.idea.sdk.remote.internal.packages.MinToolsMixin(
                source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOsPath);
    }

    /**
     * The minimal revision of the tools package required by this extra package, if > 0,
     * or {@link #MIN_TOOLS_REV_NOT_SPECIFIED} if there is no such requirement.
     */
    @Override
    public FullRevision getMinToolsRevision() {
        return mMinToolsMixin.getMinToolsRevision();
    }

    @Override
    public void saveProperties(Properties props) {
        super.saveProperties(props);
        mMinToolsMixin.saveProperties(props);
    }

    @Override
    public int hashCode() {
        return mMinToolsMixin.hashCode(super.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof com.android.tools.idea.sdk.remote.internal.packages.MinToolsPackage)) {
            return false;
        }
        return mMinToolsMixin.equals(obj);
    }
}
