/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.stats;

import com.intellij.openapi.util.JDOMUtil;
import junit.framework.TestCase;
import org.jdom.Document;
import org.jdom.Element;

import java.util.Arrays;

public class StudioBuildStatsPersistenceComponentTest extends TestCase {

  private StudioBuildStatsPersistenceComponent myComponent;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myComponent = new StudioBuildStatsPersistenceComponent();
    assertNotNull(myComponent);
  }

  public void testGetInstance() throws Exception {
    // Since this is not an Idea app unit test, getInstance() should return null without crashing.
    assertNull(StudioBuildStatsPersistenceComponent.getInstance());
  }

  public void testNewBuildRecord() {
    BuildRecord b1 = new BuildRecord(
      42,
      new KeyString[] {
        new KeyString("key1", "value 1"),
        new KeyString("key2", "value 2"),
      });

    assertEquals(42, b1.getUtcTimestampMs());
    assertEquals(
      "[KeyString{myKey='key1', myValue='value 1'}, KeyString{myKey='key2', myValue='value 2'}]",
      Arrays.toString(b1.getData()));

    // Test th "simplified" constructor that uses utcNow for the timestamp.
    long now = BuildRecord.utcNow();
    BuildRecord b2 = new BuildRecord("key3", "value 3", "key 4", "value 4");
    // timestamp will have varied, -/+ 5 seconds should be a good enough range of validation
    assertTrue(now - 5000 < b2.getUtcTimestampMs());
    assertTrue(b2.getUtcTimestampMs() < now + 5000);
    assertEquals(
      "[KeyString{myKey='key3', myValue='value 3'}, KeyString{myKey='key 4', myValue='value 4'}]",
      Arrays.toString(b2.getData()));

  }

  public void testAddBuildRecord() throws Exception {
    assertNull(myComponent.getFirstRecord());
    assertEquals(0, myComponent.getRecords().size());

    BuildRecord b1 = new BuildRecord(
        42,
        new KeyString[] {
          new KeyString("key1", "value 1"),
          new KeyString("key2", "value 2"),
        });

    myComponent.addBuildRecordImmediately(b1);

    assertEquals(1, myComponent.getRecords().size());
    assertEquals(b1, myComponent.getRecords().getFirst());
    // get and remove the first record
    assertEquals(b1, myComponent.getFirstRecord());
    assertNull(myComponent.getFirstRecord());
  }

  public void testGetState() throws Exception {
    Element element1 = myComponent.getState();
    assertNotNull(element1);
    assertEquals("<state />", JDOMUtil.writeElement(element1));

    BuildRecord b1 = new BuildRecord(
        42,
        new KeyString[] {
          new KeyString("key1", "value 1"),
          new KeyString("key2", "value 2"),
        });

    BuildRecord b2 = new BuildRecord(
        43,
        new KeyString[] {
          new KeyString("key1", "value 43"),
          new KeyString("key2", "value 44"),
        });

    myComponent.addBuildRecordImmediately(b1);
    myComponent.addBuildRecordImmediately(b2);

    Element element2 = myComponent.getState();
    assertNotNull(element2);
    assertEquals(
      "<state>\n" +
      "  <record utc_ms=\"42\">\n" +
      "    <value key=\"key1\" value=\"value 1\" />\n" +
      "    <value key=\"key2\" value=\"value 2\" />\n" +
      "  </record>\n" +
      "  <record utc_ms=\"43\">\n" +
      "    <value key=\"key1\" value=\"value 43\" />\n" +
      "    <value key=\"key2\" value=\"value 44\" />\n" +
      "  </record>\n" +
      "</state>",
      JDOMUtil.writeElement(element2));
  }

  public void testLoadState() throws Exception {
    assertEquals(0, myComponent.getRecords().size());

    Document doc = JDOMUtil.loadDocument(
      "<state>\n" +
      "  <record utc_ms=\"42\">\n" +
      "    <value key=\"key1\" value=\"value 1\" />\n" +
      "    <value key=\"key2\" value=\"value 2\" />\n" +
      "  </record>\n" +
      "  <record utc_ms=\"43\">\n" +
      "    <value key=\"key1\" value=\"value 43\" />\n" +
      "    <value key=\"key2\" value=\"value 44\" />\n" +
      "  </record>\n" +
      "</state>");
    assertNotNull(doc);

    myComponent.loadState(doc.getRootElement());

    assertEquals(2, myComponent.getRecords().size());


    BuildRecord b1 = new BuildRecord(
        42,
        new KeyString[] {
          new KeyString("key1", "value 1"),
          new KeyString("key2", "value 2"),
        });

    BuildRecord b2 = new BuildRecord(
        43,
        new KeyString[] {
          new KeyString("key1", "value 43"),
          new KeyString("key2", "value 44"),
        });

    assertEquals(b1, myComponent.getFirstRecord());
    assertEquals(b2, myComponent.getFirstRecord());
    assertNull(myComponent.getFirstRecord());
  }
}
