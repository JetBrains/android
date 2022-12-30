/*
 * Copyright (C) 2019 The Android Open Source Project
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

package org.jetbrains.android.dom.xml;

import static com.android.AndroidXConstants.PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX;
import static com.android.AndroidXConstants.PreferenceAndroidX.CLASS_PREFERENCE_GROUP_ANDROIDX;
import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.PreferenceClasses.CLASS_PREFERENCE;
import static com.android.SdkConstants.PreferenceClasses.CLASS_PREFERENCE_GROUP;

import com.android.SdkConstants;
import com.android.tools.idea.dom.xml.PathsDomFileDescription;
import com.android.tools.idea.psi.TagToClassMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidClassesForXmlUtilKt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AndroidXmlResourcesUtil {
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

  public static final ImmutableSet<String> PREFERENCES_ROOT_TAGS = ImmutableSet.of(
    SdkConstants.TAG_APPWIDGET_PROVIDER, SEARCHABLE_TAG_NAME, KEYBOARD_TAG_NAME, DEVICE_ADMIN_TAG_NAME, ACCOUNT_AUTHENTICATOR_TAG_NAME,
    PREFERENCE_HEADERS_TAG_NAME, PathsDomFileDescription.TAG_NAME);

  public static final ImmutableSet<String> ROOT_TAGS =
    ImmutableSet.<String>builder().addAll(PREFERENCES_ROOT_TAGS).add(AppRestrictionsDomFileDescription.ROOT_TAG_NAME).build();


  private AndroidXmlResourcesUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    List<String> result = new ArrayList<>();

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(facet.getModule().getProject());
    boolean hasAndroidXClass = javaPsiFacade.findClass(CLASS_PREFERENCE_ANDROIDX.newName(), facet.getModule().getModuleWithLibrariesScope()) != null;
    if (hasAndroidXClass) {
      result.addAll(
        AndroidDomUtil
          .removeUnambiguousNames(TagToClassMapper.getInstance(facet.getModule()).getClassMap(CLASS_PREFERENCE_ANDROIDX.newName()))
      );
    } else if (javaPsiFacade.findClass(CLASS_PREFERENCE_ANDROIDX.oldName(), facet.getModule().getModuleWithLibrariesScope()) != null) {
      result.addAll(
        AndroidDomUtil
          .removeUnambiguousNames(TagToClassMapper.getInstance(facet.getModule()).getClassMap(CLASS_PREFERENCE_ANDROIDX.oldName()))
      );
    }
    else {
      result.addAll(AndroidDomUtil.removeUnambiguousNames(TagToClassMapper.getInstance(facet.getModule()).getClassMap(CLASS_PREFERENCE)));
    }
    result.addAll(ROOT_TAGS);

    return result;
  }

  public static boolean isSupportedRootTag(@NotNull AndroidFacet facet, @NotNull String rootTagName) {
    return ROOT_TAGS.contains(rootTagName) ||
           AndroidClassesForXmlUtilKt.findClassValidInXMLByName(facet, rootTagName, CLASS_PREFERENCE) != null;
  }

  public enum PreferenceSource {
    ANDROIDX(CLASS_PREFERENCE_ANDROIDX.newName(), CLASS_PREFERENCE_GROUP_ANDROIDX.newName()),
    SUPPORT(CLASS_PREFERENCE_ANDROIDX.oldName(), CLASS_PREFERENCE_GROUP_ANDROIDX.oldName()),
    FRAMEWORK(CLASS_PREFERENCE, CLASS_PREFERENCE_GROUP);

    private final String myQualifiedBaseClass;
    private final String myQualifiedGroupClass;

    PreferenceSource(String qualifiedBaseClass, String qualifiedGroupClass) {
      myQualifiedBaseClass = qualifiedBaseClass;
      myQualifiedGroupClass = qualifiedGroupClass;
    }

    public String getQualifiedBaseClass() {
      return myQualifiedBaseClass;
    }

    public String getQualifiedGroupClass() {
      return myQualifiedGroupClass;
    }

    public static PreferenceSource getPreferencesSource(@NotNull XmlTag tag, @NotNull AndroidFacet facet) {
      XmlTag rootTag = ((XmlFile)tag.getContainingFile()).getRootTag();
      if (rootTag == null) {
        return FRAMEWORK;
      }
      String rootTagName = rootTag.getName();
      if (rootTagName.startsWith(ANDROIDX_PKG_PREFIX)) {
        return ANDROIDX;
      }
      else if (rootTagName.startsWith("android.support.v") && StringUtil.getPackageName(rootTagName).endsWith("preference")) {
        return SUPPORT;
      }
      else if (rootTagName.startsWith(ANDROID_PKG_PREFIX)) {
        return FRAMEWORK;
      }
      Project project = facet.getModule().getProject();
      String supportLibName = CLASS_PREFERENCE_ANDROIDX.oldName();
      String androidXLibName = CLASS_PREFERENCE_ANDROIDX.newName();
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      if (psiFacade.findClass(supportLibName, rootTag.getResolveScope()) == null
          && psiFacade.findClass(androidXLibName, rootTag.getResolveScope()) == null) {
        return FRAMEWORK;
      }
      PsiJavaParserFacade parser = psiFacade.getParserFacade();
      try {
        PsiType type = parser.createTypeFromText(rootTagName, null);
        if (type instanceof PsiClassType && ((PsiClassType)type).resolve() != null) {
          if (InheritanceUtil.isInheritor(type, androidXLibName)) {
            return ANDROIDX;
          } else if (InheritanceUtil.isInheritor(type, supportLibName)) {
            return SUPPORT;
          }
          return FRAMEWORK;
        }
      } catch (IncorrectOperationException ignored) {}
      // The root tag is an unqualified name (eg. PreferenceScreen) or does not specify a valid type eg. <preference-headers>, if AndroidX
      // Preference class can be found then we assume that AndroidX classes are being used. Otherwise, support libraries are being used.
      if (psiFacade.findClass(androidXLibName, rootTag.getResolveScope()) != null) {
        return ANDROIDX;
      } else {
        return SUPPORT;
      }
    }
  }
}
