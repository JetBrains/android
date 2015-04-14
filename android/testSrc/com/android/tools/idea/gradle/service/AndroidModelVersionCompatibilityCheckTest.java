/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.service;

import com.android.builder.model.AndroidProject;
import com.android.sdklib.repository.PreciseRevision;
import com.android.tools.idea.gradle.service.AndroidProjectDataService.AndroidModelVersionCompatibilityCheck;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link AndroidModelVersionCompatibilityCheck}.
 */
@RunWith(Parameterized.class)
public class AndroidModelVersionCompatibilityCheckTest {
  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      { "2.4", "1.2.0", true},
      { "2.4", "1.2.2", true},
      { "2.4", "1.3.0", true},
      { "2.4", "1.3.6", true},
      { "2.3.1", "1.0.1", true},
      { "2.3.1", "1.1.3", true},
      { "2.4", "1.0.1", false},
      { "2.4", "1.1.3", false},
      { "2.4.1", "1.0.1", false},
      { "2.4.1", "1.1.3", false},
      { "2.5.0", "1.0.1", false},
      { "2.5.0", "1.1.3", false}
    });
  }

  @NotNull private final AndroidModelVersionCompatibilityCheck myCompatibilityCheck;
  @NotNull private final String myAndroidModelVersion;
  private final boolean myCompatible;

  private AndroidProject myAndroidProject;

  public AndroidModelVersionCompatibilityCheckTest(@NotNull String gradleVersion, @NotNull String androidModelVersion, boolean compatible) {
    myCompatibilityCheck = new AndroidModelVersionCompatibilityCheck(PreciseRevision.parseRevision(gradleVersion));
    myAndroidModelVersion = androidModelVersion;
    myCompatible = compatible;
  }

  @Before
  public void setUp() {
    myAndroidProject = createMock(AndroidProject.class);
  }

  @Test
  public void testIsAndroidModelVersionCompatible() throws Exception {
    expect(myAndroidProject.getModelVersion()).andStubReturn(myAndroidModelVersion);
    replay(myAndroidProject);

    assertEquals(myCompatible, myCompatibilityCheck.isAndroidModelVersionCompatible(myAndroidProject));
    verify(myAndroidProject);
  }
}