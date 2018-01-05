/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.network.httpdata;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/**
 * Data of http url connection. Each {@code HttpData} object matches a http connection with a unique id, and it includes both request data
 * and response data. Request data is filled immediately when the connection starts. Response data may be empty, it will filled when
 * connection completes.
 */
public class HttpData {
  // TODO: Way more robust handling of different types. See also:
  // http://www.iana.org/assignments/media-types/media-types.xhtml
  private static final Map<String, String> CONTENT_EXTENSIONS_MAP = new ImmutableMap.Builder<String, String>()
    .put("/bmp", ".bmp")
    .put("/csv", ".csv")
    .put("/gif", ".gif")
    .put("/html", ".html")
    .put("/jpeg", ".jpg")
    .put("/json", ".json")
    .put("/png", ".png")
    .put("/webp", ".webp")
    .put("/xml", ".xml")
    .build();

  private final long myId;
  private final long myStartTimeUs;
  private final long myUploadedTimeUs;
  private final long myDownloadingTimeUs;
  private final long myEndTimeUs;
  @NotNull private final String myUrl;
  @NotNull private final String myMethod;
  @NotNull private final String myTraceId;
  @NotNull private final List<JavaThread> myThreads;

  @NotNull private final RequestHeader myRequestHeader;
  @NotNull private final ResponseHeader myResponseHeader;

  @NotNull private final String myRequestPayloadId;
  @NotNull private final String myResponsePayloadId;

  private HttpData(@NotNull Builder builder) {
    myId = builder.myId;
    myStartTimeUs = builder.myStartTimeUs;
    myUploadedTimeUs = builder.myUploadedTimeUs;
    myDownloadingTimeUs = builder.myDownloadingTimeUs;
    myEndTimeUs = builder.myEndTimeUs;
    myUrl = builder.myUrl;
    myMethod = builder.myMethod;
    myTraceId = builder.myTraceId;
    myThreads = builder.myThreads;

    myRequestPayloadId = builder.myRequestPayloadId;
    myResponsePayloadId = builder.myResponsePayloadId;

    myRequestHeader = new RequestHeader(builder.myRequestFields);
    myResponseHeader = new ResponseHeader(builder.myResponseFields);
  }

  public long getId() {
    return myId;
  }

  public long getStartTimeUs() {
    return myStartTimeUs;
  }

  public long getUploadedTimeUs() {
    return myUploadedTimeUs;
  }

  public long getDownloadingTimeUs() {
    return myDownloadingTimeUs;
  }

  public long getEndTimeUs() {
    return myEndTimeUs;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getMethod() {
    return myMethod;
  }

  @NotNull
  public String getTraceId() { return myTraceId; }

  /**
   * Threads that access a connection. The first thread is the thread that creates the connection.
   */
  @NotNull
  public List<JavaThread> getJavaThreads() {
    return myThreads;
  }

  @NotNull
  public String getRequestPayloadId() {
    return myRequestPayloadId;
  }

  @NotNull
  public String getResponsePayloadId() {
    return myResponsePayloadId;
  }

  @NotNull
  public RequestHeader getRequestHeader() {
    return myRequestHeader;
  }

  @NotNull
  public ResponseHeader getResponseHeader() {
    return myResponseHeader;
  }

  /**
   * Return the name of the URL, which is the final complete word in the path portion of the URL.
   * The query is included as it can be useful to disambiguate requests. Additionally,
   * the returned value is URL decoded, so that, say, "Hello%2520World" -> "Hello World".
   *
   * For example,
   * "www.example.com/demo/" -> "demo"
   * "www.example.com/test.png" -> "test.png"
   * "www.example.com/test.png?res=2" -> "test.png?res=2"
   * "www.example.com/" -> "www.example.com"
   */
  @NotNull
  public static String getUrlName(@NotNull String url) {
    URI uri = URI.create(url);
    String name = uri.getPath() != null ? StringUtil.trimTrailing(uri.getPath(), '/') : "";
    if (name.isEmpty()) {
      return uri.getHost();
    }
    name = name.lastIndexOf('/') != -1 ? name.substring(name.lastIndexOf('/') + 1) : name;
    if (uri.getQuery() != null) {
      name += "?" + uri.getQuery();
    }

    // URL might be encoded an arbitrarily deep number of times. Keep decoding until we peel away the final layer.
    // Usually this is only expected to loop once or twice.
    // See more: http://stackoverflow.com/questions/3617784/plus-signs-being-replaced-for-252520
    try {
      String lastName;
      do {
        lastName = name;
        name = URLDecoder.decode(name, "UTF-8");
      } while (!name.equals(lastName));
    } catch (Exception ignored) {
    }
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HttpData data = (HttpData)o;
    return myId == data.myId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myId);
  }

  public static final class ContentType {
    public static final String APPLICATION_FORM_MIME_TYPE = "application/x-www-form-urlencoded";

    @NotNull private final String myContentType;

    public ContentType(@NotNull String contentType) {
      myContentType = contentType;
    }

    public boolean isEmpty() {
      return myContentType.isEmpty();
    }

    /**
     * @return MIME type related information from Content-Type because Content-Type may contain
     * other information such as charset or boundary.
     *
     * Examples:
     * "text/html; charset=utf-8" => "text/html"
     * "text/html" => "text/html"
     */
    @NotNull
    public String getMimeType() {
      return myContentType.split(";")[0];
    }

    /**
     * Returns file extension based on the response Content-Type header field.
     * If type is absent or not supported, returns null.
     */
    @Nullable
    public String guessFileExtension() {
      for (Map.Entry<String, String> entry : CONTENT_EXTENSIONS_MAP.entrySet()) {
        if (myContentType.contains(entry.getKey())) {
          return entry.getValue();
        }
      }
      return null;
    }

    @NotNull
    public String getContentType() {
      return myContentType;
    }

    @Override
    public String toString() {
      return getContentType();
    }

    public boolean isFormData() {
      return getMimeType().equalsIgnoreCase(APPLICATION_FORM_MIME_TYPE);
    }

    /**
     * Returns display name with the first letter in upper case.
     * <ul>
     *   <li>If type is form data, returns "Form Data".</li>
     *   <li>If type is "text" or "application", returns the sub type, for example, "application/json" => "Json".</li>
     *   <li>Otherwise, return the type, for example, "image/png" => "Image".</li>
     * </ul>
     */
    public String getTypeDisplayName() {
      String mimeType = getMimeType().trim();
      if (mimeType.isEmpty()) {
        return mimeType;
      }
      if (isFormData()) {
        return "Form Data";
      }
      String[] typeAndSubType = mimeType.split("/", 2);
      boolean showSubType = typeAndSubType.length > 1 && (typeAndSubType[0].equals("text") || typeAndSubType[0].equals("application"));
      String name = showSubType ? typeAndSubType[1] : typeAndSubType[0];
      return name.isEmpty() ? name : name.substring(0, 1).toUpperCase() + name.substring(1);
    }
  }

  public static abstract class Header {
    private static final String FIELD_CONTENT_ENCODING = "content-encoding";
    private static final String FIELD_CONTENT_TYPE = "content-type";
    public static final String FIELD_CONTENT_LENGTH = "content-length";

    @NotNull
    public abstract ImmutableMap<String, String> getFields();

    @NotNull
    public String getField(@NotNull String key) {
      return getFields().getOrDefault(key.toLowerCase(), "");
    }

    @NotNull
    public String getContentEncoding() {
      return getField(FIELD_CONTENT_ENCODING);
    }

    @NotNull
    public ContentType getContentType() {
      return new ContentType(getField(FIELD_CONTENT_TYPE));
    }

    /**
     * @return value of "content-length" in the headers, or -1 if the headers does not contain it.
     */
    public int getContentLength() {
      String contentLength = getField(FIELD_CONTENT_LENGTH);
      return contentLength.isEmpty() ? -1 : Integer.parseInt(contentLength);
    }

    @NotNull
    protected static Map<String, String> parseHeaderFields(@NotNull String fields) {
      Map<String, String> fieldsMap = new HashMap<>();
      Arrays.stream(fields.split("\\n")).filter(line -> !line.trim().isEmpty()).forEach(line -> {
        String[] keyAndValue = line.split("=", 2);
        assert keyAndValue.length == 2 : String.format("Unexpected http header field (%s)", line);
        fieldsMap.put(keyAndValue[0].trim().toLowerCase(), StringUtil.trimEnd(keyAndValue[1].trim(), ';'));
      });
      return fieldsMap;
    }
  }

  public static final class ResponseHeader extends Header {
    private static final String STATUS_CODE_NAME = "response-status-code";
    public static final int NO_STATUS_CODE = -1;

    @NotNull private final ImmutableMap<String, String> myFields;
    private int myStatusCode = NO_STATUS_CODE;

    ResponseHeader(String fields) {
      fields = fields.trim();
      if (fields.isEmpty()) {
        myFields = ImmutableMap.of();
        return;
      }

      // The status-line - should be formatted as per
      // section 6.1 of RFC 2616.
      // https://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html
      //
      // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase

      String[] firstLineSplit = fields.split("\\n", 2);
      String status = firstLineSplit[0].trim();
      if (!status.isEmpty()) {
        String[] tokens = status.split("=", 2);
        status = tokens[tokens.length - 1].trim();
        if (status.startsWith("HTTP/1.")) {
          myStatusCode = Integer.parseInt(status.split(" ")[1]);
          fields = firstLineSplit.length > 1 ? firstLineSplit[1] : "";
        }
      }

      Map<String, String> fieldsMap = parseHeaderFields(fields);

      if (fieldsMap.containsKey(STATUS_CODE_NAME)) {
         String statusCode = fieldsMap.remove(STATUS_CODE_NAME);
        myStatusCode = Integer.parseInt(statusCode);
      }
      assert myStatusCode != -1 : String.format("Unexpected http response (%s)", fields);
      myFields = ImmutableMap.copyOf(fieldsMap);
    }

    public int getStatusCode() {
      return myStatusCode;
    }

    @NotNull
    @Override
    public ImmutableMap<String, String> getFields() {
      return myFields;
    }
  }

  public static final class RequestHeader extends Header {
    @NotNull private final ImmutableMap<String, String> myFields;

    RequestHeader(String fields) {
      myFields = ImmutableMap.copyOf(parseHeaderFields(fields));
    }

    @NotNull
    @Override
    public ImmutableMap<String, String> getFields() {
      return myFields;
    }
  }

  // Thread information fetched from the JVM, as opposed to from native code.
  // See also: https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html
  public static final class JavaThread {
    private final long myId;
    @NotNull private final String myName;

    public JavaThread(long id, @NotNull String name) {
      myId = id;
      myName = name;
    }

    public long getId() {
      return myId;
    }

    @NotNull
    public String getName() {
      return myName;
    }
  }

  public static final class Builder {
    private final long myId;
    private final long myStartTimeUs;
    private final long myUploadedTimeUs;
    private final long myDownloadingTimeUs;
    private final long myEndTimeUs;

    private String myUrl = "";
    private String myMethod = "";

    private String myResponseFields = "";
    private String myRequestFields = "";
    private String myResponsePayloadId = "";
    private String myRequestPayloadId = "";
    private String myTraceId = "";
    private List<JavaThread> myThreads = new ArrayList<>();

    public Builder(long id, long startTimeUs, long uploadedTimeUs, long downloadingTimeUs, long endTimeUs, List<JavaThread> threads) {
      myId = id;
      myStartTimeUs = startTimeUs;
      myUploadedTimeUs = uploadedTimeUs;
      myDownloadingTimeUs = downloadingTimeUs;
      myEndTimeUs = endTimeUs;

      assert !threads.isEmpty() : "HttpData.Builder must be initialized with at least one thread";
      threads.forEach(this::addJavaThread);
    }

    @VisibleForTesting
    public Builder(@NotNull HttpData template) {
      this(template.getId(),
           template.getStartTimeUs(),
           template.getUploadedTimeUs(),
           template.getDownloadingTimeUs(),
           template.getEndTimeUs(),
           template.getJavaThreads());
      setUrl(template.getUrl());
      setMethod(template.getMethod());
      setTraceId(template.getTraceId());
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
    public Builder setTraceId(@NotNull String traceId) {
      myTraceId = traceId;
      return this;
    }

    @NotNull
    public Builder addJavaThread(@NotNull JavaThread thread) {
      if (myThreads.stream().noneMatch(t -> t.getId() == thread.getId())) {
        myThreads.add(thread);
      }
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
    public Builder setRequestPayloadId(@NotNull String payloadId) {
      myRequestPayloadId = payloadId;
      return this;
    }

    @NotNull
    public Builder setRequestFields(@NotNull String requestFields) {
      myRequestFields = requestFields;
      return this;
    }

    @NotNull
    public HttpData build() {
      return new HttpData(this);
    }

  }
}
