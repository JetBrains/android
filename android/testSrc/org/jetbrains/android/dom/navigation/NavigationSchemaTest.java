/*
 * Copyright (C) 2017 The Android Open Source Project
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
package org.jetbrains.android.dom.navigation;

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.NavTestUtil;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;

import static com.android.SdkConstants.TAG_DEEP_LINK;

/**
 * Tests for {@link NavigationSchema}.
 */
public class NavigationSchemaTest extends AndroidTestCase {
  private static final String[] LEAF_DESTINATIONS = new String[] {
    "fragment", "fragment_sub", "fragment_sub_sub", "other_1", "other_2"
  };
  private static final String[] ACTIVITIES = new String[] {"activity", "activity_sub"};
  private static final String[] EMPTIES = new String[] {"include" };
  private static final String[] GROUPS = new String[] {"navigation", "navigation_sub"};

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject("navschematest", "src");

    for (String prebuiltPath : NavTestUtil.getNavEditorAarPaths()) {
      File aar = new File(PathManager.getHomePath(), prebuiltPath);
      File tempDir = FileUtil.createTempDirectory("NavigationSchemaTest", null);
      ZipUtil.extract(aar, tempDir, null);
      PsiTestUtil.addLibrary(myFixture.getModule(), new File(tempDir, "classes.jar").getPath());
    }
    NavigationSchema.createIfNecessary(myFacet);
  }

  public void testSubtags() {
    NavigationSchema schema = NavigationSchema.get(myFacet);

    // Destination types
    Multimap<Class<? extends AndroidDomElement>, String> subtags;

    Multimap<Class<? extends AndroidDomElement>, String> expected = HashMultimap.create();
    expected.put(DeeplinkElement.class, TAG_DEEP_LINK);
    expected.put(NavArgumentElement.class, NavigationSchema.TAG_ARGUMENT);
    for (String activity : ACTIVITIES) {
      subtags = schema.getDestinationSubtags(activity);
      assertEquals(activity, expected, subtags);
    }
    expected.put(NavActionElement.class, NavigationSchema.TAG_ACTION);
    for (String leaf : LEAF_DESTINATIONS) {
      subtags = schema.getDestinationSubtags(leaf);
      assertEquals(leaf, expected, subtags);
    }

    expected.putAll(NavGraphElement.class, Arrays.asList(GROUPS));
    expected.put(NavGraphElement.class, "include");
    expected.putAll(ConcreteDestinationElement.class, Arrays.asList(LEAF_DESTINATIONS));
    expected.putAll(ConcreteDestinationElement.class, Arrays.asList(ACTIVITIES));
    for (String group : GROUPS) {
      subtags = schema.getDestinationSubtags(group);
      assertEquals(group, expected, subtags);
    }

    for (String empty : EMPTIES) {
      assertTrue(schema.getDestinationSubtags(empty).isEmpty());
    }

    // Non-destination types
    expected.clear();
    assertEquals(expected, schema.getDestinationSubtags(NavigationSchema.TAG_ARGUMENT));
    assertEquals(expected, schema.getDestinationSubtags(TAG_DEEP_LINK));
    expected.put(NavArgumentElement.class, NavigationSchema.TAG_ARGUMENT);
    assertEquals(expected, schema.getDestinationSubtags(NavigationSchema.TAG_ACTION));
  }

  public void testDestinationClassByTag() {
    NavigationSchema schema = NavigationSchema.get(myFacet);
    PsiClass activity = findClass(SdkConstants.CLASS_ACTIVITY);
    PsiClass fragment = findClass(SdkConstants.CLASS_V4_FRAGMENT.oldName());
    PsiClass navGraph = findClass("androidx.navigation.NavGraph");
    // TODO: update custom navs so some have custom destination classes in the release after alpha4

    assertSameElements(schema.getDestinationClassesForTag("activity"), activity);
    assertSameElements(schema.getDestinationClassesForTag("activity_sub"), activity);
    assertSameElements(schema.getDestinationClassesForTag("fragment"), fragment);
    assertSameElements(schema.getDestinationClassesForTag("fragment_sub"), fragment);
    assertSameElements(schema.getDestinationClassesForTag("fragment_sub_sub"), fragment);
    assertSameElements(schema.getDestinationClassesForTag("navigation"), navGraph);
    assertSameElements(schema.getDestinationClassesForTag("navigation_sub"), navGraph);
    assertEmpty(schema.getDestinationClassesForTag("other_1"));
    assertEmpty(schema.getDestinationClassesForTag("other_2"));
  }

  @NotNull
  private PsiClass findClass(@NotNull String className) {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    return javaPsiFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
  }

  public void testDestinationType() {
    // TODO: update custom navs so some have multiple types in the release after alpha4
    NavigationSchema schema = NavigationSchema.get(myFacet);
    assertSameElements(schema.getDestinationTypesForTag("activity"), NavigationSchema.DestinationType.ACTIVITY);
    assertSameElements(schema.getDestinationTypesForTag("activity_sub"), NavigationSchema.DestinationType.ACTIVITY);
    assertSameElements(schema.getDestinationTypesForTag("fragment"), NavigationSchema.DestinationType.FRAGMENT);
    assertSameElements(schema.getDestinationTypesForTag("fragment_sub"), NavigationSchema.DestinationType.FRAGMENT);
    assertSameElements(schema.getDestinationTypesForTag("fragment_sub_sub"), NavigationSchema.DestinationType.FRAGMENT);
    assertSameElements(schema.getDestinationTypesForTag("navigation"), NavigationSchema.DestinationType.NAVIGATION);
    assertSameElements(schema.getDestinationTypesForTag("navigation_sub"), NavigationSchema.DestinationType.NAVIGATION);
    assertSameElements(schema.getDestinationTypesForTag("other_1"), NavigationSchema.DestinationType.OTHER);
    assertSameElements(schema.getDestinationTypesForTag("other_2"), NavigationSchema.DestinationType.OTHER);
  }

  public void testTagByType() {
    // TODO: update custom navs so some have "OTHER" type in the release after alpha4
    NavigationSchema schema = NavigationSchema.get(myFacet);
    assertEquals("activity", schema.getDefaultTag(NavigationSchema.DestinationType.ACTIVITY));
    assertEquals("navigation", schema.getDefaultTag(NavigationSchema.DestinationType.NAVIGATION));
    assertEquals("fragment", schema.getDefaultTag(NavigationSchema.DestinationType.FRAGMENT));
  }

  public void testTagLabel() {
    // TODO: update custom navs so some have multiple types in the release after alpha4
    NavigationSchema schema = NavigationSchema.get(myFacet);
    assertEquals("Activity", schema.getTagLabel("activity"));
    assertEquals("Activity (activity_sub)", schema.getTagLabel("activity_sub"));
    assertEquals("Fragment", schema.getTagLabel("fragment"));
    assertEquals("Fragment (fragment_sub)", schema.getTagLabel("fragment_sub"));
    assertEquals("Fragment (fragment_sub_sub)", schema.getTagLabel("fragment_sub_sub"));
    assertEquals("Nested Graph", schema.getTagLabel("navigation"));
    assertEquals("Nested Graph", schema.getTagLabel("navigation", false));
    assertEquals("Root Graph", schema.getTagLabel("navigation", true));
    assertEquals("Nested Graph (navigation_sub)", schema.getTagLabel("navigation_sub"));
    assertEquals("other_1", schema.getTagLabel("other_1"));
    assertEquals("other_2", schema.getTagLabel("other_2"));
    assertEquals("Include Graph", schema.getTagLabel("include"));
    assertEquals("Action", schema.getTagLabel("action"));
  }
}
