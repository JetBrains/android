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

package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for
 * {@link NewTemplateObjectWizard}-derived wizards.
 */
public class TemplateWizardState {
  /*
   * TODO: The parameter handling code needs to be completely rewritten. It's extremely fragile now. When it's rewritten, it needs to take
   * the following into account:
   *
   * Parameters may or may not have corresponding parameters in the template. If they do, they may or may not have default values.
   *
   * Parameters have types, and the conversion between types ought to be fairly transparent, so that you don't have to do all the type
   * conversion you do now -- see the int vs. string problems with API level parameters (convertApisToInt).
   *
   * A parameter can be linked to a UI object. When the UI object is modified by the user, the parameter needs to be updated. Beware of UI
   * changes that happen programatically
   *
   * Some parameters have calculated values that can be overridden by the user.
   *
   * Sometimes we want to clear out parameters or reset their values from defaults.
   *
   * Sometimes we know at wizard step creation time how we want to populate parameters; sometimes we only find that out later. This
   * shouldn't affect how we bind parameters to UI objects.
   *
   * Look at all the places that validate, update, register, and refresh UI, and rationalize. Right now too many times these methods are
   * called as ad-hoc fixes for individual bugs.
   *
   * ConfigureAndroidModuleStep may or may not know its template at construction time.
   *
   * You ought to be able to change a value either by the user changing something in the UI, or programatically updaing something and
   * propagating changes to the UI, without undue hackery. Right now to do the latter we have to set this global disable-changes bit.
   */
  /** Suffix added by default to activity names */
  public static final String ACTIVITY_NAME_SUFFIX = "Activity";
  /** Prefix added to default layout names */
  public static final String LAYOUT_NAME_PREFIX = "activity_";

  /** Template handler responsible for instantiating templates and reading resources */
  protected Template myTemplate;

  /** Configured parameters, by id */
  protected final Map<String, Object> myParameters = new HashMap<String, Object>();

  /** Ids for parameters which should be hidden (because the client wizard already
   * has information for these parameters) */
  protected final Set<String> myHidden = new HashSet<String>();

  /** Ids for parameters which have been modified directly by the user. */
  protected final Set<String> myModified = new HashSet<String>();

  /**
   * Create a new state object for use by the {@link NewTemplatePage}
   */
  public TemplateWizardState() {
    put(TemplateMetadata.ATTR_IS_NEW_PROJECT, false);
    put(TemplateMetadata.ATTR_IS_GRADLE, "true");
  }

  /**
   * Sets a number of parameters that get picked up as globals in the Freemarker templates. These are used to specify the directories where
   * a number of files go. The templates use these globals to allow them to service both old-style Ant builds with the old directory
   * structure and new-style Gradle builds with the new structure.
   */
  protected void populateDirectoryParameters() throws IOException {
    File projectRoot = new File((String)get(NewModuleWizardState.ATTR_PROJECT_LOCATION));
    File moduleRoot = new File(projectRoot, (String)get(NewProjectWizardState.ATTR_MODULE_NAME));
    File mainFlavorSourceRoot = new File(moduleRoot, TemplateWizard.MAIN_FLAVOR_SOURCE_PATH);
    File javaSourceRoot = new File(mainFlavorSourceRoot, TemplateWizard.JAVA_SOURCE_PATH);
    File javaSourcePackageRoot = new File(javaSourceRoot, ((String)get(TemplateMetadata.ATTR_PACKAGE_NAME)).replace('.', '/'));
    File resourceSourceRoot = new File(mainFlavorSourceRoot, TemplateWizard.RESOURCE_SOURCE_PATH);
    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
    put(TemplateMetadata.ATTR_TOP_OUT, projectRoot.getPath());
    put(TemplateMetadata.ATTR_PROJECT_OUT, moduleRoot.getPath());
    put(TemplateMetadata.ATTR_MANIFEST_OUT, mainFlavorSourceRoot.getPath());
    put(TemplateMetadata.ATTR_SRC_OUT, javaSourcePackageRoot.getPath());
    put(TemplateMetadata.ATTR_RES_OUT, resourceSourceRoot.getPath());
    if (mavenUrl != null) {
      put(TemplateMetadata.ATTR_MAVEN_URL, mavenUrl);
    }
  }

  public boolean hasTemplate() {
    return myTemplate != null && myTemplate.getMetadata() != null;
  }

  @Nullable
  public Template getTemplate() {
    return myTemplate;
  }

  @Nullable
  public TemplateMetadata getTemplateMetadata() {
    if (myTemplate == null) {
      return null;
    }
    return myTemplate.getMetadata();
  }

  @Nullable
  public Object get(String key) {
    return myParameters.get(key);
  }

  public boolean hasAttr(String key) {
    return myParameters.containsKey(key);
  }

  public void put(@NotNull String key, @Nullable Object value) {
    myParameters.put(key, value);
  }

  /**
   * Sets the current template
   */
  public void setTemplateLocation(@NotNull File file) {
    if (myTemplate == null || !myTemplate.getRootPath().getAbsolutePath().equals(file.getAbsolutePath())) {
      // Clear out any parameters from the old template and bring in the defaults for the new template.
      if (myTemplate != null && myTemplate.getMetadata() != null) {
        for (Parameter param : myTemplate.getMetadata().getParameters()) {
          myParameters.remove(param.id);
        }
      }
      myTemplate = Template.createFromPath(file);
      setParameterDefaults();
    }
  }

  protected void setParameterDefaults() {
    for (Parameter param : myTemplate.getMetadata().getParameters()) {
      if (!myParameters.containsKey(param.id) && param.initial != null) {
        switch(param.type) {
          case BOOLEAN:
            put(param.id, Boolean.valueOf(param.initial));
            break;
          case ENUM:
          case SEPARATOR:
          case STRING:
            put(param.id, param.initial);
            break;
        }
      }
    }
  }

  public void convertApisToInt() {
    convertToInt(ATTR_MIN_API);
    convertToInt(ATTR_BUILD_API);
    convertToInt(ATTR_MIN_API_LEVEL);
    convertToInt(ATTR_TARGET_API);
  }

  private void convertToInt(String attr) {
    Object value = get(attr);
    if (value != null && !(value instanceof Integer)) {
      put(attr, Integer.parseInt(get(attr).toString()));
    }
  }
}
