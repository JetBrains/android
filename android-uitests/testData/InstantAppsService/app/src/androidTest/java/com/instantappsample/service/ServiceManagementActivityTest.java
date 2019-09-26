/*
 * Copyright (C) 2019 Google Inc. All Rights Reserved.
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
package com.instantappsample.service;

import android.content.Intent;
import android.net.Uri;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class ServiceManagementActivityTest {

    @Rule
    public IntentsTestRule<ServiceManagementActivity> rule =
            new IntentsTestRule<ServiceManagementActivity>(ServiceManagementActivity.class, true) {
                @Override
                protected Intent getActivityIntent() {
                    return new Intent()
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .setAction(Intent.ACTION_VIEW)
                            .setData(Uri.parse("https://service.instantappsample.com"));
                }
            };

    /**
     * Tests whether the Activity can be launched via its registered URL.
     */
    @Test
    public void isAddressableViaUrl() {
        onView(withId(R.id.text_explanation)).check(matches(isDisplayed()));
    }

    @Test
    public void bindService() {
        onView(withText(R.string.btn_service_bind)).perform(click());
    }

    @Test
    public void unbindService() {
        onView(withText(R.string.btn_service_bind)).perform(click());

        onView(withText(R.string.btn_service_unbind)).perform(click());
    }

    @Test
    public void startService() {
        onView(withText(R.string.btn_service_start)).perform(click());
    }

    @Test
    public void stopService() {
        onView(withText(R.string.btn_service_start)).perform(click());

        onView(withText(R.string.btn_service_stop)).perform(click());
    }

}
