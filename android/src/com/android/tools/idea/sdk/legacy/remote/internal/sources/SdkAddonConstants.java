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
 * Public constants for the sdk-addon XML Schema.
 */
public class SdkAddonConstants extends RepoConstants {

    /**
     * The latest version of the sdk-addon XML Schema.
     * Valid version numbers are between 1 and this number, included.
     */
    public static final int NS_LATEST_VERSION = 7;

    /**
     * The default name looked for by SdkSource when trying to load an
     * sdk-addon XML if the URL doesn't match an existing resource.
     */
    public static final String URL_DEFAULT_FILENAME = "addon.xml";         //$NON-NLS-1$

    /** The base of our sdk-addon XML namespace. */
    private static final String NS_BASE =
        "http://schemas.android.com/sdk/android/addon/";                   //$NON-NLS-1$

    /**
     * The pattern of our sdk-addon XML namespace.
     * Matcher's group(1) is the schema version (integer).
     */
    public static final String NS_PATTERN = NS_BASE + "([0-9]+)";     //$NON-NLS-1$

    /** The XML namespace of the latest sdk-addon XML. */
    public static final String NS_URI = getSchemaUri(NS_LATEST_VERSION);

    /** The root sdk-addon element */
    public static final String NODE_SDK_ADDON       = "sdk-addon";         //$NON-NLS-1$

    /** An add-on package. */
    public static final String NODE_ADD_ON          = "add-on";            //$NON-NLS-1$

    /** An extra package. */
    public static final String NODE_EXTRA           = "extra";             //$NON-NLS-1$

    /**
     * Returns a stream to the requested {@code sdk-addon} XML Schema.
     *
     * @param version Between 1 and {@link #NS_LATEST_VERSION}, included.
     * @return An {@link InputStream} object for the local XSD file or
     *         null if there is no schema for the requested version.
     */
    public static StreamSource[] getXsdStream(int version) {
        return RepoXsdUtil.getXsdStream(NODE_SDK_ADDON, version);
    }

    /**
     * Returns the URI of the sdk-addon schema for the given version number.
     * @param version Between 1 and {@link #NS_LATEST_VERSION} included.
     */
    public static String getSchemaUri(int version) {
        return String.format(NS_BASE + "%d", version);           //$NON-NLS-1$
    }
}
