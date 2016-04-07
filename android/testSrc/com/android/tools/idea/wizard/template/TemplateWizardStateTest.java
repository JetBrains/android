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

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.npw.FormFactorUtils.ATTR_MODULE_NAME;
import static org.mockito.Mockito.*;

/**
 * Tests for the base class that underlies the template wizard states
 */
public class TemplateWizardStateTest extends AndroidGradleTestCase {

  TemplateWizardState myState;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myState = spy(new TemplateWizardState());
  }

  public void testPopulateDirectoryParameters() throws Exception {

    File projectRoot = new File(getProject().getBasePath());
    String moduleName = "app";
    myState.put(ATTR_PROJECT_LOCATION, FileUtil.toSystemIndependentName(projectRoot.getPath()));
    myState.put(ATTR_MODULE_NAME, moduleName);

    // Test 1: Put the package name in and let package location be calculated
    myState.put(ATTR_PACKAGE_NAME, "com.foo.bar");

    myState.populateDirectoryParameters();

    File moduleRoot = new File(projectRoot, moduleName);
    File javaSourceRoot = new File(moduleRoot, FileUtil.toSystemDependentName(myState.getString(ATTR_SRC_DIR)));
    File resSourceRoot = new File(moduleRoot, FileUtil.toSystemDependentName(myState.getString(ATTR_RES_DIR)));
    File packageRoot = new File(javaSourceRoot, FileUtil.toSystemDependentName("com/foo/bar"));
    File mainSourceSet = new File(moduleRoot, FileUtil.toSystemDependentName("src/main"));
    File androidTestRoot = new File(moduleRoot, FileUtil.toSystemDependentName(myState.getString(ATTR_TEST_DIR)));
    File androidTestPackageRoot = new File(androidTestRoot, FileUtil.toSystemDependentName("java/com/foo/bar"));

    assertEquals(FileUtil.toSystemIndependentName(projectRoot.getPath()), myState.getString(ATTR_PROJECT_LOCATION));
    assertEquals(FileUtil.toSystemIndependentName(projectRoot.getPath()), myState.getString(ATTR_TOP_OUT));
    assertEquals(FileUtil.toSystemIndependentName(moduleRoot.getPath()), myState.getString(ATTR_PROJECT_OUT));
    assertEquals(FileUtil.toSystemIndependentName(mainSourceSet.getPath()), myState.getString(ATTR_MANIFEST_OUT));
    assertEquals(FileUtil.toSystemIndependentName(packageRoot.getPath()), myState.getString(ATTR_SRC_OUT));
    assertEquals(FileUtil.toSystemIndependentName(resSourceRoot.getPath()), myState.getString(ATTR_RES_OUT));
    assertEquals(FileUtil.toSystemIndependentName(androidTestPackageRoot.getPath()), myState.getString(ATTR_TEST_OUT));
    assertEquals(moduleName, myState.getString(ATTR_MODULE_NAME));
    assertEquals("com.foo.bar", myState.getString(ATTR_PACKAGE_NAME));

    // Test 2: put the package location in and let the package name be calculated
    myState.myParameters.remove(ATTR_SRC_OUT);
    packageRoot = new File(javaSourceRoot, FileUtil.toSystemDependentName("org/bar/foo"));
    // The java source root must exist in order for getRelativePath to work
    assertTrue(packageRoot.mkdirs());
    myState.put(ATTR_PACKAGE_ROOT, FileUtil.toSystemIndependentName(packageRoot.getPath()));

    myState.populateDirectoryParameters();
    assertEquals(FileUtil.toSystemIndependentName(packageRoot.getPath()), myState.getString(ATTR_SRC_OUT));
    assertEquals("org.bar.foo", myState.getString(ATTR_PACKAGE_NAME));
  }

  public void testBasicMap() throws Exception {
    assertFalse(myState.hasAttr("key"));
    myState.put("key", "value string");
    assertTrue(myState.hasAttr("key"));
    assertTrue(myState.get("key") instanceof String);
    assertEquals("value string", myState.get("key"));

    assertNull(myState.get("doesn't exist"));
  }

  public void testGetBoolean() throws Exception {
    myState.put("boolean", false);
    assertFalse(myState.getBoolean("boolean"));

    boolean ok = false;

    myState.put("not a boolean", new Object());
    try {
      myState.getBoolean("not a boolean");
    } catch (AssertionError e) {
      ok = true;
    }

    assertTrue(ok);

    ok = false;
    try {
      myState.getBoolean("doesn't exist");
    } catch (AssertionError e) {
      ok = true;
    }
    assertTrue(ok);
  }

  public void testGetInt() throws Exception {
    myState.put("int", 5);
    assertEquals(5, myState.getInt("int"));

    myState.put("Integer", Integer.valueOf(5));
    assertEquals(5, myState.getInt("Integer"));

    boolean ok = false;

    myState.put("not an int", new Object());
    try {
      myState.getInt("not an int");
    } catch (AssertionError e) {
      ok = true;
    }

    assertTrue(ok);

    ok = false;
    try {
      myState.getInt("doesn't exist");
    } catch (AssertionError e) {
      ok = true;
    }
    assertTrue(ok);
  }

  public void testGetString() throws Exception {
    myState.put("string", "hello world");
    assertEquals("hello world", myState.getString("string"));

    boolean ok = false;

    myState.put("not a string", new Object());
    try {
      myState.getString("not a string");
    } catch (AssertionError e) {
      ok = true;
    }

    assertTrue(ok);

    ok = false;
    try {
      myState.getString("doesn't exist");
    } catch (AssertionError e) {
      ok = true;
    }
    assertTrue(ok);
  }

  public void testSetTemplateLocation() throws Exception {
    // 3 tests: 1st make sure a new template can be set. 2 check that resetting the same template
    // is a no-op. 3 check that setting to a new template works

    assertNull(myState.myTemplate);
    File templateFile = new File(TemplateManager.getTemplateRootFolder(),
                                 FileUtil.join(Template.CATEGORY_PROJECTS, "NewAndroidProject"));
    myState.setTemplateLocation(templateFile);
    assertFalse(myState.myParameters.isEmpty());
    assertEquals(templateFile, myState.myTemplate.getRootPath());

    myState.setTemplateLocation(templateFile);

    // This should only have occurred once since the 2nd setTemplateLocation should have
    // been a no-op.
    verify(myState, times(1)).setParameterDefaults();
    assertEquals(templateFile, myState.myTemplate.getRootPath());

    File templateFile2 = new File(TemplateManager.getTemplateRootFolder(),
                                  FileUtil.join(Template.CATEGORY_ACTIVITIES, "BlankActivity"));
    myState.setTemplateLocation(templateFile2);

    // This should only have occurred twice since the 2nd setTemplateLocation should have
    // been a no-op.
    verify(myState, times(2)).setParameterDefaults();
    assertEquals(templateFile2, myState.myTemplate.getRootPath());
  }

  public void testConvertApisToInt() throws Exception {
    // Test standard conversion
    myState.put(ATTR_MIN_API, "8");
    myState.put(ATTR_MIN_API_LEVEL, 8);
    myState.put(ATTR_BUILD_API, "19");
    myState.put(ATTR_BUILD_API_STRING, "19");
    myState.put(ATTR_MIN_API_LEVEL, 8);
    myState.put(ATTR_TARGET_API, "19");
    myState.put(ATTR_TARGET_API_STRING, "19");

    Template.convertApisToInt(myState.getParameters());

    assertEquals(8, myState.getInt(ATTR_MIN_API_LEVEL));
    assertEquals("8", myState.get(ATTR_MIN_API));
    assertEquals(19, myState.getInt(ATTR_BUILD_API));
    assertEquals(19, myState.getInt(ATTR_TARGET_API));
    assertEquals("19", myState.get(ATTR_TARGET_API_STRING));
    assertEquals("19", myState.get(ATTR_BUILD_API_STRING));

    List<String> previewNames = Lists.newArrayList("Cupcake", "Donut", "Eclair", "Froyo", "Gingerbread", "Honeycomb", "IceCreamSandwich",
                                                  "JellyBean", "Kitkat");
    int[] apis = new int[] {3, 4, 5, 8, 9, 11, 14, 16, 19};

    // Test API string conversion
    for (int i = 0; i < previewNames.size(); ++i) {
      myState.put(ATTR_TARGET_API, previewNames.get(i));
      Template.convertApisToInt(myState.getParameters());
      assertEquals(apis[i], myState.getInt(ATTR_TARGET_API));
    }
  }
}
