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

package com.android.tools.idea.sdk.remote.internal.sources;

import com.android.sdklib.repository.RepoXsdUtil;

import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;

/**
 * Public constants for the sdk-addons-list XML Schema.
 */
public class SdkAddonsListConstants {

  /**
   * The base of our sdk-addons-list XML namespace.
   */
  private static final String NS_BASE = "http://schemas.android.com/sdk/android/addons-list/";              //$NON-NLS-1$

  /**
   * The pattern of our sdk-addons-list XML namespace.
   * Matcher's group(1) is the schema version (integer).
   */
  public static final String NS_PATTERN = NS_BASE + "([1-9][0-9]*)";      //$NON-NLS-1$

  /**
   * The latest version of the sdk-addons-list XML Schema.
   * Valid version numbers are between 1 and this number, included.
   */
  public static final int NS_LATEST_VERSION = 2;

  /**
   * The XML namespace of the latest sdk-addons-list XML.
   */
  public static final String NS_URI = getSchemaUri(NS_LATEST_VERSION);


  /**
   * The canonical URL filename for addons-list XML files.
   */
  public static final String URL_DEFAULT_FILENAME = getDefaultName(NS_LATEST_VERSION);

  /**
   * The URL where to find the official addons list fle.
   */
  public static final String URL_ADDON_LIST = SdkRepoConstants.URL_GOOGLE_SDK_SITE + URL_DEFAULT_FILENAME;


  /**
   * The root sdk-addons-list element
   */
  public static final String NODE_SDK_ADDONS_LIST = "sdk-addons-list";    //$NON-NLS-1$

  /**
   * An add-on site.
   */
  public static final String NODE_ADDON_SITE = "addon-site";              //$NON-NLS-1$

  /**
   * A system image site.
   */
  public static final String NODE_SYS_IMG_SITE = "sys-img-site";          //$NON-NLS-1$

  /**
   * The UI-visible name of the add-on site.
   */
  public static final String NODE_NAME = "name";                          //$NON-NLS-1$

  /**
   * The URL of the site.
   * <p/>
   * This can be either the exact URL of the an XML resource conforming
   * to the latest sdk-addon-N.xsd schema, or it can be the URL of a
   * 'directory', in which case the manager will look for a resource
   * named 'addon.xml' at this location.
   * <p/>
   * Examples:
   * <pre>
   *    http://www.example.com/android/my_addons.xml
   *  or
   *    http://www.example.com/android/
   * </pre>
   * In the second example, the manager will actually look for
   * http://www.example.com/android/addon.xml
   */
  public static final String NODE_URL = "url";                            //$NON-NLS-1$

  /**
   * Returns a stream to the requested sdk-addon XML Schema.
   *
   * @param version Between 1 and {@link #NS_LATEST_VERSION}, included.
   * @return An {@link InputStream} object for the local XSD file or
   * null if there is no schema for the requested version.
   */
  public static StreamSource[] getXsdStream(int version) {
    return RepoXsdUtil.getXsdStream("sdk-addons-list", version);
  }

  /**
   * Returns the URI of the sdk-addon schema for the given version number.
   *
   * @param version Between 1 and {@link #NS_LATEST_VERSION} included.
   */
  public static String getSchemaUri(int version) {
    return String.format(NS_BASE + "%d", version);                      //$NON-NLS-1$
  }

  public static String getDefaultName(int version) {
    return String.format("addons_list-%1$d.xml", version);              //$NON-NLS-1$
  }
}
