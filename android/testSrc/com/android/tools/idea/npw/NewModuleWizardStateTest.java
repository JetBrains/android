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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.*;
import com.google.common.collect.Maps;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.templates.RepositoryUrlManager.*;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_CREATE_ACTIVITY;

/**
 *
 */
public class NewModuleWizardStateTest extends AndroidGradleTestCase {

  NewModuleWizardState myState;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myState = new NewModuleWizardState();
  }

  public void testConstruction() throws Exception {
    assertTrue(myState.hasAttr(ATTR_IS_LIBRARY_MODULE));
    assertTrue(myState.hasAttr(ATTR_CREATE_ICONS));
    assertTrue(myState.hasAttr(ATTR_IS_NEW_PROJECT));
    assertTrue(myState.hasAttr(ATTR_IS_NEW_PROJECT));
    assertTrue(myState.hasAttr(ATTR_IS_LAUNCHER));
    assertTrue(myState.hasAttr(ATTR_CREATE_ACTIVITY));

    assertTrue(myState.hasAttr(ATTR_SDK_DIR));

    assertContainsElements(myState.myActivityTemplateState.myHidden,
                           ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API,
                           ATTR_MIN_API_LEVEL, ATTR_TARGET_API, ATTR_BUILD_API, ATTR_BUILD_API_STRING,
                           ATTR_COPY_ICONS, ATTR_IS_LAUNCHER, ATTR_PARENT_ACTIVITY_CLASS,
                           ATTR_ACTIVITY_TITLE, ATTR_TARGET_API_STRING);
  }

  public void testSetTemplateLocation() throws Exception {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(Template.CATEGORY_PROJECTS);

    File androidTemplateFile = null;
    File javaTemplateFile = null;

    for (File template : templates) {
      TemplateMetadata metadata = manager.getTemplateMetadata(template);
      if (metadata.getTitle().equals(TemplateWizardModuleBuilder.MODULE_NAME)) {
        androidTemplateFile = template;
      } else if (metadata.getTitle().equals("Java Library")) {
        javaTemplateFile = template;
      }
    }

    assertNotNull(androidTemplateFile);
    assertNotNull(javaTemplateFile);

    // Check that the android state is set correctly
    myState.setTemplateLocation(androidTemplateFile);
    assertEquals(myState.getTemplate().getRootPath(), androidTemplateFile);

    // Check that the android state is unset correctly
    myState.setTemplateLocation(javaTemplateFile);
    assertEquals(myState.getTemplate().getRootPath(), javaTemplateFile);
  }

  public void testUpdateDependencies() throws Exception {
    LinkedList<String> dependencyList = (LinkedList<String>)myState.get(ATTR_DEPENDENCIES_LIST);
    assertNull(dependencyList);

    // No libs
    myState.put(ATTR_FRAGMENTS_EXTRA, false);
    myState.put(ATTR_ACTION_BAR_EXTRA, false);
    myState.put(ATTR_GRID_LAYOUT_EXTRA, false);

    myState.updateDependencies();

    dependencyList = (LinkedList<String>)myState.get(ATTR_DEPENDENCIES_LIST);
    assertNotNull(dependencyList);
    assertEquals(0, dependencyList.size());

    // All libs (fragments)
    myState.put(ATTR_FRAGMENTS_EXTRA, true);
    myState.put(ATTR_ACTION_BAR_EXTRA, true);
    myState.put(ATTR_GRID_LAYOUT_EXTRA, true);

    myState.updateDependencies();

    dependencyList = (LinkedList<String>)myState.get(ATTR_DEPENDENCIES_LIST);
    assertNotNull(dependencyList);

    assertEquals(3, dependencyList.size());

    RepositoryUrlManager urlManager = RepositoryUrlManager.get();

    assertContainsElements(dependencyList,
                           urlManager.getLibraryCoordinate(SUPPORT_ID_V4),
                           urlManager.getLibraryCoordinate(APP_COMPAT_ID_V7),
                           urlManager.getLibraryCoordinate(GRID_LAYOUT_ID_V7));

    myState.put(ATTR_DEPENDENCIES_LIST, new LinkedList<String>());

    // Support lib (nav drawer)
    myState.put(ATTR_FRAGMENTS_EXTRA, false);
    myState.put(ATTR_ACTION_BAR_EXTRA, false);
    myState.put(ATTR_GRID_LAYOUT_EXTRA, false);
    myState.put(ATTR_NAVIGATION_DRAWER_EXTRA, true);

    myState.updateDependencies();

    dependencyList = (LinkedList<String>)myState.get(ATTR_DEPENDENCIES_LIST);
    assertNotNull(dependencyList);

    assertEquals(1, dependencyList.size());

    assertContainsElements(dependencyList,
                           urlManager.getLibraryCoordinate(SUPPORT_ID_V4));
  }

  public void testUpdateParameters() throws Exception {
    Boolean createIcons = myState.getBoolean(ATTR_CREATE_ICONS);


    myState.updateParameters();

    assertSameForKeys(myState.myParameters, myState.myActivityTemplateState.myParameters,
                      ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                      ATTR_TARGET_API, ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                      ATTR_CREATE_ICONS, ATTR_IS_GRADLE, ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT,
                      ATTR_TARGET_API_STRING, ATTR_BUILD_API_STRING);

    assertEquals(!createIcons, myState.getBoolean(ATTR_COPY_ICONS));
  }

  private void assertSameForKeys(Map<String, Object> m1, Map<String, Object> m2, String... keys) {
    for (String key : keys) {
      assertEquals(m1.get(key), m2.get(key));
    }
  }

  public void testCopyParameters() throws Exception {
    Map<String, Object> from = Maps.newHashMap();
    Map<String, Object> to = Maps.newHashMap();

    from.put("hello", "world");
    from.put("boolean", true);
    from.put("integer", 5);
    from.put("object", new Object());

    assertFalse(from.equals(to));

    String[] keys = new String[] {"hello", "boolean", "integer", "object"};

    myState.copyParameters(from, to, keys);

    assertEquals(from, to);
  }
}
