/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.dom.xml;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AndroidXmlResourcesUtil {
  @NonNls public static final String SEARCHABLE_TAG_NAME = "searchable";
  @NonNls public static final String KEYBOARD_TAG_NAME = "Keyboard";
  @NonNls public static final String DEVICE_ADMIN_TAG_NAME = "device-admin";
  @NonNls public static final String ACCOUNT_AUTHENTICATOR_TAG_NAME = "account-authenticator";
  @NonNls public static final String PREFERENCE_HEADERS_TAG_NAME = "preference-headers";

  public static final ImmutableMap<String, String> SPECIAL_STYLEABLE_NAMES = ImmutableMap.<String, String>builder()
    .put(SdkConstants.TAG_APPWIDGET_PROVIDER, "AppWidgetProviderInfo")
    .put(SEARCHABLE_TAG_NAME, "Searchable")
    .put("actionkey", "SearchableActionKey")
    .put("intent", "Intent")
    .put(KEYBOARD_TAG_NAME, "Keyboard")
    .put("Row", "Keyboard_Row")
    .put("Key", "Keyboard_Key")
    .put(DEVICE_ADMIN_TAG_NAME, "DeviceAdmin")
    .put(ACCOUNT_AUTHENTICATOR_TAG_NAME, "AccountAuthenticator")
    .put("header", "PreferenceHeader")
    .build();

  private static final ImmutableSet<String> ROOT_TAGS = ImmutableSet
    .of(SdkConstants.TAG_APPWIDGET_PROVIDER, SEARCHABLE_TAG_NAME, KEYBOARD_TAG_NAME, DEVICE_ADMIN_TAG_NAME, ACCOUNT_AUTHENTICATOR_TAG_NAME,
        PREFERENCE_HEADERS_TAG_NAME);

  private AndroidXmlResourcesUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    List<String> result = new ArrayList<String>();
    result.addAll(AndroidDomUtil.removeUnambiguousNames(AndroidDomExtender.getPreferencesClassMap(facet)));
    result.addAll(ROOT_TAGS);

    return result;
  }

  public static boolean isSupportedRootTag(@NotNull AndroidFacet facet, @NotNull String rootTagName) {
    return ROOT_TAGS.contains(rootTagName) ||
           SimpleClassMapConstructor.findClassByTagName(facet, rootTagName, SdkConstants.CLASS_PREFERENCE) != null;
  }
}
