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

import com.google.common.collect.ImmutableMap;
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
  public void testResponseStatusLine() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("   \n" +
                              "null  =  HTTP/1.1 302 Found  \n  " +
                              " Content-Type =  text/html; charset=UTF-8;  ");
    HttpData data = builder.build();
    assertThat(data.getStatusCode(), equalTo(302));
    assertThat(data.getResponseField("content-type"), equalTo("text/html; charset=UTF-8"));
  }

  @Test
  public void testResponseStatusLineWithoutKey() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("  HTTP/1.1 200 Found  \n  " +
                              " \n   \n  \n" +
                              "  Content-Type =  text/html; charset=UTF-8  ");
    HttpData data = builder.build();
    assertThat(data.getStatusCode(), equalTo(200));
    assertThat(data.getResponseField("content-type"), equalTo("text/html; charset=UTF-8"));
  }

  @Test
  public void emptyResponseFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("");
    assertEquals(-1, builder.build().getStatusCode());
  }

  @Test
  public void emptyResponseFields2() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("   \n  \n  \n\n   \n  ");
    assertEquals(-1, builder.build().getStatusCode());
  }

  @Test(expected = AssertionError.class)
  public void invalidResponseFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("Invalid response fields");
    builder.build();
  }

  @Test
  public void emptyRequestFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setRequestFields("");
    assertTrue(builder.build().getRequestHeaders().isEmpty());
  }

  @Test
  public void testSetRequestFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setRequestFields("\nfirst=1 \n  second  = 2\n equation=x+y=10");
    ImmutableMap<String, String> requestFields = builder.build().getRequestHeaders();
    assertEquals(3, requestFields.size());
    assertEquals("1", requestFields.get("first"));
    assertEquals("2", requestFields.get("second"));
    assertEquals("x+y=10", requestFields.get("equation"));
  }

  @Test
  public void responsePayloadFile() throws Exception {
    HttpData data = new HttpData.Builder(1, 0, 0, 0).build();
    File file = Mockito.mock(File.class);
    data.setResponsePayloadFile(file);
    assertThat(data.getResponsePayloadFile(), equalTo(file));
  }

  @Test
  public void urlNameWithQueryParsedProperly() {
    String urlString = "www.google.com/l1/l2/test?query=1&other_query=2";
    assertThat(HttpData.getUrlName(urlString), equalTo("test?query=1&other_query=2"));
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
    } catch (IllegalArgumentException ignored) {}
    String singleEncoded = "https://www.google.com/test%20test";
    assertThat(HttpData.getUrlName(singleEncoded), equalTo("test test"));
    String tripleEncoded = "https://www.google.com/test%252520test";
    assertThat(HttpData.getUrlName(tripleEncoded), equalTo("test test"));
  }

  @Test
  public void uryQueryDecoded() {
    String tripleEncoded = "https://www.google.com/test?query1%25253DHello%252520World%252526query2%25253D%252523Goodbye%252523";
    assertThat(HttpData.getUrlName(tripleEncoded), equalTo("test?query1=Hello World&query2=#Goodbye#"));
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
    String trace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";

    HttpData.Builder builder = new HttpData.Builder(id, startTime, endTime, downloadTime);
    builder.setResponseFields("status line =  HTTP/1.1 302 Found \n").setMethod("method")
      .setResponsePayloadId("payloadId").setTrace(trace).setUrl("url");
    builder.addJavaThread(new HttpData.JavaThread(1, "myThread"));
    HttpData data = builder.build();

    assertThat(data.getId(), equalTo(id));
    assertThat(data.getStartTimeUs(), equalTo(startTime));
    assertThat(data.getDownloadingTimeUs(), equalTo(downloadTime));
    assertThat(data.getEndTimeUs(), equalTo(endTime));

    assertThat(data.getStatusCode(), equalTo(302));
    assertThat(data.getMethod(), equalTo("method"));
    assertThat(data.getResponsePayloadId(), equalTo("payloadId"));
    assertThat(data.getStackTrace().getTrace(), equalTo(trace));
    assertThat(data.getUrl(), equalTo("url"));
    assertThat(data.getJavaThreads().get(0).getId(), equalTo(1L));
    assertThat(data.getJavaThreads().get(0).getName(), equalTo("myThread"));
  }

  @Test
  public void guessFileExtensionFromContentType() {
    assertEquals(".html", new HttpData.ContentType("text/html").guessFileExtension());
    assertEquals(".jpg", new HttpData.ContentType("image/jpeg").guessFileExtension());
    assertEquals(".json", new HttpData.ContentType("application/json").guessFileExtension());
    assertEquals(".xml", new HttpData.ContentType("application/xml").guessFileExtension());
    assertNull(new HttpData.ContentType("application/text").guessFileExtension());
    assertNull(new HttpData.ContentType("").guessFileExtension());
  }

  @Test
  public void getMimeTypeFromContentType() {
    assertEquals("text/html", new HttpData.ContentType("text/html; charset=utf-8").getMimeType());
    assertEquals("text/html", new HttpData.ContentType("text/html").getMimeType());
    assertEquals("text/html", new HttpData.ContentType("text/html;").getMimeType());
    assertEquals("", new HttpData.ContentType("").getMimeType());
  }

  @Test
  public void getContentLengthFromLowerCaseData() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("CoNtEnt-LEngtH = 10000 \n  response-status-code = 200");
    HttpData data = builder.build();
    assertEquals("10000", data.getResponseField("content-length"));
    assertEquals("10000", data.getResponseField("cOnTenT-leNGth"));
  }

  @Test
  public void getStatusCodeFromFields() {
    HttpData.Builder builder = new HttpData.Builder(1, 0, 0, 0);
    builder.setResponseFields("content-length = 10000 \n  response-status-code = 200");
    HttpData data = builder.build();
    assertEquals(200, data.getStatusCode());
  }
}
