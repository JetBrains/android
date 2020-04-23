/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Unit tests for {@link HtmlUtils}.
 */
public class HtmlUtilsTest {

  @Test
  public void testPlainTextToHtml_linkifyUrl() {
    assertEquals("<html><body>visit <a href='www.google.com'>www.google.com</a>.</body></html>",
                 HtmlUtils.plainTextToHtml("visit www.google.com."));
    assertEquals("<html><body>visit <a href='http://www.google.com'>http://www.google.com</a>.</body></html>",
                 HtmlUtils.plainTextToHtml("visit http://www.google.com."));
    assertEquals("<html><body>check <a href='https://www.google.com/abc'>https://www.google.com/abc</a> for detail.</body></html>",
                 HtmlUtils.plainTextToHtml("check https://www.google.com/abc for detail."));
    assertEquals("<html><body>visit non url abc.edf</body></html>", HtmlUtils.plainTextToHtml("visit non url abc.edf"));
  }

  @Test
  public void testPlainTextToHtml_escapeHtmlEntities() {
    assertEquals("<html><body>&quot;test&quot;</body></html>", HtmlUtils.plainTextToHtml("\"test\""));
    assertEquals("<html><body>&lt;test&gt;</body></html>", HtmlUtils.plainTextToHtml("<test>"));
    assertEquals("<html><body>test&amp;</body></html>", HtmlUtils.plainTextToHtml("test&"));
  }

  @Test
  public void testPlainTextToHtml_escapeLineBreaker() {
    assertEquals("<html><body>test<br>line breaker</body></html>", HtmlUtils.plainTextToHtml("test\nline breaker"));
    assertEquals("<html><body>test<br>line breaker</body></html>", HtmlUtils.plainTextToHtml("test\r\nline breaker"));
  }
}
