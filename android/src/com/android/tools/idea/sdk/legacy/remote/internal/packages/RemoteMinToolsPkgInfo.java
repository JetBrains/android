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

package com.android.tools.idea.sdk.legacy.remote.internal.packages;

import com.android.repository.Revision;
import com.android.tools.idea.sdk.legacy.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSource;
import org.w3c.dom.Node;

import java.util.Map;

/**
 * Represents an XML node in an SDK repository that has a min-tools-rev requirement.
 */
public abstract class RemoteMinToolsPkgInfo extends RemotePkgInfo implements IMinToolsDependency {

    private final MinToolsMixin mMinToolsMixin;

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
    RemoteMinToolsPkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
        super(source, packageNode, nsUri, licenses);

        mMinToolsMixin = new MinToolsMixin(packageNode);
    }

    /**
     * The minimal revision of the tools package required by this extra package, if > 0,
     * or {@link #MIN_TOOLS_REV_NOT_SPECIFIED} if there is no such requirement.
     */
    @Override
    public Revision getMinToolsRevision() {
        return mMinToolsMixin.getMinToolsRevision();
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
        if (!(obj instanceof RemoteMinToolsPkgInfo)) {
            return false;
        }
        return mMinToolsMixin.equals(obj);
    }
}
