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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
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
import java.util.stream.Stream;

import static com.android.SdkConstants.TAG_DEEPLINK;

/**
 * Tests for {@link NavigationSchema}.
 */
public class NavigationSchemaTest extends AndroidTestCase {
  private static final String[] LEAF_DESTINATIONS = new String[] {
    "fragment", "fragment_sub", "fragment_sub_sub", "other_1", "other_2"
  };
  private static final String[] DEEPLINK_ONLY = new String[] {"activity", "activity_sub"};
  private static final String[] EMPTIES = new String[] {"include" };
  private static final String[] GROUPS = new String[] {"navigation", "navigation_sub"};
  private static final String[] ALL =
    Stream.concat(
      Stream.concat(
        Stream.concat(
          Arrays.stream(LEAF_DESTINATIONS),
          Arrays.stream(GROUPS)),
        Arrays.stream(EMPTIES)),
      Arrays.stream(DEEPLINK_ONLY)).toArray(String[]::new);

  private static final String PREBUILT_AAR_PATH =
    "../../prebuilts/tools/common/m2/repository/android/arch/navigation/runtime/0.6.0-alpha1/runtime-0.6.0-alpha1.aar";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyDirectoryToProject("navschematest", "src");

    File aar = new File(PathManager.getHomePath(), PREBUILT_AAR_PATH);
    File tempDir = FileUtil.createTempDirectory("NavigationSchemaTest", null);
    ZipUtil.extract(aar, tempDir, null);

    PsiTestUtil.addLibrary(myFixture.getModule(), new File(tempDir, "classes.jar").getPath());
    NavigationSchema.createIfNecessary(myFacet);
  }

  public void testSubtags() {
    NavigationSchema schema = NavigationSchema.get(myFacet);

    // Destination types
    Multimap<Class<? extends AndroidDomElement>, String> subtags;

    Multimap<Class<? extends AndroidDomElement>, String> expected = HashMultimap.create();
    expected.put(NavActionElement.class, NavigationSchema.TAG_ACTION);
    expected.put(DeeplinkElement.class, TAG_DEEPLINK);
    expected.put(ArgumentElement.class, NavigationSchema.TAG_ARGUMENT);
    for (String leaf : LEAF_DESTINATIONS) {
      subtags = schema.getDestinationSubtags(leaf);
      assertEquals(leaf, expected, subtags);
    }

    expected.putAll(NavDestinationElement.class, Arrays.asList(ALL));
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
    assertEquals(expected, schema.getDestinationSubtags(TAG_DEEPLINK));
    expected.put(ArgumentElement.class, NavigationSchema.TAG_ARGUMENT);
    assertEquals(expected, schema.getDestinationSubtags(NavigationSchema.TAG_ACTION));
  }

  public void testDestinationClassByTag() {
    NavigationSchema schema = NavigationSchema.get(myFacet);
    PsiClass activityNavigator = findClass("android.arch.navigation.ActivityNavigator");
    PsiClass fragmentNavigator = findClass("android.arch.navigation.FragmentNavigator");
    PsiClass navGraphNavigator = findClass("android.arch.navigation.NavGraphNavigator");
    PsiClass activityNavigatorSub = findClass("ActivityNavigatorSub");
    PsiClass activityNavigatorSub2 = findClass("ActivityNavigatorSub2");
    PsiClass fragmentNavigatorSub = findClass("FragmentNavigatorSub");
    PsiClass fragmentNavigatorSubSub = findClass("FragmentNavigatorSubSub");
    PsiClass navGraphNavigatorSub = findClass("NavGraphNavigatorSub");
    PsiClass otherNavigator1 = findClass("OtherNavigator1");
    PsiClass otherNavigator2 = findClass("OtherNavigator2");


    assertSameElements(schema.getDestinationClassesByTagSlowly("activity"), ImmutableList.of(activityNavigator));
    assertSameElements(schema.getDestinationClassesByTagSlowly("activity_sub"),
                       ImmutableList.of(activityNavigatorSub, activityNavigatorSub2));
    assertSameElements(schema.getDestinationClassesByTagSlowly("fragment"), ImmutableList.of(fragmentNavigator));
    assertSameElements(schema.getDestinationClassesByTagSlowly("fragment_sub"), ImmutableList.of(fragmentNavigatorSub));
    assertSameElements(schema.getDestinationClassesByTagSlowly("fragment_sub_sub"), ImmutableList.of(fragmentNavigatorSubSub));
    assertSameElements(schema.getDestinationClassesByTagSlowly("navigation"), ImmutableList.of(navGraphNavigator));
    assertSameElements(schema.getDestinationClassesByTagSlowly("navigation_sub"), ImmutableList.of(navGraphNavigatorSub));
    assertSameElements(schema.getDestinationClassesByTagSlowly("other_1"), ImmutableList.of(otherNavigator1));
    assertSameElements(schema.getDestinationClassesByTagSlowly("other_2"), ImmutableList.of(otherNavigator2));
  }

  @NotNull
  private PsiClass findClass(@NotNull String className) {
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    return javaPsiFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
  }

  public void testDestinationType() {
    NavigationSchema schema = NavigationSchema.get(myFacet);
    assertEquals(NavigationSchema.DestinationType.ACTIVITY, schema.getDestinationType("activity"));
    assertEquals(NavigationSchema.DestinationType.ACTIVITY, schema.getDestinationType("activity_sub"));
    assertEquals(NavigationSchema.DestinationType.FRAGMENT, schema.getDestinationType("fragment"));
    assertEquals(NavigationSchema.DestinationType.FRAGMENT, schema.getDestinationType("fragment_sub"));
    assertEquals(NavigationSchema.DestinationType.FRAGMENT, schema.getDestinationType("fragment_sub_sub"));
    assertEquals(NavigationSchema.DestinationType.NAVIGATION, schema.getDestinationType("navigation"));
    assertEquals(NavigationSchema.DestinationType.NAVIGATION, schema.getDestinationType("navigation_sub"));
    assertEquals(NavigationSchema.DestinationType.OTHER, schema.getDestinationType("other_1"));
    assertEquals(NavigationSchema.DestinationType.OTHER, schema.getDestinationType("other_2"));
  }

  public void testTagByType() {
    NavigationSchema schema = NavigationSchema.get(myFacet);
    assertEquals("activity", schema.getDefaultTag(NavigationSchema.DestinationType.ACTIVITY));
    assertEquals("navigation", schema.getDefaultTag(NavigationSchema.DestinationType.NAVIGATION));
    assertEquals("fragment", schema.getDefaultTag(NavigationSchema.DestinationType.FRAGMENT));
  }

  public void testTagLabel() {
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
