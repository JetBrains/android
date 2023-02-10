/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.analytics

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_DEEP_LINK
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.isArgument
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.google.wireless.android.sdk.stats.NavActionInfo
import com.google.wireless.android.sdk.stats.NavDestinationInfo
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavPropertyInfo
import com.google.wireless.android.sdk.stats.NavSchemaInfo
import com.google.wireless.android.sdk.stats.NavigationContents
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.Executor
import java.util.function.Consumer

class NavLogEventTest : NavTestCase() {

  fun testLog() {
    val tracker = mock(NavUsageTracker::class.java)
    NavLogEvent(NavEditorEvent.NavEditorEventType.ACTIVATE_INCLUDE, tracker).log()
    verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_INCLUDE).build())
  }

  fun testActionInfo() {
    val model = model("nav.xml") {
      navigation {
        action("global1", destination = "f2")
        fragment("f1") {
          action("regular1", destination = "f3")
        }
        fragment("f2") {
          action("duplicate1", destination = "f1")
          action("duplicate2", destination = "f1")
          action("regular2", destination = "f3")
        }
        fragment("f3") {
          action("self1", destination = "f3")
          action("regular3", destination = "subnav")
        }
        navigation("subnav") {
          action("global2", destination = "f4")
          action("global3", destination = "f1")
          fragment("f4") {
            action("exit1", destination = "f2")
          }
        }
      }
    }

    validateActionInfo(model, "global2", NavActionInfo.newBuilder()
      .setType(NavActionInfo.ActionType.GLOBAL)
      .setCountFromSource(2)
      .setCountToDestination(1)
      .setCountSame(1).build())

    validateActionInfo(model, "duplicate1", NavActionInfo.newBuilder()
      .setType(NavActionInfo.ActionType.REGULAR)
      .setCountFromSource(3)
      .setCountToDestination(3)
      .setCountSame(2).build())

    validateActionInfo(model, "self1", NavActionInfo.newBuilder()
      .setType(NavActionInfo.ActionType.SELF)
      .setCountFromSource(2)
      .setCountToDestination(3)
      .setCountSame(1).build())

    validateActionInfo(model, "exit1", NavActionInfo.newBuilder()
      .setType(NavActionInfo.ActionType.EXIT)
      .setCountFromSource(1)
      .setCountToDestination(2)
      .setCountSame(1).build())
  }

  private fun validateActionInfo(model: NlModel, actionId: String, expected: NavActionInfo) {
    val tracker = mock(NavUsageTracker::class.java)
    val proto = NavLogEvent(NavEditorEvent.NavEditorEventType.UNKNOWN_EVENT_TYPE, tracker)
      .withActionInfo(model.find(actionId)!!)
      .getProtoForTest().actionInfo
    assertEquals(expected, proto)
  }

  fun testDestinationInfo() {
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomdestination\")\n" +
                         "public class CustomNavigator extends Navigator<CustomNavigator.Destination> {\n" +
                         "  public static class Destination extends NavDestination {}\n" +
                         "}\n")
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomactivity\")\n" +
                         "public class CustomActivityNavigator extends ActivityNavigator {}\n")
    }

    val model = model("nav.xml") {
      navigation {
        fragment("f1", name = "bar")
        activity("a1", layout = "foo")
        custom("mycustomdestination", id = "custom1", layout = "foo", name = "bar")
        custom("mycustomactivity", id = "customactivity")
      }
    }

    validateDestinationInfo(model, "f1", NavDestinationInfo.newBuilder()
      .setType(NavDestinationInfo.DestinationType.FRAGMENT)
      .setHasClass(true).build())

    validateDestinationInfo(model, "a1", NavDestinationInfo.newBuilder()
      .setType(NavDestinationInfo.DestinationType.ACTIVITY)
      .setHasLayout(true).build())

    validateDestinationInfo(model, "custom1", NavDestinationInfo.newBuilder()
      .setType(NavDestinationInfo.DestinationType.OTHER)
      .setHasLayout(true)
      .setHasClass(true).build())

    validateDestinationInfo(model, "customactivity", NavDestinationInfo.newBuilder()
      .setType(NavDestinationInfo.DestinationType.ACTIVITY).build())
  }

  private fun validateDestinationInfo(model: NlModel, destinationId: String, expected: NavDestinationInfo) {
    val tracker = mock(NavUsageTracker::class.java)
    val proto = NavLogEvent(NavEditorEvent.NavEditorEventType.UNKNOWN_EVENT_TYPE, tracker)
      .withDestinationInfo(model.find(destinationId)!!)
      .getProtoForTest().destinationInfo
    assertEquals(expected, proto)
  }

  fun testNavigationContents() {
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomdestination\")\n" +
                         "public class CustomNavigator extends Navigator<CustomNavigator.Destination> {\n" +
                         "  public static class Destination extends NavDestination {}\n" +
                         "}\n")
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomactivity\")\n" +
                         "public class CustomActivityNavigator extends ActivityNavigator {}\n")
    }

    val model = model("nav.xml") {
      navigation {
        activity("a1", layout = "foo")
        custom("mycustomdestination", id = "custom1", layout = "foo", name = "bar")
        custom("mycustomactivity", id = "customactivity")
        action("global1", destination = "f2")
        fragment("f1", name = "bar") {
          action("regular1", destination = "f3")
        }
        fragment("f2") {
          action("duplicate1", destination = "f1")
          action("duplicate2", destination = "f1")
          action("regular2", destination = "f3")
        }
        fragment("f3") {
          action("self1", destination = "f3")
          action("regular3", destination = "subnav")
        }
        include("navigation")
        navigation("subnav") {
          action("global2", destination = "f4")
          action("global3", destination = "f1")
          fragment("f4", name = "bar") {
            action("exit1", destination = "f2")
          }
        }
      }
    }

    val tracker = NavUsageTrackerImpl(mock(Executor::class.java), model, Consumer { })
    val proto = NavLogEvent(NavEditorEvent.NavEditorEventType.UNKNOWN_EVENT_TYPE, tracker)
      .withNavigationContents()
      .getProtoForTest().contents
    assertEquals(NavigationContents.newBuilder()
                   .setActivities(2)
                   .setFragments(4)
                   .setCustomDestinations(1)
                   .setExitActions(1)
                   .setGlobalActions(3)
                   .setIncludes(1)
                   .setNestedGraphs(1)
                   .setPlaceholders(4)
                   .setRegularActions(5)
                   .setSelfActions(1).toString(), proto.toString())

  }

  fun testPropertyInfo() {
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.addClass("import androidx.navigation.*;\n" +
                         "\n" +
                         "@Navigator.Name(\"mycustomactivity\")\n" +
                         "public class CustomActivityNavigator extends ActivityNavigator {}\n")
    }

    val model = model("nav.xml") {
      navigation {
        custom("mycustomactivity", id = "customactivity") {
          deeplink("deepLink", "http://example.com")
        }
        custom("mycustomdestination", id = "customdestination")
        fragment("f1") {
          argument("arg1")
        }
      }
    }

    validatePropertyInfo(ATTR_ID, ANDROID_URI, false, model.find("f1")!!,
                         NavPropertyInfo.newBuilder()
                           .setContainingTag(NavPropertyInfo.TagType.FRAGMENT_TAG)
                           .setProperty(NavPropertyInfo.Property.ID)
                           .setWasEmpty(false).build())

    validatePropertyInfo("foo", AUTO_URI, false, model.find("f1")!!,
                         NavPropertyInfo.newBuilder()
                           .setContainingTag(NavPropertyInfo.TagType.FRAGMENT_TAG)
                           .setProperty(NavPropertyInfo.Property.CUSTOM)
                           .setWasEmpty(false).build())

    validatePropertyInfo(ATTR_NAME, ANDROID_URI, true, model.find("customactivity")!!,
                         NavPropertyInfo.newBuilder()
                           .setContainingTag(NavPropertyInfo.TagType.ACTIVITY_TAG)
                           .setProperty(NavPropertyInfo.Property.NAME)
                           .setWasEmpty(true).build())

    validatePropertyInfo(ATTR_LABEL, ANDROID_URI, true, model.find("customdestination")!!,
                         NavPropertyInfo.newBuilder()
                           .setContainingTag(NavPropertyInfo.TagType.CUSTOM_TAG)
                           .setProperty(NavPropertyInfo.Property.LABEL)
                           .setWasEmpty(true).build())

    validatePropertyInfo(NavigationSchema.ATTR_DEFAULT_VALUE, AUTO_URI, false, model.find { it.isArgument }!!,
                         NavPropertyInfo.newBuilder()
                           .setContainingTag(NavPropertyInfo.TagType.ARGUMENT_TAG)
                           .setProperty(NavPropertyInfo.Property.DEFAULT_VALUE)
                           .setWasEmpty(false).build())

    validatePropertyInfo(ATTR_URI, AUTO_URI, false, model.find { it.tagName == TAG_DEEP_LINK }!!,
                         NavPropertyInfo.newBuilder()
                           .setContainingTag(NavPropertyInfo.TagType.DEEPLINK_TAG)
                           .setProperty(NavPropertyInfo.Property.URI)
                           .setWasEmpty(false).build())
  }

  private fun validatePropertyInfo(propertyName: String,
                                   namespace: String,
                                   wasEmpty: Boolean,
                                   component: NlComponent,
                                   expected: NavPropertyInfo) {
    val tracker = NavUsageTrackerImpl(mock(Executor::class.java), component.model, Consumer { })
    val proto = NavLogEvent(NavEditorEvent.NavEditorEventType.UNKNOWN_EVENT_TYPE, tracker)
      .withAttributeInfo(propertyName, component.tagName, wasEmpty)
      .getProtoForTest().propertyInfo
    assertEquals(expected, proto)
  }

  fun testSchemaInfo() {
    myFixture.addClass("import androidx.navigation.*;\n" +
                       "\n" +
                       "@Navigator.Name(\"mycustomdestination\")\n" +
                       "public class CustomNavigator extends Navigator<CustomNavigator.Destination> {\n" +
                       "  public static class Destination extends NavDestination {}\n" +
                       "}\n")
    myFixture.addClass("import androidx.navigation.*;\n" +
                       "\n" +
                       "@Navigator.Name(\"mycustomactivity\")\n" +
                       "public class CustomActivityNavigator extends ActivityNavigator {}\n")
    myFixture.addClass("import androidx.navigation.*;\n" +
                       "import androidx.navigation.fragment.*;\n" +
                       "\n" +
                       "@Navigator.Name(\"fragment\")\n" +
                       "public class CustomFragmentNavigator extends FragmentNavigator {}\n")

    StudioResourceRepositoryManager.getInstance(myFacet).resetAllCaches()
    waitForResourceRepositoryUpdates();

    val model = model("nav.xml") {
      navigation {
        custom("mycustomactivity")
        custom("mycustomdestination")
      }
    }
    val tracker = NavUsageTrackerImpl(mock(Executor::class.java), model, Consumer { })
    val proto = NavLogEvent(NavEditorEvent.NavEditorEventType.UNKNOWN_EVENT_TYPE, tracker)
      .withSchemaInfo()
      .getProtoForTest().schemaInfo
    assertEquals(NavSchemaInfo.newBuilder()
                   .setCustomDestinations(1)
                   .setCustomNavigators(3)
                   .setCustomTags(2).toString(), proto.toString())
  }
}