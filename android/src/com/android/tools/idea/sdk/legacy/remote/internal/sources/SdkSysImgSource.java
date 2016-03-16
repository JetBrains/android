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

import com.android.annotations.Nullable;
import org.w3c.dom.Document;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;


/**
 * An sdk-sys-img source, i.e. a download site for system-image packages.
 * A repository describes one or more {@link RemotePkgInfo}s available for download.
 */
public class SdkSysImgSource extends SdkSource {

    /**
     * Constructs a new source for the given repository URL.
     * @param url The source URL. Cannot be null. If the URL ends with a /, the default
     *            sys-img.xml filename will be appended automatically.
     * @param uiName The UI-visible name of the source. Can be null.
     */
    public SdkSysImgSource(String url, String uiName) {
        super(url, uiName);
    }

    /**
     * Returns true if this is an addon source.
     * We only load addons and extras from these sources.
     */
    @Override
    public boolean isAddonSource() {
        return false;
    }

    /**
     * Returns true if this is a system-image source.
     * We only load system-images from these sources.
     */
    @Override
    public boolean isSysImgSource() {
        return true;
    }


    @Override
    protected String[] getDefaultXmlFileUrls() {
        return new String[] { SdkSysImgConstants.URL_DEFAULT_FILENAME };
    }

    @Override
    protected int getNsLatestVersion() {
        return SdkSysImgConstants.NS_LATEST_VERSION;
    }

    @Override
    protected String getNsUri() {
        return SdkSysImgConstants.NS_URI;
    }

    @Override
    protected String getNsPattern() {
        return SdkSysImgConstants.NS_PATTERN;
    }

    @Override
    protected String getSchemaUri(int version) {
        return SdkSysImgConstants.getSchemaUri(version);
    }

    @Override
    protected String getRootElementName() {
        return SdkSysImgConstants.NODE_SDK_SYS_IMG;
    }

    @Override
    protected StreamSource[] getXsdStream(int version) {
        return SdkSysImgConstants.getXsdStream(version);
    }

    /**
     * This kind of schema does not support forward-evolution of the &lt;tool&gt; element.
     *
     * @param xml The input XML stream. Can be null.
     * @return Always null.
     * @null This implementation always return null.
     */
    @Override
    protected Document findAlternateToolsXml(@Nullable InputStream xml) {
        return null;
    }
}
