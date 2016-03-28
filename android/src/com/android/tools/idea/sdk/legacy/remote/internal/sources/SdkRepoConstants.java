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


import com.android.annotations.NonNull;
import com.android.sdklib.repository.legacy.RepoXsdUtil;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public constants for the sdk-repository XML Schema.
 */
public class SdkRepoConstants extends RepoConstants {

    /**
     * The latest version of the sdk-repository XML Schema.
     * Valid version numbers are between 1 and this number, included.
     */
    public static final int NS_LATEST_VERSION = 12;

    /**
     * The min version of the sdk-repository XML Schema we'll try to load.
     * When looking for a repository-N.xml on the server, we'll check from
     * {@link #NS_LATEST_VERSION} down to this revision.
     * We only introduced the "repository-N.xml" pattern start with revision
     * 5, so we know that <em>our</em> server will never contain a repository
     * XML with a schema version lower than this one.
     */
    public static final int NS_SERVER_MIN_VERSION = 5;

    /**
     * The URL of the official Google sdk-repository site.
     * The URL ends with a /, allowing easy concatenation.
     * */
    public static final String URL_GOOGLE_SDK_SITE =
        "https://dl.google.com/android/repository/";                        //$NON-NLS-1$

    /**
     * The default name looked for by SdkSource when trying to load an
     * sdk-repository XML if the URL doesn't match an existing resource.
     */
    public static final String URL_DEFAULT_FILENAME = "repository.xml";         //$NON-NLS-1$

    /**
     * The pattern name looked by {@link SdkSource} when trying to load
     * an sdk-repository XML that is specific to a given XSD revision.
     * <p/>
     * This must be used with {@link String#format(String, Object...)} with
     * one integer parameter between 1 and {@link #NS_LATEST_VERSION}.
     */
    public static final String URL_FILENAME_PATTERN = "repository-%1$d.xml";      //$NON-NLS-1$

    /** The base of our sdk-repository XML namespace. */
    private static final String NS_BASE =
        "http://schemas.android.com/sdk/android/repository/";                   //$NON-NLS-1$

    /**
     * The pattern of our sdk-repository XML namespace.
     * Matcher's group(1) is the schema version (integer).
     */
    public static final String NS_PATTERN = NS_BASE + "([0-9]+)";          //$NON-NLS-1$

    /** The XML namespace of the latest sdk-repository XML. */
    public static final String NS_URI = getSchemaUri(NS_LATEST_VERSION);

    /** The root sdk-repository element */
    public static final String NODE_SDK_REPOSITORY = "sdk-repository";        //$NON-NLS-1$

    /* The major revision for tool and platform-tool package
     * (the full revision number is revision.minor.micro + preview#.)
     * Mandatory int > 0. 0 when missing, which should not happen in
     * a valid document. */
    public static final String NODE_MAJOR_REV       = "major";                //$NON-NLS-1$
    /* The minor revision for tool and platform-tool package
     * (the full revision number is revision.minor.micro + preview#.)
     * Optional int >= 0. Implied to be 0 when missing. */
    public static final String NODE_MINOR_REV       = "minor";                //$NON-NLS-1$
    /* The micro revision for tool and platform-tool package
     * (the full revision number is revision.minor.micro + preview#.)
     * Optional int >= 0. Implied to be 0 when missing. */
    public static final String NODE_MICRO_REV       = "micro";                //$NON-NLS-1$
    /* The preview revision for tool and platform-tool package.
     * Int > 0, only present for "preview / release candidate" packages. */
    public static final String NODE_PREVIEW         = "preview";              //$NON-NLS-1$

    /** A platform package. */
    public static final String NODE_PLATFORM        = "platform";             //$NON-NLS-1$
    /** A tool package. */
    public static final String NODE_TOOL            = "tool";                 //$NON-NLS-1$
    /** A platform-tool package. */
    public static final String NODE_PLATFORM_TOOL   = "platform-tool";        //$NON-NLS-1$
    /** A build-tool package. */
    public static final String NODE_BUILD_TOOL      = "build-tool";           //$NON-NLS-1$
    /** A doc package. */
    public static final String NODE_DOC             = "doc";                  //$NON-NLS-1$
    /** A sample package. */
    public static final String NODE_SAMPLE          = "sample";               //$NON-NLS-1$
    /** A source package. */
    public static final String NODE_SOURCE          = "source";               //$NON-NLS-1$
    /** A NDK package. */
    public static final String NODE_NDK             = "ndk";                  //$NON-NLS-1$
    /** A LLDB package. */
    public static final String NODE_LLDB             = "lldb";                //$NON-NLS-1$
    /** A generic package. */
    public static final String NODE_GENERIC         = "generic";              //$NON-NLS-1$

    /**
     * List of possible nodes in a repository XML. Used to populate options automatically
     * in the no-GUI mode.
     */
    public static final String[] NODES = {
        NODE_PLATFORM,
        NODE_SYSTEM_IMAGE,
        NODE_TOOL,
        NODE_PLATFORM_TOOL,
        NODE_DOC,
        NODE_SAMPLE,
        NODE_SOURCE,
    };

    /**
     * Returns a stream to the requested {@code sdk-repository} XML Schema.
     *
     * @param version Between 1 and {@link #NS_LATEST_VERSION}, included.
     * @return An {@link InputStream} object for the local XSD file or
     *         null if there is no schema for the requested version.
     */
    public static StreamSource[] getXsdStream(int version) {
        return RepoXsdUtil.getXsdStream(NODE_SDK_REPOSITORY, version);
    }

    /**
     * Returns the URI of the SDK Repository schema for the given version number.
     * @param version Between 1 and {@link #NS_LATEST_VERSION} included.
     */
    public static String getSchemaUri(int version) {
        return String.format(NS_BASE + "%d", version);           //$NON-NLS-1$
    }

    /**
     * Checks whether the schema version is greater or equal to the specified one.
     *
     * @param nsUri A non-null sdk-repository schema URI.
     * @param minVersion The minimum version accepted.
     * @return True if the URI is valid and has at least the required version. False otherwise.
     */
    public static boolean versionGreaterOrEqualThan(@NonNull String nsUri, int minVersion) {
        Pattern nsPattern = Pattern.compile(NS_PATTERN);
        Matcher m = nsPattern.matcher(nsUri);
        if (m.matches()) {
            String version = m.group(1);
            try {
                return Integer.parseInt(version) >= minVersion;
            } catch (NumberFormatException e) {
            }
        }
        return false;
    }

}
