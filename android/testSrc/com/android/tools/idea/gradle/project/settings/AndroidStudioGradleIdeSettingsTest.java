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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

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

    Instant now = Instant.now();
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = now.toEpochMilli();

    // Add 2 days.
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(now.plus(Duration.ofDays(2)).toEpochMilli());

    assertTrue(mySettings.isEmbeddedMavenRepoEnabled());
  }

  @Test
  public void isEmbeddedMavenRepoEnabledWithTrueAndRightAt2Weeks() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = true;

    Instant now = Instant.now();
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = now.toEpochMilli();

    // Add 14 days.
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(now.plus(Duration.ofDays(14)).toEpochMilli());

    assertTrue(mySettings.isEmbeddedMavenRepoEnabled());
  }

  @Test
  public void isEmbeddedMavenRepoEnabledWithTrueAndAfter2Weeks() {
    mySettings.ENABLE_EMBEDDED_MAVEN_REPO = true;

    Instant now = Instant.now();
    mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS = now.toEpochMilli();

    // Add 15 days.
    when(myCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(now.plus(Duration.ofDays(15)).toEpochMilli());

    try {
      assertFalse("Expected embedded repo to be disabled.", mySettings.isEmbeddedMavenRepoEnabled());
    }
    catch (AssertionError e) {
      // See b/73920264.
      throw new AssertionError(
        String.format(
          "ENABLE_EMBEDDED_MAVEN_REPO: %s, EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS: %s, time from provider: %d, daysPassed: %d",
          mySettings.ENABLE_EMBEDDED_MAVEN_REPO,
          mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS,
          myCurrentTimeProvider.getCurrentTimeMillis(),
          TimeUnit.MILLISECONDS.toDays(myCurrentTimeProvider.getCurrentTimeMillis() - mySettings.EMBEDDED_MAVEN_REPO_ENABLED_TIMESTAMP_MILLIS)),
        e);
    }
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
