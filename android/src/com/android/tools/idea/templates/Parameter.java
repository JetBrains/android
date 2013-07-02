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
package com.android.tools.idea.templates;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.rendering.ResourceNameValidator;
import com.google.common.base.Splitter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static com.android.tools.idea.templates.Template.*;

/**
 * Parameter represents an external input to a template. It consists of an ID used to refer to it within the template,
 * human-readable information to be displayed in the UI, and type and validation specifications that can be used in the UI to assist in
 * data entry.
 */
public class Parameter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.templates.Parameter");

  public enum Type {
    STRING,
    BOOLEAN,
    ENUM,
    SEPARATOR;
    // TODO: Numbers?

    public static Type get(String name) {
      try {
        return Type.valueOf(name.toUpperCase(Locale.US));
      } catch (IllegalArgumentException e) {
        LOG.error("Unexpected template type '" + name + "'");
        LOG.error("Expected one of :");
        for (Type s : Type.values()) {
          LOG.error("  " + s.name().toLowerCase(Locale.US));
        }
      }

      return STRING;
    }
  }

  /**
   * Constraints that can be applied to a parameter which helps the UI add a
   * validator etc for user input. These are typically combined into a set
   * of constraints via an EnumSet.
   */
  enum Constraint {
    /**
     * This value must be unique. This constraint usually only makes sense
     * when other constraints are specified, such as {@link #LAYOUT}, which
     * means that the parameter should designate a name that does not
     * represent an existing layout resource name
     */
    UNIQUE,

    /**
     * This value must already exist. This constraint usually only makes sense
     * when other constraints are specified, such as {@link #LAYOUT}, which
     * means that the parameter should designate a name that already exists as
     * a resource name.
     */
    EXISTS,

    /** The associated value must not be empty */
    NONEMPTY,

    /** The associated value is allowed to be empty */
    EMPTY,

    /** The associated value should represent a fully qualified activity class name */
    ACTIVITY,

    /** The associated value should represent an API level */
    APILEVEL,

    /** The associated value should represent a valid class name */
    CLASS,

    /** The associated value should represent a valid package name */
    PACKAGE,

    /** The associated value should represent a valid layout resource name */
    LAYOUT,

    /** The associated value should represent a valid drawable resource name */
    DRAWABLE,

    /** The associated value should represent a valid id resource name */
    ID,

    /** The associated value should represent a valid string resource name */
    STRING;

    public static Constraint get(String name) {
      try {
        return Constraint.valueOf(name.toUpperCase(Locale.US));
      } catch (IllegalArgumentException e) {
        LOG.error("Unexpected template constraint '" + name + "'");
        if (name.indexOf(',') != -1) {
          LOG.error("Use | to separate constraints");
        } else {
          LOG.error("Expected one of :");
          for (Constraint s : Constraint.values()) {
            LOG.error("  " + s.name().toLowerCase(Locale.US));
          }
        }
      }

      return NONEMPTY;
    }
  }

  /** The template defining the parameter */
  public final TemplateMetadata template;

  /** The type of parameter */
  @NotNull
  public final Type type;

  /** The unique id of the parameter (not displayed to the user) */
  @Nullable
  public final String id;

  /** The display name for this parameter */
  @Nullable
  public final String name;

  /**
   * The initial value for this parameter (see also {@link #suggest} for more
   * dynamic defaults
   */
  @Nullable
  public final String initial;

  /**
   * A template expression using other template parameters for producing a
   * default value based on other edited parameters, if possible.
   */
  @Nullable
  public final String suggest;

  /** Help for the parameter, if any */
  @Nullable
  public final String help;

  /** The element defining this parameter */
  @NotNull
  public final Element element;

  /** The constraints applicable for this parameter */
  @NotNull
  public final EnumSet<Constraint> constraints;

  Parameter(@NotNull TemplateMetadata template, @NotNull Element parameter) {
    this.template = template;
    element = parameter;

    String typeName = parameter.getAttribute(Template.ATTR_TYPE);
    assert typeName != null && !typeName.isEmpty() : Template.ATTR_TYPE;
    type = Type.get(typeName);

    id = parameter.getAttribute(ATTR_ID);
    initial = parameter.getAttribute(ATTR_DEFAULT);
    suggest = parameter.getAttribute(ATTR_SUGGEST);
    name = parameter.getAttribute(ATTR_NAME);
    help = parameter.getAttribute(ATTR_HELP);
    String constraintString = parameter.getAttribute(ATTR_CONSTRAINTS);
    if (constraintString != null && !constraintString.isEmpty()) {
      EnumSet<Constraint> constraintSet = null;
      for (String s : Splitter.on('|').omitEmptyStrings().split(constraintString)) {
        Constraint constraint = Constraint.get(s);
        if (constraintSet == null) {
          constraintSet = EnumSet.of(constraint);
        } else {
          constraintSet = EnumSet.copyOf(constraintSet);
          constraintSet.add(constraint);
        }
      }
      constraints = constraintSet;
    } else {
      constraints = EnumSet.noneOf(Constraint.class);
    }
  }

  Parameter(
    @NotNull TemplateMetadata template,
    @NotNull Type type,
    @NotNull String id) {
    this.template = template;
    this.type = type;
    this.id = id;
    element = null;
    initial = null;
    suggest = null;
    name = id;
    help = null;
    constraints = EnumSet.noneOf(Constraint.class);
  }

  public List<Element> getOptions() {
    return TemplateUtils.getChildren(element);
  }

  @Nullable
  public String validate(@Nullable Project project, @Nullable String packageName, @Nullable Object value) {
    switch (type) {
      case STRING:
        return validateStringType(project, packageName, value.toString());
      case BOOLEAN:
      case ENUM:
      case SEPARATOR:
      default:
        return null;
    }
  }

  @Nullable
  protected String validateStringType(@Nullable Project project, @Nullable String packageName, @Nullable String value) {
    if (value == null || value.isEmpty()) {
      if (constraints.contains(Constraint.NONEMPTY)) {
        return "Please specify " + name;
      } else {
        return null;
      }
    }
    boolean exists = false;
    String fqName = (packageName != null && value.indexOf('.') == -1 ? packageName + "." : "") + value;

    if (constraints.contains(Constraint.ACTIVITY)) {
      if (!isValidFullyQualifiedJavaIdentifier(fqName)) {
        return name + " is not a valid activity name";
      }
      if (project != null) {
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.projectScope(project));
        PsiClass activityClass = JavaPsiFacade.getInstance(project).findClass(SdkConstants.CLASS_ACTIVITY,
                                                                              GlobalSearchScope.allScope(project));
        exists = aClass != null && activityClass != null && aClass.isInheritor(activityClass, true);
      }
    } else if (constraints.contains(Constraint.APILEVEL)) {
      // TODO: validity check
    } else if (constraints.contains(Constraint.CLASS)) {
      if (!isValidFullyQualifiedJavaIdentifier(fqName)) {
        return name + " is not a valid class name";
      }
      if (project != null) {
        exists = JavaPsiFacade.getInstance(project).findClass(fqName, GlobalSearchScope.projectScope(project)) != null;
      }
    } else if (constraints.contains(Constraint.PACKAGE)) {
      if (!isValidFullyQualifiedJavaIdentifier(value)) {
        return name + " is not a valid package name";
      }
      if (project != null) {
        exists = JavaPsiFacade.getInstance(project).findPackage(value) != null;
      }
    } else if (constraints.contains(Constraint.LAYOUT)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.LAYOUT).getErrorText(value);
      if (resourceNameError != null) {
        return name + " is not a valid resource name. " + resourceNameError;
      }
      exists = existsResourceFile(project, SdkConstants.FD_RES_LAYOUT, value + SdkConstants.DOT_XML);
    } else if (constraints.contains(Constraint.DRAWABLE)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.DRAWABLE).getErrorText(value);
      if (resourceNameError != null) {
        return name + " is not a valid resource name. " + resourceNameError;
      }
      exists = existsResourceFile(project, "drawable", value + ".xml");
    } else if (constraints.contains(Constraint.ID)) {
      // TODO: validity and existence check
    } else if (constraints.contains(Constraint.STRING)) {
      String resourceNameError = ResourceNameValidator.create(false, ResourceFolderType.VALUES).getErrorText(value);
      if (resourceNameError != null) {
        return name + " is not a valid resource name. " + resourceNameError;
      }
      // TODO: Existence check
    }

    if (constraints.contains(Constraint.UNIQUE) && exists) {
      return name + " must be unique";
    } else if (constraints.contains(Constraint.EXISTS) && !exists) {
      return name + " must already exist";
    }
    return null;
  }

  private static boolean isValidFullyQualifiedJavaIdentifier(String value) {
    return AndroidUtils.isValidPackageName(value) && value.indexOf('.') != -1;
  }

  private static boolean existsResourceFile(@Nullable Project project, @NotNull String resourceType, @Nullable String name) {
    if (name == null || name.isEmpty() || project == null) {
      return false;
    }
    for (PsiFile file : FilenameIndex.getFilesByName(project, name, GlobalSearchScope.projectScope(project))) {
      PsiDirectory containingDirectory = file.getContainingDirectory();
      if (containingDirectory != null && containingDirectory.getName().startsWith(resourceType)) {
        return true;
      }
    }
    return false;
  }
}