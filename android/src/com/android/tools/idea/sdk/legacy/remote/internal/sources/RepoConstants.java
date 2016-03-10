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

/**
 * Public constants common to the sdk-repository and sdk-addon XML Schemas.
 */
public class RepoConstants {

  /**
   * The license definition.
   */
  public static final String NODE_LICENSE = "license";                  //$NON-NLS-1$
  /**
   * The optional uses-license for all packages or for a lib.
   */
  public static final String NODE_USES_LICENSE = "uses-license";             //$NON-NLS-1$
  /**
   * The revision, an int > 0, for all packages.
   */
  public static final String NODE_REVISION = "revision";                 //$NON-NLS-1$
  /**
   * The optional description for all packages or for a lib.
   */
  public static final String NODE_DESCRIPTION = "description";              //$NON-NLS-1$
  /**
   * The optional description URL for all packages.
   */
  public static final String NODE_DESC_URL = "desc-url";                 //$NON-NLS-1$
  /**
   * The optional obsolete qualifier for all packages.
   */
  public static final String NODE_OBSOLETE = "obsolete";                 //$NON-NLS-1$
  /**
   * The optional project-files provided by extra packages.
   */
  public static final String NODE_PROJECT_FILES = "project-files";            //$NON-NLS-1$

  /**
   * A system-image package.
   */
  public static final String NODE_SYSTEM_IMAGE = "system-image";           //$NON-NLS-1$

  /* An included-ABI element for a system-image package. */
  public static final String NODE_ABI_INCLUDED = "included-abi";           //$NON-NLS-1$
  /* An ABI element for a system-image package. */
  public static final String NODE_ABI = "abi";                    //$NON-NLS-1$

  /**
   * The optional minimal tools revision required by platform & extra packages.
   */
  public static final String NODE_MIN_TOOLS_REV = "min-tools-rev";            //$NON-NLS-1$
  /**
   * The optional minimal platform-tools revision required by tool packages.
   */
  public static final String NODE_MIN_PLATFORM_TOOLS_REV = "min-platform-tools-rev"; //$NON-NLS-1$
  /**
   * The optional minimal API level required by extra packages.
   */
  public static final String NODE_MIN_API_LEVEL = "min-api-level";            //$NON-NLS-1$

  /**
   * The version, a string, for platform packages.
   */
  public static final String NODE_VERSION = "version";                      //$NON-NLS-1$
  /**
   * The api-level, an int > 0, for platform, add-on and doc packages.
   */
  public static final String NODE_API_LEVEL = "api-level";                    //$NON-NLS-1$
  /**
   * The codename, a string, for platform packages.
   */
  public static final String NODE_CODENAME = "codename";                      //$NON-NLS-1$
  /**
   * The *old* vendor, a string, for add-on and extra packages.
   * Replaced by {@link #NODE_VENDOR_DISPLAY} and {@link #NODE_VENDOR_ID} in addon-v4.xsd.
   */
  public static final String NODE_VENDOR = "vendor";                       //$NON-NLS-1$
  /**
   * The vendor display string, for add-on and extra packages.
   */
  public static final String NODE_VENDOR_DISPLAY = "vendor-display";       //$NON-NLS-1$
  /**
   * The unique vendor id string, for add-on and extra packages.
   */
  public static final String NODE_VENDOR_ID = "vendor-id";                 //$NON-NLS-1$
  /**
   * The name, a string, for add-on packages or for libraries.
   * Replaced by {@link #NODE_NAME_DISPLAY} and {@link #NODE_NAME_ID} in addon-v4.xsd.
   */
  public static final String NODE_NAME = "name";                         //$NON-NLS-1$
  /**
   * The name display string, for add-on packages or for libraries.
   */
  public static final String NODE_NAME_DISPLAY = "name-display";         //$NON-NLS-1$
  /**
   * The unique name id string, for add-on packages or for libraries.
   */
  public static final String NODE_NAME_ID = "name-id";                   //$NON-NLS-1$
  /**
   * The optional string used to display a package in a list view.
   */
  public static final String NODE_LIST_DISPLAY = "list-display";              //$NON-NLS-1$

  /**
   * A layoutlib package.
   */
  public static final String NODE_LAYOUT_LIB = "layoutlib";                 //$NON-NLS-1$
  /**
   * The API integer for a layoutlib element.
   */
  public static final String NODE_API = "api";                       //$NON-NLS-1$

  /**
   * The libs container, optional for an add-on.
   */
  public static final String NODE_LIBS = "libs";                         //$NON-NLS-1$
  /**
   * A lib element in a libs container.
   */
  public static final String NODE_LIB = "lib";                          //$NON-NLS-1$

  /**
   * The path segment, a string, for extra packages.
   */
  public static final String NODE_PATH = "path";                         //$NON-NLS-1$

  /**
   * The old_path segments, a string, for extra packages.
   */
  public static final String NODE_OLD_PATHS = "old-paths";                    //$NON-NLS-1$

  /**
   * The archives container, for all packages.
   */
  public static final String NODE_ARCHIVES = "archives";                      //$NON-NLS-1$
  /**
   * An archive element, for the archives container.
   */
  public static final String NODE_ARCHIVE = "archive";                       //$NON-NLS-1$

  /**
   * An archive size, an int > 0.
   */
  public static final String NODE_SIZE = "size";                          //$NON-NLS-1$
  /**
   * A sha1 archive checksum, as a 40-char hex.
   */
  public static final String NODE_CHECKSUM = "checksum";                      //$NON-NLS-1$
  /**
   * A download archive URL, either absolute or relative to the repository xml.
   */
  public static final String NODE_URL = "url";                           //$NON-NLS-1$

  /**
   * Optional element to indicate an archive is only suitable for the specified OS. <br/>
   * Values: windows | macosx | linux.
   *
   * @replaces {@link #LEGACY_ATTR_OS}
   * @since repo-10, addon-7 and sys-img-3.
   */
  public static final String NODE_HOST_OS = "host-os";                   //$NON-NLS-1$
  /**
   * Optional element to indicate an archive is only suitable for the specified host bit size.<br/>
   * Values: 32 | 64.
   *
   * @since repo-10, addon-7 and sys-img-3.
   */
  public static final String NODE_HOST_BITS = "host-bits";               //$NON-NLS-1$
  /**
   * Optional element to indicate an archive is only suitable for the specified JVM bit size.<br/>
   * Values: 32 | 64.
   *
   * @replaces {@link #LEGACY_ATTR_ARCH}
   * @since repo-10, addon-7 and sys-img-3.
   */
  public static final String NODE_JVM_BITS = "jvm-bits";                 //$NON-NLS-1$
  /**
   * Optional element to indicate an archive is only suitable for a JVM equal or greater than
   * the specified value. <br/>
   * Value format: [1-9](\.[0-9]{1,2}){0,2}, e.g. "1.6", "1.7.0", "1.10" or "2"
   *
   * @since repo-10, addon-7 and sys-img-3.
   */
  public static final String NODE_MIN_JVM_VERSION = "min-jvm-version";        //$NON-NLS-1$



  /**
   * An archive checksum type, mandatory.
   */
  public static final String ATTR_TYPE = "type";                              //$NON-NLS-1$
  /**
   * An archive OS attribute, mandatory. <br/>
   * Use {@link #NODE_HOST_OS} instead in repo-10, addon-7 and sys-img-3.
   */
  public static final String LEGACY_ATTR_OS = "os";                                //$NON-NLS-1$
  /**
   * An optional archive Architecture attribute. <br/>
   * Use {@link #NODE_JVM_BITS} instead in repo-10, addon-7 and sys-img-3.
   */
  public static final String LEGACY_ATTR_ARCH = "arch";                              //$NON-NLS-1$

  /**
   * A license definition ID.
   */
  public static final String ATTR_ID = "id";                                  //$NON-NLS-1$
  /**
   * A license reference.
   */
  public static final String ATTR_REF = "ref";                                //$NON-NLS-1$


  /**
   * Type of a sha1 checksum.
   */
  public static final String SHA1_TYPE = "sha1";                              //$NON-NLS-1$

  /**
   * Length of a string representing a SHA1 checksum; always 40 characters long.
   */
  public static final int SHA1_CHECKSUM_LEN = 40;

  /**
   * Temporary folder used to hold downloads and extract archives during installation.
   * This folder will be located in the SDK.
   */
  public static final String FD_TEMP = "temp";     //$NON-NLS-1$

}
