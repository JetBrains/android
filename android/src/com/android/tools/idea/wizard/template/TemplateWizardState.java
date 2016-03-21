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

package com.android.tools.idea.wizard.template;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.npw.FormFactorUtils;
import com.android.tools.idea.npw.NewModuleWizardState;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.base.Function;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for
 * NewTemplateObjectWizard-derived wizards.
 *
 * Deprecated. Use {@link ScopedStateStore} and {@link ScopedDataBinder} instead.
 */
@Deprecated
public class TemplateWizardState implements Function<String, Object> {
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
  /**
   * Suffix added by default to activity names
   */
  public static final String ACTIVITY_NAME_SUFFIX = "Activity";
  /**
   * Prefix added to default layout names
   */
  public static final String LAYOUT_NAME_PREFIX = "activity_";

  private static final Logger logger = Logger.getInstance(TemplateWizardState.class);

  /**
   * Template handler responsible for instantiating templates and reading resources
   */
  // TODO: Temporary change. Set to private in a followup CL!
  public Template myTemplate;

  /**
   * Targeted source set
   */
  protected SourceProvider mySourceProvider;

  /**
   * Configured parameters, by id
   */
  // TODO: Temporary change. Set to private in a followup CL!
  public final Map<String, Object> myParameters = new HashMap<String, Object>();

  /**
   * Ids for parameters which should be hidden (because the client wizard already
   * has information for these parameters)
   */
  // TODO: Temporary change. Set to private in a followup CL!
  public final Set<String> myHidden = new HashSet<String>();

  /**
   * Ids for parameters whose values should not change.
   */
  // TODO: Temporary change. Set to private in a followup CL!
  public final Set<String> myFinal = new HashSet<String>();

  /**
   * Ids for parameters which have been modified directly by the user.
   */
  // TODO: Temporary change. Set to private in a followup CL!
  public final Set<String> myModified = new HashSet<String>();

  public TemplateWizardState() {
    put(ATTR_IS_NEW_PROJECT, false);
    put(ATTR_IS_GRADLE, true);
    put(ATTR_IS_LIBRARY_MODULE, false);

    put(ATTR_SRC_DIR, "src/main/java");
    put(ATTR_RES_DIR, "src/main/res");
    put(ATTR_AIDL_DIR, "src/main/aidl");
    put(ATTR_MANIFEST_DIR, "src/main");
    put(ATTR_TEST_DIR, "src/androidTest");
  }

  /**
   * Sets a number of parameters that get picked up as globals in the Freemarker templates. These are used to specify the directories where
   * a number of files go. The templates use these globals to allow them to service both old-style Ant builds with the old directory
   * structure and new-style Gradle builds with the new structure.
   */
  public void populateDirectoryParameters() {
    File projectRoot = new File(getString(NewModuleWizardState.ATTR_PROJECT_LOCATION));
    File moduleRoot = new File(projectRoot, getString(FormFactorUtils.ATTR_MODULE_NAME));
    File mainFlavorSourceRoot = new File(moduleRoot, TemplateWizard.MAIN_FLAVOR_SOURCE_PATH);
    File testSourceRoot = new File(moduleRoot, TemplateWizard.TEST_SOURCE_PATH);

    // Set Res directory if we don't have one
    if (!myParameters.containsKey(ATTR_RES_OUT) || myParameters.get(ATTR_RES_OUT) == null) {
      File resourceSourceRoot = new File(mainFlavorSourceRoot, TemplateWizard.RESOURCE_SOURCE_PATH);
      put(ATTR_RES_OUT, FileUtil.toSystemIndependentName(resourceSourceRoot.getPath()));
    }

    // Set AIDL directory if we don't have one
    if (!myParameters.containsKey(ATTR_AIDL_OUT) || get(ATTR_AIDL_OUT) == null) {
      File aidlRoot = new File(mainFlavorSourceRoot, TemplateWizard.AIDL_SOURCE_PATH);
      put(ATTR_AIDL_OUT, FileUtil.toSystemIndependentName(aidlRoot.getPath()));
    }

    String javaPackageDir = getString(ATTR_PACKAGE_NAME).replace('.', File.separatorChar);
    // Set Src directory if we don't have one
    if (!myParameters.containsKey(ATTR_SRC_OUT) || myParameters.get(ATTR_SRC_OUT) == null) {
      File javaSourceRoot = new File(mainFlavorSourceRoot, TemplateWizard.JAVA_SOURCE_PATH);
      File javaSourcePackageRoot;
      if (myParameters.containsKey(ATTR_PACKAGE_ROOT)) {
        javaSourcePackageRoot = new File(getString(ATTR_PACKAGE_ROOT));
        String relativePath = FileUtil.getRelativePath(javaSourceRoot, javaSourcePackageRoot);
        String javaPackage = relativePath != null ? FileUtil.toSystemIndependentName(relativePath).replace('/', '.') : null;
        put(ATTR_PACKAGE_NAME, javaPackage);
      }
      else {
        javaSourcePackageRoot = new File(javaSourceRoot, javaPackageDir);
      }
      put(ATTR_SRC_OUT, FileUtil.toSystemIndependentName(javaSourcePackageRoot.getPath()));
    }

    // Set Manifest directory if we don't have one
    if (!myParameters.containsKey(ATTR_MANIFEST_OUT) || myParameters.get(ATTR_MANIFEST_OUT) == null) {
      put(ATTR_MANIFEST_OUT, FileUtil.toSystemIndependentName(mainFlavorSourceRoot.getPath()));
    }

    // Set Test directory if we don't have one
    if (!myParameters.containsKey(ATTR_TEST_OUT) || myParameters.get(ATTR_TEST_OUT) == null) {
      String relativeTestOut = FileUtil.join(TemplateWizard.JAVA_SOURCE_PATH, javaPackageDir);
      File testOut = new File(testSourceRoot, relativeTestOut);
      put(ATTR_TEST_OUT, FileUtil.toSystemIndependentName(testOut.getPath()));
    }

    put(ATTR_TOP_OUT, FileUtil.toSystemIndependentName(projectRoot.getPath()));
    put(ATTR_PROJECT_OUT, FileUtil.toSystemIndependentName(moduleRoot.getPath()));

    String mavenUrl = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
    if (mavenUrl != null) {
      put(ATTR_MAVEN_URL, mavenUrl);
    }

    populateRelativePackage(null);
  }

  public void populateRelativePackage(@Nullable Module module) {
    // If the module's application package is "foo.bar", and we're creating an activity
    // in the "foo.bar.baz.bay" package, the package prefix should be ".baz.bay". That lets
    // templates (for example) specify activity names in the manifest relative to the application
    // package node.
    String pkg = (String)myParameters.get(ATTR_PACKAGE_NAME);
    if (pkg != null) {
      if (module != null) {
        String applicationId = MergedManifest.get(module).getPackage();
        if (applicationId != null) {
          if (pkg.startsWith(applicationId) && pkg.length() > applicationId.length() &&
              pkg.charAt(applicationId.length()) == '.') {
            pkg = pkg.substring(applicationId.length());
          }
        }
      }
      else {
        pkg = "";
      }
      put(ATTR_RELATIVE_PACKAGE, pkg);
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
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  public void setSourceProvider(@Nullable SourceProvider provider) {
    mySourceProvider = provider;
  }

  @Nullable
  public TemplateMetadata getTemplateMetadata() {
    if (myTemplate == null) {
      return null;
    }
    return myTemplate.getMetadata();
  }

  public Map<String, Object> getParameters() {
    return myParameters;
  }

  @Nullable
  public Object get(@NotNull String key) {
    return myParameters.get(key);
  }

  public boolean getBoolean(@NotNull String key) {
    return get(key, Boolean.class);
  }

  public int getInt(@NotNull String key) {
    return get(key, Integer.class);
  }

  @NotNull
  public String getString(@NotNull String key) {
    return get(key, String.class);
  }

  @NotNull
  private <T> T get(@NotNull String key, @NotNull Class<T> valueType) {
    Object val = get(key);
    assert valueType.isInstance(val);
    return valueType.cast(val);
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
          if (!myFinal.contains(param.id)) {
            myParameters.remove(param.id);
          }
        }
      }
      myTemplate = Template.createFromPath(file);
      setParameterDefaults();
    }
  }

  public void setParameterDefaults() {
    TemplateMetadata metadata = myTemplate.getMetadata();

    if (metadata == null) {
      logger.warn("Null metadata");
      return;
    }

    for (Parameter param : metadata.getParameters()) {
      if (!myFinal.contains(param.id) && !myParameters.containsKey(param.id) && param.initial != null) {
        assert param.id != null;

        switch (param.type) {
          case BOOLEAN:
            put(param.id, Boolean.valueOf(param.initial));
            break;
          case ENUM:
          case STRING:
            put(param.id, param.initial);
            break;
          case SEPARATOR:
          default:
            break;
        }
      }
    }
  }

  @Override
  public Object apply(String input) {
    return get(input);
  }
}
