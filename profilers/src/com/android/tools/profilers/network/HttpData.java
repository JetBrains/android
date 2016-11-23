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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Data of http url connection. Each {@code HttpData} object matches a http connection with a unique id, and it includes both request data
 * and response data. Request data is filled immediately when the connection starts. Response data may be empty, it will filled when
 * connection completes.
 */
public class HttpData {
  public static final String FIELD_CONTENT_TYPE = "Content-Type";
  public static final String FIELD_CONTENT_LENGTH = "Content-Length";

  private long myId;
  private String myUrl;
  private String myMethod;
  private long myStartTimeUs;
  private long myDownloadingTimeUs;
  private long myEndTimeUs;
  private int myStatusCode = -1;
  private final Map<String, String> myResponseFields = new HashMap<>();
  private String myResponsePayloadId;
  // TODO: Move it to datastore, for now virtual file creation cannot select file type.
  private File myResponsePayloadFile;

  public long getId() {
    return myId;
  }

  public void setId(long id) {
    myId = id;
  }

  public String getUrl() {
    return myUrl;
  }

  public void setUrl(String url) {
    myUrl = url;
  }

  public String getMethod() {
    return myMethod;
  }

  public void setMethod(String method) {
    myMethod = method;
  }

  public long getStartTimeUs() {
    return myStartTimeUs;
  }

  public void setStartTimeUs(long startTimeUs) {
    myStartTimeUs = startTimeUs;
  }

  public long getDownloadingTimeUs() {
    return myDownloadingTimeUs;
  }

  public void setDownloadingTimeUs(long downloadingTimeUs) {
    myDownloadingTimeUs = downloadingTimeUs;
  }

  public long getEndTimeUs() {
    return myEndTimeUs;
  }

  public void setEndTimeUs(long endTimeUs) {
    myEndTimeUs = endTimeUs;
  }

  public int getStatusCode() {
    return myStatusCode;
  }

  public void setStatusCode(int statusCode) {
    myStatusCode = statusCode;
  }

  public String getHttpResponsePayloadId() {
    return myResponsePayloadId;
  }

  public void setHttpResponsePayloadId(String payloadId) {
    myResponsePayloadId = payloadId;
  }

  @Nullable
  public File getHttpResponsePayloadFile() {
    return myResponsePayloadFile;
  }

  public void setHttpResponsePayloadFile(@NotNull File payloadFile) {
    myResponsePayloadFile = payloadFile;
  }

  /**
   * Set the header response fields for this connection. To retrieve a single field,
   * use {@link #getHttpResponseField(String)}
   */
  public void setHttpResponseFields(@NotNull String fields) {
    parseHttpFieldsMap(fields);
  }

  @Nullable
  public String getHttpResponseField(@NotNull String field) {
    return myResponseFields.get(field);
  }

  private void parseHttpFieldsMap(@NotNull String fields) {
    myResponseFields.clear();
    boolean isFirstLine = true;
    for (String line : fields.split("\\n")) {
      if (line.trim().isEmpty()) {
        continue;
      }
      String[] keyAndValue = line.split("=", 2);
      assert keyAndValue.length == 2 : String.format("Unexpected http response field (%s)", line);
      String key = keyAndValue[0].trim();
      String value = StringUtil.trimEnd(keyAndValue[1].trim(), ';');
      myResponseFields.put(key, value);
      if (key.equals("null")) {
        // Parse the status code from the status line.
        // According to https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html,
        // the first line of a Response message is the Status-Line and the format is:
        // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRL
        assert isFirstLine;
        setStatusCode(Integer.parseInt(value.split(" ")[1]));
      }
      isFirstLine = false;
    }
  }
}
