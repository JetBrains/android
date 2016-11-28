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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

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
    assertThat(HttpData.getUrlName(urlString), equalTo(""));
  }
}
