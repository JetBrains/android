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
package com.android.tools.profilers.network;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

public class HttpDataTest {
  @Test
  public void responseFieldsStringIsCorrectlySplitAndTrimmed() throws Exception {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("status line =  HTTP/1.1 302 Found \n" +
                              "first=1 \n  second  = 2\n equation=x+y=10");
    HttpData data = builder.build();
    assertThat(data.getResponseField("first"), equalTo("1"));
    assertThat(data.getResponseField("second"), equalTo("2"));
    assertThat(data.getResponseField("equation"), equalTo("x+y=10"));
  }

  @Test
  public void testResponseStatusLineWithKey() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("   \n" +
                              "null  =  HTTP/1.1 302 Found  \n  " +
                              " Content-Type =  text/html; charset=UTF-8;  ");
    HttpData data = builder.build();
    assertThat(data.getStatusCode(), equalTo(302));
    assertThat(data.getResponseField("Content-Type"), equalTo("text/html; charset=UTF-8"));
  }

  @Test
  public void testResponseStatusLineWithoutKey() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("  HTTP/1.1 200 Found  \n  " +
                              " \n   \n  \n" +
                              "  Content-Type =  text/html; charset=UTF-8  ");
    HttpData data = builder.build();
    assertThat(data.getStatusCode(), equalTo(200));
    assertThat(data.getResponseField("Content-Type"), equalTo("text/html; charset=UTF-8"));
  }

  @Test(expected = AssertionError.class)
  public void emptyResponseFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("");
    builder.build();
  }

  @Test(expected = AssertionError.class)
  public void emptyResponseFields2() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("   \n  \n  \n\n   \n  ");
    builder.build();
  }

  @Test(expected = AssertionError.class)
  public void invalidResponseFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("Invalid response fields");
    builder.build();
  }

  @Test
  public void responsePayloadFile() throws Exception {
    HttpData data = new HttpData.Builder(1, 0, 0, 0).build();
    File file = Mockito.mock(File.class);
    data.setResponsePayloadFile(file);
    assertThat(data.getResponsePayloadFile(), equalTo(file));
  }

  @Test
  public void urlNameParsedProperly() {
    String urlString = "www.google.com/l1/l2/test?query=1";
    assertThat(HttpData.getUrlName(urlString), equalTo("test"));
  }

  @Test
  public void urlNameParsedProperlyWithEndingSlash() {
    String urlString = "https://www.google.com/l1/l2/test/";
    assertThat(HttpData.getUrlName(urlString), equalTo("test"));
  }

  @Test
  public void urlNameParsedProperlyWithEmptyPath() {
    String urlString = "https://www.google.com";
    assertThat(HttpData.getUrlName(urlString), equalTo("www.google.com"));
  }

  @Test
  public void urlNameDecoded() {
    String notEncoded = "https://www.google.com/test test";
    try {
      HttpData.getUrlName(notEncoded);
      fail(String.format("Not-encoded URL %s should be invalid.", notEncoded));
    } catch (IllegalArgumentException expected) {}
    String singleEncoded = "https://www.google.com/test%20test";
    assertThat(HttpData.getUrlName(singleEncoded), equalTo("test test"));
    String tripleEncoded = "https://www.google.com/test%252520test";
    assertThat(HttpData.getUrlName(tripleEncoded), equalTo("test test"));
  }

  @Test
  public void urlReturnedAsIsIfUnableToDecode() {
    String url = "https://www.google.com/test%25-2test";
    // Tries 2 times url decoding:
    // 1. test%25-2test -> test%-2test
    // 2. test%-2test -> can't decode -2 so throws an exception
    assertEquals("test%-2test", HttpData.getUrlName(url));
  }

  @Test
  public void testBuilder() {
    long id = 1111;
    long startTime = 10, downloadTime = 100, endTime = 1000;
    HttpData.Builder builder = new HttpData.Builder(id, startTime, endTime, downloadTime);
    builder.setResponseFields("status line =  HTTP/1.1 302 Found \n").setMethod("method")
      .setResponsePayloadId("payloadId").setTrace("trace").setUrl("url");

    HttpData data = builder.build();

    assertThat(data.getId(), equalTo(id));
    assertThat(data.getStartTimeUs(), equalTo(startTime));
    assertThat(data.getDownloadingTimeUs(), equalTo(downloadTime));
    assertThat(data.getEndTimeUs(), equalTo(endTime));

    assertThat(data.getStatusCode(), equalTo(302));
    assertThat(data.getMethod(), equalTo("method"));
    assertThat(data.getResponsePayloadId(), equalTo("payloadId"));
    assertThat(data.getTrace(), equalTo("trace"));
    assertThat(data.getUrl(), equalTo("url"));
  }

  @Test
  public void guessFileExtensionFromContentType() {
    assertEquals(".jpg", HttpData.guessFileExtensionFromContentType("image/jpeg"));
    assertEquals(".json", HttpData.guessFileExtensionFromContentType("application/json"));
    assertEquals(".xml", HttpData.guessFileExtensionFromContentType("application/xml"));
    assertNull(HttpData.guessFileExtensionFromContentType("application/text"));
    assertNull(HttpData.guessFileExtensionFromContentType(""));
  }
}
