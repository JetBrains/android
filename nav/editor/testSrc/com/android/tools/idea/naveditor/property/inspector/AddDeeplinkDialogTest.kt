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
package com.android.tools.idea.naveditor.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavPropertyInfo
import org.mockito.Mockito
import org.mockito.Mockito.mock

class AddDeeplinkDialogTest : NavTestCase() {

  fun testValidation() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }
    AddDeeplinkDialog(null, model.find("fragment1")!!).runAndClose { dialog ->
      dialog.myUriField.text = "http://example.com/foo"
      assertNull(dialog.doValidate())

      dialog.myUriField.text = "http://!@#$"
      assertNull(dialog.doValidate())

      dialog.myUriField.text = "http://example.com/{blah}"
      assertNull(dialog.doValidate())

      dialog.myUriField.text = "http://example.com/{blah"
      assertNotNull(dialog.doValidate())
    }
  }

  fun testInitWithExisting() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          deeplink("deepLink","http://example.com", autoVerify = true)
        }
      }
    }
    val fragment1 = model.find("fragment1")!!
    AddDeeplinkDialog(fragment1.getChild(0), fragment1).runAndClose { dialog ->
      assertEquals("http://example.com", dialog.uri)
      assertTrue(dialog.autoVerify)
    }
  }

  fun testInitWithDefaults() {
    AddDeeplinkDialog(null, mock(NlComponent::class.java)).runAndClose { dialog ->
      assertEquals("", dialog.uri)
      assertFalse(dialog.autoVerify)
    }
  }

  fun testPropertyChangeMetrics() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("f1")
      }
    }

    val f1 = model.find("f1")!!
    AddDeeplinkDialog(null, f1).runAndClose { dialog ->
      dialog.myUriField.text = "http://example.com"
      dialog.myAutoVerify.isSelected = true

      TestNavUsageTracker.create(model).use { tracker ->
        dialog.save()
        Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                           .setType(NavEditorEvent.NavEditorEventType.CHANGE_PROPERTY)
                                           .setPropertyInfo(NavPropertyInfo.newBuilder()
                                                              .setWasEmpty(true)
                                                              .setProperty(NavPropertyInfo.Property.URI)
                                                              .setContainingTag(NavPropertyInfo.TagType.DEEPLINK_TAG))
                                           .setSource(NavEditorEvent.Source.PROPERTY_INSPECTOR).build())
        Mockito.verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                           .setType(NavEditorEvent.NavEditorEventType.CHANGE_PROPERTY)
                                           .setPropertyInfo(NavPropertyInfo.newBuilder()
                                                              .setWasEmpty(true)
                                                              .setProperty(NavPropertyInfo.Property.AUTO_VERIFY)
                                                              .setContainingTag(NavPropertyInfo.TagType.DEEPLINK_TAG))
                                           .setSource(NavEditorEvent.Source.PROPERTY_INSPECTOR).build())
      }
    }
  }

}