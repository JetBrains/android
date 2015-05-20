/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.SdkUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.InputValidatorEx;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.android.SdkConstants.DOT_XML;

/**
 * Validator which ensures that new Android resource names are valid.
 */
public class ResourceNameValidator implements InputValidatorEx {
  private static final Logger LOG = Logger.getInstance(ResourceNameValidator.class);

  /**
   * Set of existing names to check for conflicts with
   */
  private Set<String> myExisting;

  /**
   * If true, the validated name must be unique
   */
  private boolean myUnique = true;

  /**
   * If true, the validated name must exist
   */
  private boolean myExist;

  /**
   * True if the resource name being considered is a "file" based resource (where the
   * resource name is the actual file name, rather than just a value attribute inside an
   * XML file name of arbitrary name
   */
  private boolean myIsFileType;

  /**
   * True if the resource type can point to image resources
   */
  private boolean myIsImageType;

  /**
   * If true, allow .xml as a name suffix
   */
  private boolean myAllowXmlExtension;

  private ResourceNameValidator(boolean allowXmlExtension, @Nullable Set<String> existing, boolean isFileType, boolean isImageType) {
    myAllowXmlExtension = allowXmlExtension;
    myExisting = existing;
    myIsFileType = isFileType;
    myIsImageType = isImageType;
  }

  /**
   * Makes the resource name validator require that names are unique.
   *
   * @return this, for construction chaining
   */
  public ResourceNameValidator unique() {
    myUnique = true;
    myExist = false;

    return this;
  }

  /**
   * Makes the resource name validator require that names already exist
   *
   * @return this, for construction chaining
   */
  public ResourceNameValidator exist() {
    myExist = true;
    myUnique = false;

    return this;
  }

  @Nullable
  @Override
  public String getErrorText(String inputString) {
    try {
      if (inputString == null || inputString.trim().length() == 0) {
        return "Enter a new name";
      }

      if (myAllowXmlExtension && inputString.endsWith(DOT_XML)) {
        inputString = inputString.substring(0, inputString.length() - DOT_XML.length());
      }

      if (myAllowXmlExtension && myIsImageType && SdkUtils.hasImageExtension(inputString)) {
        inputString = inputString.substring(0, inputString.lastIndexOf('.'));
      }

      if (!myIsFileType) {
        inputString = AndroidResourceUtil.getFieldNameByResourceName(inputString);
      }

      if (myAllowXmlExtension) {
        if (inputString.indexOf('.') != -1 && !inputString.endsWith(DOT_XML)) {
          if (myIsImageType) {
            return "The filename must end with .xml or .png";
          }
          else {
            return "The filename must end with .xml";
          }
        }
      }

      // Resource names must be valid Java identifiers, since they will
      // be represented as Java identifiers in the R file:
      if (!Character.isJavaIdentifierStart(inputString.charAt(0))) {
        return "The resource name must begin with a character";
      }
      for (int i = 1, n = inputString.length(); i < n; i++) {
        char c = inputString.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          return String.format("'%1$c' is not a valid resource name character", c);
        }
      }

      if (myIsFileType) {
        char first = inputString.charAt(0);
        if (!(first >= 'a' && first <= 'z')) {
          return String.format("File-based resource names must start with a lowercase letter.");
        }

        // AAPT only allows lowercase+digits+_:
        // "%s: Invalid file name: must contain only [a-z0-9_.]","
        for (int i = 0, n = inputString.length(); i < n; i++) {
          char c = inputString.charAt(i);
          if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
            return String.format("File-based resource names must contain only lowercase a-z, 0-9, or _.");
          }
        }
      }

      if (!AndroidUtils.isIdentifier(inputString)) {
        // It's a reserved keyword. There are other reasons isIdentifier can return false,
        // but we've dealt with those above.
        return String.format("%1$s is not a valid name (reserved Java keyword)", inputString);
      }

      if (myExisting != null && (myUnique || myExist)) {
        boolean exists = myExisting.contains(inputString);
        if (myUnique && exists) {
          return String.format("%1$s already exists", inputString);
        }
        else if (myExist && !exists) {
          return String.format("%1$s does not exist", inputString);
        }
      }

      return null;
    }
    catch (Exception e) {
      LOG.error("Validation failed: " + e.toString(), e);
      return "";
    }
  }

  public boolean doesResourceExist(@NotNull final String resourceName) {
    return myExisting != null && myExisting.contains(resourceName);
  }

  /**
   * Creates a new {@link ResourceNameValidator}
   *
   * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
   *                          resource name
   * @param type              the resource type of the resource name being validated
   * @return a new {@link ResourceNameValidator}
   */
  public static ResourceNameValidator create(boolean allowXmlExtension, @NotNull ResourceFolderType type) {
    boolean isFileType = type != ResourceFolderType.VALUES;
    return new ResourceNameValidator(allowXmlExtension, null, isFileType, type == ResourceFolderType.DRAWABLE);
  }

  /**
   * Creates a new {@link ResourceNameValidator}
   *
   * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
   *                          resource name
   * @param existing          An optional set of names that already exist (and therefore will not
   *                          be considered valid if entered as the new name)
   * @param type              the resource type of the resource name being validated
   * @return a new {@link ResourceNameValidator}
   */
  public static ResourceNameValidator create(boolean allowXmlExtension, @Nullable Set<String> existing, @NotNull ResourceType type) {
    boolean isFileType = ResourceHelper.isFileBasedResourceType(type);
    return new ResourceNameValidator(allowXmlExtension, existing, isFileType, type == ResourceType.DRAWABLE).unique();
  }

  /**
   * Creates a new {@link ResourceNameValidator}. By default, the name will need to be
   * unique in the project.
   *
   * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
   *                          resource name
   * @param appResources      the app resources to validate new resource names for
   * @param type              the resource type of the resource name being validated
   * @return a new {@link ResourceNameValidator}
   */
  public static ResourceNameValidator create(boolean allowXmlExtension, @Nullable LocalResourceRepository appResources,
                                             @NotNull ResourceType type) {
    return create(allowXmlExtension, appResources, type, ResourceHelper.isFileBasedResourceType(type));
  }

  /**
   * Creates a new {@link ResourceNameValidator}. By default, the name will need to be
   * unique in the project.
   *
   * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
   *                          resource name
   * @param appResources      the app resources to validate new resource names for
   * @param type              the resource type of the resource name being validated
   * @param isFileType        allows you to specify if the resource is a file.
   *                          for resources that can be both files and values like Color.
   * @return a new {@link ResourceNameValidator}
   */
  public static ResourceNameValidator create(boolean allowXmlExtension, @Nullable LocalResourceRepository appResources,
                                             @NotNull ResourceType type, boolean isFileType) {
    Set<String> existing = null;
    if (appResources != null) {
      existing = new HashSet<String>();
      Collection<String> items = appResources.getItemsOfType(type);
      for (String resourceName : items) {
        existing.add(AndroidResourceUtil.getFieldNameByResourceName(resourceName));
      }
    }

    return new ResourceNameValidator(allowXmlExtension, existing, isFileType, type == ResourceType.DRAWABLE);
  }

  @Override
  public boolean checkInput(String inputString) {
    return getErrorText(inputString) == null;
  }

  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}
