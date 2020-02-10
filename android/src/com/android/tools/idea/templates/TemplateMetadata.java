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
package com.android.tools.idea.templates;

import static com.android.tools.idea.templates.Template.ATTR_CONSTRAINTS;
import static com.android.tools.idea.templates.Template.ATTR_DESCRIPTION;
import static com.android.tools.idea.templates.Template.ATTR_NAME;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

/**
 * An ADT template along with metadata
 */
public class TemplateMetadata {
  /**
   * Constraints that can be applied to template that helps the UI add a
   * validator etc for user input. These are typically combined into a set
   * of constraints via an EnumSet.
   */
  public enum TemplateConstraint {
    ANDROIDX,
    KOTLIN
  }

  public static final String ATTR_DEPENDENCIES_MULTIMAP = "dependenciesMultimap";

  private static final String ATTR_TEMPLATE_REVISION = "revision";
  private static final String ATTR_MIN_BUILD_API = "minBuildApi";
  private static final String ATTR_MIN_API = "minApi";

  private final Document myDocument;
  private final Map<String, Parameter> myParameterMap = new LinkedHashMap<>();
  private final String myFormFactor = "Mobile";
  private final String myCategory = "Activity";

  TemplateMetadata(@NotNull Document document) {
    myDocument = document;
  }

  @Nullable
  public String getTitle() {
    return getAttrNonEmpty(ATTR_NAME);
  }

  @Nullable
  public String getDescription() {
    return getAttrNonEmpty(ATTR_DESCRIPTION);
  }

  public int getMinSdk() {
    return getInteger(ATTR_MIN_API, 1);
  }

  public int getMinBuildApi() {
    return getInteger(ATTR_MIN_BUILD_API, 1);
  }

  @NotNull
  public EnumSet<TemplateConstraint> getConstraints() {
    String constraintString = myDocument.getDocumentElement().getAttribute(ATTR_CONSTRAINTS);
    if (!isNullOrEmpty(constraintString)) {
      List<TemplateConstraint> constraintsList = new ArrayList<>();
      for (String constraint : Splitter.on('|').omitEmptyStrings().trimResults().split(constraintString)) {
        constraintsList.add(TemplateConstraint.valueOf(constraint.toUpperCase(Locale.US)));
      }
      return EnumSet.copyOf(constraintsList);
    }

    return EnumSet.noneOf(TemplateConstraint.class);
  }

  public int getRevision() {
    return getInteger(ATTR_TEMPLATE_REVISION, 1);
  }

  @Nullable
  public String getCategory() {
    return myCategory;
  }

  /**
   * Returns the list of available parameters
   */
  @NotNull
  public Collection<Parameter> getParameters() {
    return myParameterMap.values();
  }

  /**
   * Returns the parameter of the given id, or null if not found
   *
   * @param id the id of the target parameter
   * @return the corresponding parameter, or null if not found
   */
  @Nullable
  public Parameter getParameter(@NotNull String id) {
    return myParameterMap.get(id);
  }

  @Nullable
  private String getAttrNonEmpty(@NotNull String attrName) {
    String attr = myDocument.getDocumentElement().getAttribute(attrName);
    return (attr == null || attr.isEmpty()) ? null : attr;
  }

  private int getInteger(@NotNull String attrName, int defaultValue) {
    try {
      return Integer.parseInt(myDocument.getDocumentElement().getAttribute(attrName));
    }
    catch (NumberFormatException nfe) {
      // Templates aren't allowed to contain codenames, should always be an integer
      //LOG.warn(nfe);
      return defaultValue;
    }
    catch (RuntimeException e) {
      return defaultValue;
    }
  }

  /**
   * Computes a suitable build api string, e.g. for API level 18 the build
   * API string is "18".
   */
  @NotNull
  public static String getBuildApiString(@NotNull AndroidVersion version) {
    return version.isPreview() ? AndroidTargetHash.getPlatformHashString(version) : version.getApiString();
  }
}
