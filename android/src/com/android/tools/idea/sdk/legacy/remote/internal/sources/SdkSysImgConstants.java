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

package com.android.tools.idea.sdk.legacy.remote.internal.sources;

import com.android.sdklib.repository.RepoXsdUtil;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;

/**
 * Public constants for the sdk-sys-img XML Schema.
 */
public class SdkSysImgConstants extends RepoConstants {

    /**
     * The default name looked for by SdkSource when trying to load an
     * sdk-sys-img XML if the URL doesn't match an existing resource.
     */
    public static final String URL_DEFAULT_FILENAME = "sys-img.xml";       //$NON-NLS-1$

    /** The base of our sdk-sys-img XML namespace. */
    private static final String NS_BASE =
        "http://schemas.android.com/sdk/android/sys-img/";                 //$NON-NLS-1$

    /**
     * The pattern of our sdk-sys-img XML namespace.
     * Matcher's group(1) is the schema version (integer).
     */
    public static final String NS_PATTERN = NS_BASE + "([0-9]+)";     //$NON-NLS-1$

    /**
     * The latest version of the sdk-sys-img XML Schema.
     * Valid version numbers are between 1 and this number, included.
     */
    public static final int NS_LATEST_VERSION = 3;

    /** The XML namespace of the latest sdk-sys-img XML. */
    public static final String NS_URI = getSchemaUri(NS_LATEST_VERSION);

    /** The root sdk-sys-img element */
    public static final String NODE_SDK_SYS_IMG     = "sdk-sys-img";       //$NON-NLS-1$

    /** A system-image tag id. */
    public static final String ATTR_TAG_ID = "tag-id";                          //$NON-NLS-1$
    /** The user-visible display part of a system-image tag id. Optional. */
    public static final String ATTR_TAG_DISPLAY = "tag-display";                //$NON-NLS-1$

    /** An add-on sub-element, indicating this is an add-on system image. */
    public static final String NODE_ADD_ON = SdkAddonConstants.NODE_ADD_ON;

    /**
     * Returns a stream to the requested {@code sdk-sys-img} XML Schema.
     *
     * @param version Between 1 and {@link #NS_LATEST_VERSION}, included.
     * @return An {@link InputStream} object for the local XSD file or
     *         null if there is no schema for the requested version.
     */
    public static StreamSource[] getXsdStream(int version) {
        return RepoXsdUtil.getXsdStream(NODE_SDK_SYS_IMG, version);
    }

    /**
     * Returns the URI of the sdk-sys-img schema for the given version number.
     * @param version Between 1 and {@link #NS_LATEST_VERSION} included.
     */
    public static String getSchemaUri(int version) {
        return String.format(NS_BASE + "%d", version);           //$NON-NLS-1$
    }
}
