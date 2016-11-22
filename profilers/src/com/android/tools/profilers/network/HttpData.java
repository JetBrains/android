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
import java.net.URI;
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

  private final long myId;
  private final long myStartTimeUs;
  private final long myEndTimeUs;
  private final long myDownloadingTimeUs;
  @NotNull private final String myUrl;
  @NotNull private final String myMethod;

  @Nullable private String myResponsePayloadId;
  private int myStatusCode = -1;
  private final Map<String, String> myResponseFields = new HashMap<>();
  private String myTrace;
  // TODO: Move it to datastore, for now virtual file creation cannot select file type.
  private File myResponsePayloadFile;

  private HttpData(@NotNull Builder builder) {
    myId = builder.myId;
    myStartTimeUs = builder.myStartTimeUs;
    myEndTimeUs = builder.myEndTimeUs;
    myDownloadingTimeUs = builder.myDownloadingTimeUs;
    myUrl = builder.myUrl;
    myMethod = builder.myMethod;
    myTrace = builder.myTrace;

    myResponsePayloadId = builder.myResponsePayloadId;
    if (builder.myResponseFields != null) {
      parseResponseFields(builder.myResponseFields);
    }
  }

  public long getId() {
    return myId;
  }

  public long getStartTimeUs() {
    return myStartTimeUs;
  }

  public long getEndTimeUs() {
    return myEndTimeUs;
  }

  public long getDownloadingTimeUs() {
    return myDownloadingTimeUs;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getMethod() {
    return myMethod;
  }

  @Nullable
  public String getResponsePayloadId() {
    return myResponsePayloadId;
  }

  public int getStatusCode() {
    return myStatusCode;
  }

  @Nullable
  public String getTrace() { return myTrace; }

  public void setTrace(@NotNull String trace) { myTrace = trace; }

  @Nullable
  public File getResponsePayloadFile() {
    return myResponsePayloadFile;
  }

  public void setResponsePayloadFile(@NotNull File payloadFile) {
    myResponsePayloadFile = payloadFile;
  }

  @Nullable
  public String getResponseField(@NotNull String field) {
    return myResponseFields.get(field);
  }

  private void parseResponseFields(@NotNull String fields) {
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
        myStatusCode = Integer.parseInt(value.split(" ")[1]);
      }
      isFirstLine = false;
    }
  }

  /**
   * Return the name of the URL, which is the final complete word in the path portion of the URL.
   *
   * For example,
   * "www.example.com/demo/" -> "demo"
   * "www.example.com/test.png?res=2" -> "test.png"
   */
  @NotNull
  public static String getUrlName(@NotNull String url) {
    String path = URI.create(url).getPath().trim();
    path = StringUtil.trimTrailing(path, '/');
    path = path.lastIndexOf('/') != -1 ? path.substring(path.lastIndexOf('/') + 1) : path;
    return path;
  }

  public static final class Builder {
    private final long myId;
    private final long myStartTimeUs;
    private final long myEndTimeUs;
    private final long myDownloadingTimeUs;

    private String myUrl;
    private String myMethod;

    private String myResponseFields;
    private String myResponsePayloadId;
    private String myTrace;

    public Builder(long id, long startTimeUs, long endTimeUs, long downloadingTimeUS) {
      myId = id;
      myStartTimeUs = startTimeUs;
      myEndTimeUs = endTimeUs;
      myDownloadingTimeUs = downloadingTimeUS;
    }

    @NotNull
    public Builder setUrl(@NotNull String url) {
      myUrl = url;
      return this;
    }

    @NotNull
    public Builder setMethod(@NotNull String method) {
      myMethod = method;
      return this;
    }

    @NotNull
    public Builder setTrace(String trace) {
      myTrace = trace;
      return this;
    }

    @NotNull
    public Builder setResponseFields(@NotNull String responseFields) {
      myResponseFields = responseFields;
      return this;
    }

    @NotNull
    public Builder setResponsePayloadId(@NotNull String payloadId) {
      myResponsePayloadId = payloadId;
      return this;
    }

    @NotNull
    public HttpData build() {
      return new HttpData(this);
    }
  }
}
