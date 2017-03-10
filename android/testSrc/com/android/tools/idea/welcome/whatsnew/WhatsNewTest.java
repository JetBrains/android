/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.welcome.whatsnew;

import com.android.repository.Revision;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link WhatsNew}
 */
public class WhatsNewTest {

  @Test
  public void getMessageToShow() throws Exception{
    WhatsNew wn = new WhatsNew();
    WhatsNew.WhatsNewData data = new WhatsNew.WhatsNewData();
    // Available images are 1.0.0.0, 1.0.0.1
    URI testDataUri = new File(AndroidTestBase.getTestDataPath(), "whatsNew/a").toURI();
    Path dataRoot = Paths.get(testDataUri);
    // no previous version seen, current version > latest available
    String path = wn.getMessageToShow(data, new Revision(1, 0, 1, 0), testDataUri.toURL());
    assertEquals("1.0.0.1.png", dataRoot.relativize(Paths.get(path)).toString());
    assertEquals(0, Revision.parseRevision("1.0.1.0").compareTo(Revision.parseRevision(data.myRevision)));

    // Available images are 1.0.0.0, 1.1.0.0
    testDataUri = new File(AndroidTestBase.getTestDataPath(), "whatsNew/b").toURI();
    dataRoot = Paths.get(testDataUri);

    // no previous version seen, current version == latest available
    path = wn.getMessageToShow(data, new Revision(1, 1, 0, 0), testDataUri.toURL());
    assertEquals("1.1.0.0.png", dataRoot.relativize(Paths.get(path)).toString());
    assertEquals(0, Revision.parseRevision("1.1.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // no previous version seen, current version > latest available
    data.myRevision = null;
    path = wn.getMessageToShow(data, new Revision(1, 1, 1, 1), testDataUri.toURL());
    assertEquals("1.1.0.0.png", dataRoot.relativize(Paths.get(path)).toString());
    assertEquals(0, Revision.parseRevision("1.1.1.1").compareTo(Revision.parseRevision(data.myRevision)));

    // no previous version seen, current version >> latest available
    path = wn.getMessageToShow(data, new Revision(1, 2, 0, 0), testDataUri.toURL());
    assertNull(path);
    assertEquals(0, Revision.parseRevision("1.2.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // previous version seen < current version == latest available
    data.myRevision = "1.0.0.0";
    path = wn.getMessageToShow(data, new Revision(1, 1, 0, 0), testDataUri.toURL());
    assertEquals("1.1.0.0.png", dataRoot.relativize(Paths.get(path)).toString());
    assertEquals(0, Revision.parseRevision("1.1.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // previous version seen == current version == latest available
    data.myRevision = "1.1.0.0";
    path = wn.getMessageToShow(data, new Revision(1, 1, 0, 0), testDataUri.toURL());
    assertNull(path);
    assertEquals(0, Revision.parseRevision("1.1.0.0").compareTo(Revision.parseRevision(data.myRevision)));

    // previous version seen < current version < latest available
    data.myRevision = "1.0.0.0";
    path = wn.getMessageToShow(data, new Revision(1, 1, 1, 1), testDataUri.toURL());
    assertEquals("1.1.0.0.png", dataRoot.relativize(Paths.get(path)).toString());
    assertEquals(0, Revision.parseRevision("1.1.1.1").compareTo(Revision.parseRevision(data.myRevision)));
  }
}
