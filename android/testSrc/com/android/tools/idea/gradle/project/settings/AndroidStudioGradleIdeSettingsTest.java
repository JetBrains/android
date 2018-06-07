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
package com.android.tools.idea.gradle.project.settings;

import com.android.tools.idea.gradle.project.settings.AndroidStudioGradleIdeSettings.CurrentTimeProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AndroidStudioGradleIdeSettingsTest {
  @Mock private CurrentTimeProvider myCurrentTimeProvider;

  private AndroidStudioGradleIdeSettings mySettings;

  @Before
  public void setUp() throws Exception {
    initMocks(this);

    mySettings = new AndroidStudioGradleIdeSettings(myCurrentTimeProvider);
  }

  @Test
  public void isEmbeddedMavenRepoEnabledWithTrueAndTimestampNegativeOne() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = true;
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = -1;

    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(6L);

    assertTrue(mySettings.isEmbeddedMavenRepoEnabled());
    assertEquals(6L, mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS);
  }

  @Test
  public void isEmbeddedMavenRepoEnabledWithTrueAndBefore2Weeks() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = true;

    Calendar calendar = new GregorianCalendar();
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = calendar.getTimeInMillis();

    // Add 2 days.
    calendar.add(Calendar.DATE, 2);
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(calendar.getTimeInMillis());

    assertTrue(mySettings.isEmbeddedMavenRepoEnabled());
  }

  @Test
  public void isEmbeddedMavenRepoEnabledWithTrueAndRightAt2Weeks() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = true;

    Calendar calendar = new GregorianCalendar();
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = calendar.getTimeInMillis();

    // Add 14 days.
    calendar.add(Calendar.DATE, 14);
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(calendar.getTimeInMillis());

    assertTrue(mySettings.isEmbeddedMavenRepoEnabled());
  }

  @Test
  public void isEmbeddedMavenRepoEnabledWithTrueAndAfter2Weeks() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = true;

    Calendar calendar = new GregorianCalendar();
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = calendar.getTimeInMillis();

    // Add 15 days.
    calendar.add(Calendar.DATE, 15);
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(calendar.getTimeInMillis());

    assertFalse(mySettings.isEmbeddedMavenRepoEnabled());
  }

  @Test
  public void isEmbeddedMavenRepoDisabled() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = false;
    assertFalse(mySettings.isEmbeddedMavenRepoEnabled());
    verify(myCurrentTimeProvider, never()).getCurrentTimeMillis();
  }

  @Test
  public void setEmbeddedMavenRepoEnabledWithTrue() {
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(6L);
    mySettings.setEmbeddedMavenRepoEnabled(true);
    assertEquals(6L, mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS);
    assertTrue(mySettings.ENABLE_EMBEDDED_MAVEN_REPO);
  }

  @Test
  public void setEmbeddedMavenRepoEnabledWithFalse() {
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = 6L;
    mySettings.setEmbeddedMavenRepoEnabled(false);
    assertEquals(-1, mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS);
    assertFalse(mySettings.ENABLE_EMBEDDED_MAVEN_REPO);
  }
}