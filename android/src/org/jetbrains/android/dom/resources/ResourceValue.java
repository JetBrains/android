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
package org.jetbrains.android.dom.resources;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.utils.DataBindingUtils;
import com.android.utils.HashCodes;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.pom.java.LanguageLevel;
import java.util.Objects;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResourceValue {
  private static final String PLUS_ID = "+id";
  /** The resource type portion in a resource URL ({@code @+id/}) but without the leading @. */
  private String myValue;
  private char myPrefix;
  private String myPackage;
  private String myResourceType;
  private String myResourceName;

  public static final ResourceValue INVALID = new ResourceValue();

  private ResourceValue() {
  }

  public char getPrefix() {
    return myPrefix;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ResourceValue that = (ResourceValue)o;

    if (myPrefix != that.myPrefix) return false;
    if (!Objects.equals(myPackage, that.myPackage)) return false;
    if (!Objects.equals(myResourceName, that.myResourceName)) return false;
    if (!Objects.equals(myResourceType, that.myResourceType)) return false;
    if (!Objects.equals(myValue, that.myValue)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(Objects.hashCode(myValue), Character.hashCode(myPrefix), Objects.hashCode(myPackage),
                         Objects.hashCode(myResourceType), Objects.hashCode(myResourceName));
  }

  @Nullable
  public static ResourceValue parse(@Nullable String s, boolean withLiterals, boolean withPrefix, boolean requireValid) {
    if (s == null) {
      return null;
    }
    if ((s.startsWith(PREFIX_RESOURCE_REF) || s.startsWith(PREFIX_THEME_REF)) && !DataBindingUtils.isBindingExpression(s)) {
      ResourceValue reference = reference(s, true);
      return reference != null && (!requireValid || reference.isValidReference()) ? reference : null;
    }
    else if (!withPrefix) {
      ResourceValue reference = reference(s, false);
      if (reference != null) {
        return reference;
      }
    }
    return withLiterals ? literal(s) : null;
  }

  public static ResourceValue literal(String value) {
    ResourceValue result = new ResourceValue();
    result.myValue = value;
    return result;
  }

  @Nullable
  public static ResourceValue reference(String value) {
    return reference(value, true);
  }

  @Nullable
  public static ResourceValue reference(String value, boolean withPrefix) {
    ResourceValue result = new ResourceValue();
    if (withPrefix) {
      assert !value.isEmpty();
      result.myPrefix = value.charAt(0);
    }
    int startIndex = withPrefix ? 1 : 0;
    int pos = value.indexOf('/');

    if (pos > 0) {
      String resType = value.substring(startIndex, pos);
      int colonIndex = resType.indexOf(':');
      if (colonIndex > 0) {
        result.myPackage = resType.substring(0, colonIndex);
        result.myResourceType = resType.substring(colonIndex + 1);
      }
      else {
        result.myResourceType = resType;
      }

      // @+drawable etc is invalid syntax, but if users write it in the editor, this can cause assertions
      // down the line, so proactively strip it out here.
      if (result.myResourceType.startsWith("+") && !result.myResourceType.equals(PLUS_ID)) {
        return null;
      }

      String suffix = value.substring(pos + 1);
      if (ResourceType.SAMPLE_DATA.getName().equals(resType)) {
        // For sample data, we can just take the suffix as the rest of the resource name
        result.myResourceName = value.substring(pos + 1);
        return result;
      }

      colonIndex = suffix.indexOf(':');
      if (colonIndex > 0) {
        String aPackage = suffix.substring(0, colonIndex);
        if (result.myPackage == null || result.myPackage.isEmpty() || aPackage.equals(result.myPackage)) {
          result.myPackage = aPackage;
          result.myResourceName = suffix.substring(colonIndex + 1);
        } else {
          result.myResourceName = suffix;
        }
      } else {
        result.myResourceName = suffix;
      }
    }
    else {
      int colonIndex = value.indexOf(':');
      if (colonIndex > startIndex) {
        result.myPackage = value.substring(startIndex, colonIndex);
        result.myResourceName = value.substring(colonIndex + 1);
      }
      else {
        result.myResourceName = value.substring(startIndex);
      }
    }

    return result;
  }

  public static ResourceValue referenceTo(char prefix, @Nullable String resPackage, @Nullable String resourceType, String resourceName) {
    ResourceValue result = new ResourceValue();
    result.myPrefix = prefix;
    result.myPackage = resPackage;
    result.myResourceType = resourceType;
    result.myResourceName = resourceName;
    return result;
  }

  public boolean isReference() {
    return myValue == null;
  }

  public boolean isValidReference() {
    // @null is a valid resource reference, even though it is not otherwise a valid
    // resource url (it's missing a resource type, "null" is a Java keyword, etc)
    if ("null".equals(myResourceName)) {
      return myResourceType == null && myPrefix == '@';
    }

    if (myResourceName == null || myResourceName.isEmpty()) {
      return false;
    }

    ResourceType type;
    if (myResourceType == null) {
      if (myPrefix == '?') {
        type = ResourceType.ATTR;
      } else {
        return false;
      }
    } else if (PLUS_ID.equals(myResourceType)) {
      type = ResourceType.ID;
    } else {
      type = ResourceType.fromXmlValue(myResourceType);
      if (type == null) {
        return false;
      }
    }

    if (type == ResourceType.SAMPLE_DATA) {
      // SAMPLE_DATA allows for characters that are not valid identifiers. This is ok because these are not compiled by aapt.
      return true;
    }

    return AndroidUtils.isIdentifier(myResourceName)
           // Value resources are allowed to contain . and : in the names
           || FolderTypeRelationship.getRelatedFolders(type).contains(ResourceFolderType.VALUES)
              && AndroidUtils.isIdentifier(IdeResourcesUtil.getFieldNameByResourceName(myResourceName));
  }

  @Nullable
  public String getErrorMessage() {
    // @null is a valid resource reference, even though it is not otherwise a valid
    // resource url (it's missing a resource type, "null" is a Java keyword, etc)
    if ("null".equals(myResourceName) && myResourceType == null && myPrefix == '@') {
      return null;
    }

    if (myResourceName == null || myResourceName.isEmpty()) {
      if (myResourceType == null && (myPrefix == '@' || myPrefix == '?')) {
        return "Missing resource type";
      }
      return "Missing resource name";
    }

    ResourceType type;
    if (myResourceType == null) {
      if (myPrefix != '?') {
        if (myPrefix == '@' && myResourceName.indexOf('/') == -1) {
          return "Missing /";
        }
        return "Missing resource type";
      }
      type = ResourceType.ATTR;
    } else if (PLUS_ID.equals(myResourceType)) {
      type = ResourceType.ID;
    } else {
      type = ResourceType.fromXmlValue(myResourceType);
      if (type == null) {
        return "Unknown resource type " + myResourceType;
      }
    }

    String name = myResourceName;
    if (FolderTypeRelationship.getRelatedFolders(type).contains(ResourceFolderType.VALUES)) {
      name = IdeResourcesUtil.getFieldNameByResourceName(name);
    }

    if (!AndroidUtils.isIdentifier(name)) {
      if (JavaLexer.isKeyword(name, LanguageLevel.JDK_1_5)) {
        return "Resource name cannot be a Java keyword (" + name + ")";
      }

      if (!Character.isJavaIdentifierStart(name.charAt(0))) {
        return "Resource name must begin with a letter";
      }

      if (ResourceType.SAMPLE_DATA == type) {
        // SAMPLE_DATA allows for characters that are not valid identifiers. This is ok because these are not compiled by aapt.
        return null;
      }
      for (int i = 1, n = name.length(); i < n; i++) {
        char c = name.charAt(i);
        if (!Character.isJavaIdentifierPart(c)) {
          return String.format("'%1$c' is not a valid resource name character", c);
        }
      }

      return "Resource name '" + name + "' must be a valid Java identifier";
    }

    return null;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  @Nullable
  public String getResourceType() {
    return myResourceType;
  }

  @Nullable
  public ResourceType getType() {
    if (myResourceType == null) {
      return null;
    }
    if (myResourceType.startsWith("+")) {
      assert PLUS_ID.equals(myResourceType) : myResourceType;
      return ResourceType.ID;
    }
    return ResourceType.fromXmlValue(myResourceType);
  }

  @Nullable
  public String getResourceName() {
    return myResourceName;
  }

  /**
   * Returns the package of the resource, or null if not specified.
   */
  @Nullable
  public String getPackage() {
    return myPackage;
  }

  @NotNull
  public String toString() {
    if (myValue != null) {
      return myValue;
    }
    StringBuilder builder = new StringBuilder();
    if (myPrefix != 0) {
      builder.append(myPrefix);
    }
    if (myPackage != null) {
      builder.append(myPackage).append(":");
    }
    if (myResourceType != null) {
      builder.append(myResourceType).append("/");
    }
    builder.append(myResourceName);
    return builder.toString();
  }

  public void setResourceType(@NotNull String resourceType) {
    myResourceType = resourceType;
  }
}
