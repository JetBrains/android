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

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestProjectPaths;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.android.dom.AndroidDomElement;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Tests for {@link NavigationSchema}.
 */
public class NavigationSchemaTest extends AndroidGradleTestCase {
  private static final String[] LEAVES = new String[] {
    "fragment", "activity", "fragment_sub", "activity_sub", "fragment_sub_sub", "other_1", "other_2"
  };
  private static final String[] GROUPS = new String[] {"navigation", "navigation_sub"};
  private static final String[] ALL = Stream.concat(Arrays.stream(LEAVES), Arrays.stream(GROUPS)).toArray(String[]::new);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject(TestProjectPaths.NAVIGATION_EDITOR_SCHEMA_TEST);
    requestSyncAndWait();
  }

  public void testSubtags() {
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(myAndroidFacet);
    Multimap<Class<? extends AndroidDomElement>, String> subtags;

    Multimap<Class<? extends AndroidDomElement>, String> expected = HashMultimap.create();
    expected.put(NavActionElement.class, "action");
    for (String leaf : LEAVES) {
      subtags = schema.getDestinationSubtags(leaf);
      assertEquals(leaf, expected, subtags);
    }

    expected.putAll(NavDestinationElement.class, Arrays.asList(ALL));
    for (String group : GROUPS) {
      subtags = schema.getDestinationSubtags(group);
      assertEquals(group, expected, subtags);
    }
  }

  public void testDestinationClassByTag() {
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(myAndroidFacet);
    assertEquals("ActivityNavigator", schema.getDestinationClassByTag("activity").getName());
    assertEquals("ActivityNavigatorSub", schema.getDestinationClassByTag("activity_sub").getName());
    assertEquals("FragmentNavigator", schema.getDestinationClassByTag("fragment").getName());
    assertEquals("FragmentNavigatorSub", schema.getDestinationClassByTag("fragment_sub").getName());
    assertEquals("FragmentNavigatorSubSub", schema.getDestinationClassByTag("fragment_sub_sub").getName());
    assertEquals("NavGraphNavigator", schema.getDestinationClassByTag("navigation").getName());
    assertEquals("NavGraphNavigatorSub", schema.getDestinationClassByTag("navigation_sub").getName());
    assertEquals("OtherNavigator1", schema.getDestinationClassByTag("other_1").getName());
    assertEquals("OtherNavigator2", schema.getDestinationClassByTag("other_2").getName());
  }

  public void testDestinationType() {
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(myAndroidFacet);
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
    NavigationSchema schema = NavigationSchema.getOrCreateSchema(myAndroidFacet);
    assertEquals("activity", schema.getTag(NavigationSchema.DestinationType.ACTIVITY));
    assertEquals("navigation", schema.getTag(NavigationSchema.DestinationType.NAVIGATION));
    assertEquals("fragment", schema.getTag(NavigationSchema.DestinationType.FRAGMENT));
  }
}
