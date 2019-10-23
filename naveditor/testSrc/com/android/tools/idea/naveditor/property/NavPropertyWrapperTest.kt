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
package com.android.tools.idea.naveditor.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.*
import com.google.wireless.android.sdk.stats.NavEditorEvent.Source.*
import com.google.wireless.android.sdk.stats.NavPropertyInfo
import com.google.wireless.android.sdk.stats.NavPropertyInfo.Property.*
import com.google.wireless.android.sdk.stats.NavPropertyInfo.TagType.*
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.navigation.NavigationSchema.ATTR_POP_UP_TO
import org.mockito.Mockito.verify

class NavPropertyWrapperTest : NavTestCase() {
  fun testLogging() {
    val model = model("nav.xml") {
      navigation {
        fragment("f1")
      }
    }

    val baseProperty = NlPropertyItem.create(XmlName(ATTR_ID, ANDROID_URI),
                                             AttributeDefinition(ResourceNamespace.ANDROID, ATTR_ID),
                                             listOf(model.find("f1")!!),
                                             null)
    val wrapped = NavPropertyWrapper(baseProperty)
    val baseProperty2 = NlPropertyItem.create(XmlName(ATTR_POP_UP_TO, AUTO_URI),
                                             AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_POP_UP_TO),
                                             listOf(model.find("f1")!!),
                                             null)
    val wrapped2 = NavPropertyWrapper(baseProperty2)

    assertEquals(ATTR_ID, wrapped.name)

    TestNavUsageTracker.create(model).use { tracker ->
      wrapped.setValue("f2")
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(CHANGE_PROPERTY)
                                 .setPropertyInfo(NavPropertyInfo.newBuilder()
                                                    .setContainingTag(FRAGMENT_TAG)
                                                    .setProperty(ID)
                                                    .setWasEmpty(false))
                                 .setSource(PROPERTY_INSPECTOR)
                                 .build())
      wrapped2.setValue("foo")
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(CHANGE_PROPERTY)
                                 .setPropertyInfo(NavPropertyInfo.newBuilder()
                                                    .setContainingTag(FRAGMENT_TAG)
                                                    .setProperty(POP_UP_TO)
                                                    .setWasEmpty(true))
                                 .setSource(PROPERTY_INSPECTOR)
                                 .build())
    }
  }
}