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
import com.google.common.collect.ImmutableSortedMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.URLUtil;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Data of http url connection. Each {@code HttpData} object matches a http connection with a unique id, and it includes both request data
 * and response data. Request data is filled immediately when the connection starts. Response data may be empty, it will filled when
 * connection completes.
 */
public class HttpData {

  private final long myId;
  private final long myRequestStartTimeUs;
  private final long myRequestCompleteTimeUs;
  private final long myResponseStartTimeUs;
  private final long myResponseCompleteTimeUs;
  private final long myConnectionEndTimeUs;
  @NotNull private final String myUrl;
  @NotNull private final String myMethod;
  @NotNull private final String myTrace;
  @NotNull private final List<JavaThread> myThreads;

  @NotNull private final RequestHeader myRequestHeader;
  @NotNull private final ResponseHeader myResponseHeader;

  @NotNull private final String myRequestPayloadId;
  @NotNull private final String myResponsePayloadId;
  private final int myResponsePayloadSize;

  private HttpData(@NotNull Builder builder) {
    myId = builder.myId;
    myRequestStartTimeUs = builder.myRequestStartTimeUs;
    myRequestCompleteTimeUs = builder.myRequestCompleteTimeUs;
    myResponseStartTimeUs = builder.myResponseStartTimeUs;
    myResponseCompleteTimeUs = builder.myResponseCompleteTimeUs;
    myConnectionEndTimeUs = builder.myConnectionEndTimeUs;
    myUrl = builder.myUrl;
    myMethod = builder.myMethod;
    myTrace = builder.myTrace;
    myThreads = builder.myThreads;

    myResponsePayloadSize = builder.myResponsePayloadSize;
    myRequestPayloadId = builder.myRequestPayloadId;
    myResponsePayloadId = builder.myResponsePayloadId;

    myRequestHeader = new RequestHeader(builder.myRequestFields);
    myResponseHeader = new ResponseHeader(builder.myResponseFields);
  }

  public long getId() {
    return myId;
  }

  public long getRequestStartTimeUs() {
    return myRequestStartTimeUs;
  }

  public long getRequestCompleteTimeUs() {
    return myRequestCompleteTimeUs;
  }

  public long getResponseStartTimeUs() {
    return myResponseStartTimeUs;
  }

  public long getResponseCompleteTimeUs() {
    return myResponseCompleteTimeUs;
  }

  public long getConnectionEndTimeUs() {
    return myConnectionEndTimeUs;
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
  public String getTrace() { return myTrace; }

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

  public int getResponsePayloadSize() {
    return myResponsePayloadSize;
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
   * <p>
   * For example,
   * "www.example.com/demo/" -> "demo"
   * "www.example.com/test.png" -> "test.png"
   * "www.example.com/test.png?res=2" -> "test.png?res=2"
   * "www.example.com/" -> "www.example.com"
   */
  @NotNull
  public static String getUrlName(@NotNull String url) {
    try {
      // Run encode on the incoming url once, just in case, as this can prevent URI.create from
      // throwing a syntax exception in some cases. This encode will be decoded, below.
      URI uri = URI.create(URLEncoder.encode(url, CharsetToolkit.UTF8));
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
      String lastName;
      do {
        lastName = name;
        name = URLUtil.decode(name);
      }
      while (!name.equals(lastName));
      return name;
    }
    catch (UnsupportedEncodingException | IllegalArgumentException ignored) {
      // If here, it most likely means the url we are tracking is invalid in some way (formatting
      // or encoding). We try to recover gracefully by employing a simpler, less sophisticated
      // approach - return all text after the last slash. Keep in mind that this fallback
      // case should rarely, if ever, be used in practice.

      // url.length() - 2 eliminates the case where the last character in the URL is a slash
      // e.g. "www.example.com/name/" -> "name/", not ""
      int lastSlash = url.lastIndexOf('/', url.length() - 2);
      if (lastSlash >= 0) {
        return url.substring(lastSlash + 1);
      }

      return url;
    }
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
     * <p>
     * Examples:
     * "text/html; charset=utf-8" => "text/html"
     * "text/html" => "text/html"
     */
    @NotNull
    public String getMimeType() {
      return myContentType.split(";")[0];
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
  }

  public static abstract class Header {
    private static final String FIELD_CONTENT_ENCODING = "content-encoding";
    private static final String FIELD_CONTENT_TYPE = "content-type";
    public static final String FIELD_CONTENT_LENGTH = "content-length";

    /**
     * Returns the fields map with the keys sorted by the {@link String.CASE_INSENSITIVE_ORDER}. If the map contains an entry with a
     * capitalized key /Content-Length/, it returns the same value for a lower case key /content-length/.
     */
    @NotNull
    public abstract ImmutableSortedMap<String, String> getFields();

    @NotNull
    public String getField(@NotNull String key) {
      return getFields().getOrDefault(key, "");
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
        fieldsMap.put(keyAndValue[0].trim(), StringUtil.trimEnd(keyAndValue[1].trim(), ';'));
      });
      return fieldsMap;
    }
  }

  public static final class ResponseHeader extends Header {
    private static final String STATUS_CODE_NAME = "response-status-code";
    public static final int NO_STATUS_CODE = -1;

    @NotNull private final String myRawFields;
    @NotNull private final ImmutableSortedMap<String, String> myFields;
    private int myStatusCode = NO_STATUS_CODE;

    ResponseHeader(String fields) {
      myRawFields = fields;
      fields = fields.trim();
      if (fields.isEmpty()) {
        myFields = new ImmutableSortedMap.Builder<String, String>(String.CASE_INSENSITIVE_ORDER).build();
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
      myFields = new ImmutableSortedMap.Builder<String, String>(String.CASE_INSENSITIVE_ORDER).putAll(fieldsMap).build();
    }

    public int getStatusCode() {
      return myStatusCode;
    }

    @TestOnly
    @NotNull
    public String getRawFields() {
      return myRawFields;
    }

    @NotNull
    @Override
    public ImmutableSortedMap<String, String> getFields() {
      return myFields;
    }
  }

  public static final class RequestHeader extends Header {
    @NotNull private final String myRawFields;
    @NotNull private final ImmutableSortedMap<String, String> myFields;

    RequestHeader(String fields) {
      myRawFields = fields;
      myFields = new ImmutableSortedMap.Builder<String, String>(String.CASE_INSENSITIVE_ORDER).putAll(parseHeaderFields(fields)).build();
    }

    @TestOnly
    @NotNull
    public String getRawFields() {
      return myRawFields;
    }

    @NotNull
    @Override
    public ImmutableSortedMap<String, String> getFields() {
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
    private final long myRequestStartTimeUs;
    private final long myRequestCompleteTimeUs;
    private final long myResponseStartTimeUs;
    private final long myResponseCompleteTimeUs;
    private final long myConnectionEndTimeUs;

    private String myUrl = "";
    private String myMethod = "";

    private String myResponseFields = "";
    private String myRequestFields = "";
    private String myResponsePayloadId = "";
    private String myRequestPayloadId = "";
    private int myResponsePayloadSize;

    private String myTrace = "";
    private List<JavaThread> myThreads = new ArrayList<>();

    /**
     * @param id                     the unique identifier for the connection.
     * @param requestStartTimeUs     the time when the http request started uploading.
     * @param requestCompleteTimeUs  the time when the http request completed uploading.
     * @param responseStartTimeUs    the time when the http response was first received.
     * @param responseCompleteTimeUs the time when the http response was fully received.
     * @param connectionEndTimeUs    the time when the connection was closed. This can either mean the response was fully received in which
     *                               case this equals to |responseCompleteTimeUs|, or the connection was aborted in which case some of the
     *                               other time parameters may not be set.
     * @param threads                the list of threads used throughout the http connection.
     */
    public Builder(long id,
                   long requestStartTimeUs,
                   long requestCompleteTimeUs,
                   long responseStartTimeUs,
                   long responseCompleteTimeUs,
                   long connectionEndTimeUs,
                   List<JavaThread> threads) {
      myId = id;
      myRequestStartTimeUs = requestStartTimeUs;
      myRequestCompleteTimeUs = requestCompleteTimeUs;
      myResponseStartTimeUs = responseStartTimeUs;
      myResponseCompleteTimeUs = responseCompleteTimeUs;
      myConnectionEndTimeUs = connectionEndTimeUs;

      assert !threads.isEmpty() : "HttpData.Builder must be initialized with at least one thread";
      threads.forEach(this::addJavaThread);
    }

    @VisibleForTesting
    public Builder(@NotNull HttpData template) {
      this(template.getId(),
           template.getRequestStartTimeUs(),
           template.getRequestCompleteTimeUs(),
           template.getResponseStartTimeUs(),
           template.getResponseCompleteTimeUs(),
           template.getConnectionEndTimeUs(),
           template.getJavaThreads());
      setUrl(template.getUrl());
      setMethod(template.getMethod());
      setTrace(template.getTrace());
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
    public Builder setTrace(@NotNull String trace) {
      myTrace = trace;
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
    public Builder setResponsePayloadSize(int payloadSize) {
      myResponsePayloadSize = payloadSize;
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
