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

import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class HttpDataTest {
  @Test
  public void responseFieldsStringIsCorrectlySplitAndTrimmed() throws Exception {
    HttpData data = new HttpData();
    data.setHttpResponseFields("first=1 \n  second  = 2\n equation=x+y=10");

    Map<String, String> fields = data.getHttpResponseFields();
    assert fields != null;
    assertThat(fields.get("first"), equalTo("1"));
    assertThat(fields.get("second"), equalTo("2"));
    assertThat(fields.get("equation"), equalTo("x+y=10"));
  }
}