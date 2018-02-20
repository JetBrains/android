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
package com.android.tools.idea.whatsnew.assistant;


import com.android.repository.Revision;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link WhatsNew}
 */
public class WhatsNewTest {

  @Test
  public void getMessageToShow() {
    WhatsNew wn = new WhatsNew();
    WhatsNew.WhatsNewData data = new WhatsNew.WhatsNewData();

    // no previous version seen, current version > latest available
    assertTrue(wn.shouldShowMessage(data, new Revision(1, 0, 1, 0)));
    assertEquals(1, Revision.parseRevision("1.1.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // previous version seen < current version == latest available
    data.myRevision = "1.0.0.0";
    assertTrue(wn.shouldShowMessage(data, new Revision(1, 1, 0, 0)));
    assertEquals(0, Revision.parseRevision("1.1.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // previous version seen == current version == latest available
    data.myRevision = "1.1.0.0";
    assertFalse(wn.shouldShowMessage(data, new Revision(1, 1, 0, 0)));
    assertEquals(0, Revision.parseRevision("1.1.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // previous version seen < current version < latest available
    data.myRevision = "1.0.0.0";
    assertTrue(wn.shouldShowMessage(data, new Revision(1, 1, 1, 1)));
    assertEquals(0, Revision.parseRevision("1.1.1.1").compareTo(Revision.parseRevision(data.myRevision)));
  }
}
