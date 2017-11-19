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

import com.android.tools.adtui.model.Range;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf3jarjar.ByteString;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class HttpDataTest {
  @Test
  public void responseFieldsStringIsCorrectlySplitAndTrimmed() throws Exception {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("status line =  HTTP/1.1 302 Found \n" +
                              "first=1 \n  second  = 2\n equation=x+y=10");
    HttpData data = builder.build();
    assertThat(data.getResponseField("first")).isEqualTo("1");
    assertThat(data.getResponseField("second")).isEqualTo("2");
    assertThat(data.getResponseField("equation")).isEqualTo("x+y=10");
  }

  @Test
  public void testResponseStatusLine() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("   \n" +
                              "null  =  HTTP/1.1 302 Found  \n  " +
                              " Content-Type =  text/html; charset=UTF-8;  ");
    HttpData data = builder.build();
    assertThat(data.getStatusCode()).isEqualTo(302);
    assertThat(data.getResponseField("content-type")).isEqualTo("text/html; charset=UTF-8");
  }

  @Test
  public void testResponseStatusLineWithoutKey() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("  HTTP/1.1 200 Found  \n  " +
                              " \n   \n  \n" +
                              "  Content-Type =  text/html; charset=UTF-8  ");
    HttpData data = builder.build();
    assertThat(data.getStatusCode()).isEqualTo(200);
    assertThat(data.getResponseField("content-type")).isEqualTo("text/html; charset=UTF-8");
  }

  @Test
  public void emptyResponseFields() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("");
    assertThat(builder.build().getStatusCode()).isEqualTo(-1);
  }

  @Test
  public void emptyResponseFields2() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("   \n  \n  \n\n   \n  ");
    assertThat(builder.build().getStatusCode()).isEqualTo(-1);
  }

  @Test(expected = AssertionError.class)
  public void invalidResponseFields() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("Invalid response fields");
    builder.build();
  }

  @Test
  public void emptyRequestFields() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setRequestFields("");
    assertThat(builder.build().getRequestHeaders()).isEmpty();
  }

  @Test
  public void testSetRequestFields() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setRequestFields("\nfirst=1 \n  second  = 2\n equation=x+y=10");
    ImmutableMap<String, String> requestFields = builder.build().getRequestHeaders();
    assertThat(requestFields.size()).isEqualTo(3);
    assertThat(requestFields.get("first")).isEqualTo("1");
    assertThat(requestFields.get("second")).isEqualTo("2");
    assertThat(requestFields.get("equation")).isEqualTo("x+y=10");
  }

  @Test
  public void urlNameWithQueryParsedProperly() {
    String urlString = "www.google.com/l1/l2/test?query=1&other_query=2";
    assertThat(HttpData.getUrlName(urlString)).isEqualTo("test?query=1&other_query=2");
  }

  @Test
  public void urlNameParsedProperlyWithEndingSlash() {
    String urlString = "https://www.google.com/l1/l2/test/";
    assertThat(HttpData.getUrlName(urlString)).isEqualTo("test");
  }

  @Test
  public void urlNameParsedProperlyWithEmptyPath() {
    String urlString = "https://www.google.com";
    assertThat(HttpData.getUrlName(urlString)).isEqualTo("www.google.com");
  }

  @Test
  public void urlNameDecoded() {
    String notEncoded = "https://www.google.com/test test";
    try {
      HttpData.getUrlName(notEncoded);
      fail(String.format("Not-encoded URL %s should be invalid.", notEncoded));
    } catch (IllegalArgumentException ignored) {}
    String singleEncoded = "https://www.google.com/test%20test";
    assertThat(HttpData.getUrlName(singleEncoded)).isEqualTo("test test");
    String tripleEncoded = "https://www.google.com/test%252520test";
    assertThat(HttpData.getUrlName(tripleEncoded)).isEqualTo("test test");
  }

  @Test
  public void uryQueryDecoded() {
    String tripleEncoded = "https://www.google.com/test?query1%25253DHello%252520World%252526query2%25253D%252523Goodbye%252523";
    assertThat(HttpData.getUrlName(tripleEncoded)).isEqualTo("test?query1=Hello World&query2=#Goodbye#");
  }


  @Test
  public void urlReturnedAsIsIfUnableToDecode() {
    String url = "https://www.google.com/test%25-2test";
    // Tries 2 times url decoding:
    // 1. test%25-2test -> test%-2test
    // 2. test%-2test -> can't decode -2 so throws an exception
    assertThat(HttpData.getUrlName(url)).isEqualTo("test%-2test");
  }

  @Test
  public void testBuilder() {
    long id = 1111;
    long startTime = 10, uploadTime = 100, downloadTime = 1000, endTime = 10000;
    String trace = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";

    FakeNetworkConnectionsModel connectionsModel = new FakeNetworkConnectionsModel();
    HttpData.Builder builder = new HttpData.Builder(id, startTime, uploadTime, downloadTime, endTime, TestHttpData.FAKE_THREAD_LIST)
      .setResponseFields("status line =  HTTP/1.1 302 Found \n")
      .setMethod("method")
      .setRequestPayloadId(FakeNetworkConnectionsModel.REQUEST_PAYLOAD_ID)
      .setResponsePayloadId(FakeNetworkConnectionsModel.RESPONSE_PAYLOAD_ID)
      .setTrace(trace)
      .setUrl("url");

    HttpData data = builder.build();

    assertThat(data.getId()).isEqualTo(id);
    assertThat(data.getStartTimeUs()).isEqualTo(startTime);
    assertThat(data.getUploadedTimeUs()).isEqualTo(uploadTime);
    assertThat(data.getDownloadingTimeUs()).isEqualTo(downloadTime);
    assertThat(data.getEndTimeUs()).isEqualTo(endTime);

    assertThat(data.getStatusCode()).isEqualTo(302);
    assertThat(data.getMethod()).isEqualTo("method");
    assertThat(data.getRequestPayloadId()).isEqualTo(FakeNetworkConnectionsModel.REQUEST_PAYLOAD_ID);
    assertThat(data.getResponsePayloadId()).isEqualTo(FakeNetworkConnectionsModel.RESPONSE_PAYLOAD_ID);
    assertThat(data.getStackTrace().getTrace()).isEqualTo(trace);
    assertThat(data.getUrl()).isEqualTo("url");
    assertThat(data.getJavaThreads().get(0).getId()).isEqualTo(TestHttpData.FAKE_THREAD.getId());
    assertThat(data.getJavaThreads().get(0).getName()).isEqualTo(TestHttpData.FAKE_THREAD.getName());

    assertThat(Payload.newRequestPayload(connectionsModel, data).getBytes().toStringUtf8())
      .isEqualTo(FakeNetworkConnectionsModel.REQUEST_PAYLOAD);
    assertThat(Payload.newResponsePayload(connectionsModel, data).getBytes().toStringUtf8())
      .isEqualTo(FakeNetworkConnectionsModel.RESPONSE_PAYLOAD);
  }

  @Test
  public void guessFileExtensionFromContentType() {
    assertThat(new HttpData.ContentType("text/html").guessFileExtension()).isEqualTo(".html");
    assertThat(new HttpData.ContentType("image/jpeg").guessFileExtension()).isEqualTo(".jpg");
    assertThat(new HttpData.ContentType("application/json").guessFileExtension()).isEqualTo(".json");
    assertThat(new HttpData.ContentType("application/xml").guessFileExtension()).isEqualTo(".xml");
    assertThat(new HttpData.ContentType("application/text").guessFileExtension()).isNull();
    assertThat(new HttpData.ContentType("").guessFileExtension()).isNull();
  }

  @Test
  public void getMimeTypeFromContentType() {
    assertThat(new HttpData.ContentType("text/html; charset=utf-8").getMimeType()).isEqualTo("text/html");
    assertThat(new HttpData.ContentType("text/html").getMimeType()).isEqualTo("text/html");
    assertThat(new HttpData.ContentType("text/html;").getMimeType()).isEqualTo("text/html");
    assertThat(new HttpData.ContentType("").getMimeType()).isEmpty();
  }

  @Test
  public void getIsFormDataFromContentType() {
    assertThat(new HttpData.ContentType("application/x-www-form-urlencoded; charset=utf-8").isFormData()).isTrue();
    assertThat(new HttpData.ContentType("application/x-www-form-urlencoded").isFormData()).isTrue();
    assertThat(new HttpData.ContentType("Application/x-www-form-urlencoded;").isFormData()).isTrue();
    assertThat(new HttpData.ContentType("").isFormData()).isFalse();
    assertThat(new HttpData.ContentType("test/x-www-form-urlencoded;").isFormData()).isFalse();
    assertThat(new HttpData.ContentType("application/json; charset=utf-8").isFormData()).isFalse();
  }
  @Test
  public void getTypeDisplayNameFromContentType() {
    assertThat(new HttpData.ContentType("").getTypeDisplayName()).isEqualTo("");
    assertThat(new HttpData.ContentType(" ").getTypeDisplayName()).isEqualTo("");
    assertThat(new HttpData.ContentType("application/x-www-form-urlencoded; charset=utf-8").getTypeDisplayName()).isEqualTo("Form Data");
    assertThat(new HttpData.ContentType("text/html").getTypeDisplayName()).isEqualTo("Html");
    assertThat(new HttpData.ContentType("application/json").getTypeDisplayName()).isEqualTo("Json");
    assertThat(new HttpData.ContentType("image/jpeg").getTypeDisplayName()).isEqualTo("Image");
    assertThat(new HttpData.ContentType("audio/webm").getTypeDisplayName()).isEqualTo("Audio");
  }

  @Test
  public void getContentLengthFromLowerCaseData() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("CoNtEnt-LEngtH = 10000 \n  response-status-code = 200");
    HttpData data = builder.build();
    assertThat(data.getResponseField("content-length")).isEqualTo("10000");
    assertThat(data.getResponseField("cOnTenT-leNGth")).isEqualTo("10000");
  }

  @Test
  public void getStatusCodeFromFields() {
    HttpData.Builder builder = TestHttpData.newBuilder(1);
    builder.setResponseFields("content-length = 10000 \n  response-status-code = 200");
    HttpData data = builder.build();
    assertThat(data.getStatusCode()).isEqualTo(200);
  }

  private static final class FakeNetworkConnectionsModel implements NetworkConnectionsModel {
    public static final String REQUEST_PAYLOAD_ID = "requestId";
    public static final String RESPONSE_PAYLOAD_ID = "responseId";
    public static final String REQUEST_PAYLOAD = "requestPayload";
    public static final String RESPONSE_PAYLOAD = "responsePayload";

    @NotNull
    @Override
    public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ByteString requestPayload(@NotNull String payloadId) {
      if (payloadId.equals(REQUEST_PAYLOAD_ID)) {
        return ByteString.copyFromUtf8(REQUEST_PAYLOAD);
      }
      else if (payloadId.equals(RESPONSE_PAYLOAD_ID)) {
        return ByteString.copyFromUtf8(RESPONSE_PAYLOAD);
      }
      return ByteString.EMPTY;
    }
  }
}
