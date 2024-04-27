/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.appspot.gsamplesindex.samplesindex;

import com.appspot.gsamplesindex.samplesindex.model.Sample;
import com.appspot.gsamplesindex.samplesindex.model.SampleCollection;
import com.google.api.client.googleapis.GoogleUtils;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClient;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.Key;
import com.google.api.client.util.Preconditions;
import java.io.IOException;

public class SamplesIndex extends AbstractGoogleJsonClient {
  public SamplesIndex(HttpTransport transport,
                      JsonFactory jsonFactory,
                      String rootUrl,
                      String servicePath,
                      HttpRequestInitializer httpRequestInitializer) {
    this(new Builder(transport, jsonFactory, rootUrl, servicePath, httpRequestInitializer));
  }

  SamplesIndex(Builder builder) {
    super(builder);
  }

  protected void initialize(AbstractGoogleClientRequest<?> httpClientRequest) throws IOException {
    super.initialize(httpClientRequest);
  }

  public Samples samples() {
    return new Samples();
  }

  static {
    // b/283741253: update check
    Preconditions.checkState(GoogleUtils.MAJOR_VERSION == 1 &&
                             (GoogleUtils.MINOR_VERSION >= 32 || GoogleUtils.MINOR_VERSION == 31 && GoogleUtils.BUGFIX_VERSION >= 1) ||
                             GoogleUtils.MAJOR_VERSION >= 2,
                             "You are currently running with version %s of google-api-client. You need at least version 1.31.1 of " +
                             "google-api-client to run version 2.0.0 of the Cloud Resource Manager API library.",
                             GoogleUtils.VERSION);
  }

  public static final class Builder extends AbstractGoogleJsonClient.Builder {
    public Builder(HttpTransport transport,
                   JsonFactory jsonFactory,
                   String rootUrl,
                   String servicePath,
                   HttpRequestInitializer httpRequestInitializer) {
      super(transport, jsonFactory, rootUrl, servicePath, httpRequestInitializer, false);
    }

    public SamplesIndex build() {
      return new SamplesIndex(this);
    }

    public Builder setRootUrl(String rootUrl) {
      return (Builder)super.setRootUrl(rootUrl);
    }

    public Builder setServicePath(String servicePath) {
      return (Builder)super.setServicePath(servicePath);
    }

    public Builder setHttpRequestInitializer(HttpRequestInitializer httpRequestInitializer) {
      return (Builder)super.setHttpRequestInitializer(httpRequestInitializer);
    }

    public Builder setApplicationName(String applicationName) {
      return (Builder)super.setApplicationName(applicationName);
    }

    public Builder setSuppressPatternChecks(boolean suppressPatternChecks) {
      return (Builder)super.setSuppressPatternChecks(suppressPatternChecks);
    }

    public Builder setSuppressRequiredParameterChecks(boolean suppressRequiredParameterChecks) {
      return (Builder)super.setSuppressRequiredParameterChecks(suppressRequiredParameterChecks);
    }

    public Builder setSuppressAllChecks(boolean suppressAllChecks) {
      return (Builder)super.setSuppressAllChecks(suppressAllChecks);
    }

    public Builder setSamplesIndexRequestInitializer(SamplesIndexRequestInitializer samplesindexRequestInitializer) {
      return (Builder)super.setGoogleClientRequestInitializer(samplesindexRequestInitializer);
    }

    public Builder setGoogleClientRequestInitializer(GoogleClientRequestInitializer googleClientRequestInitializer) {
      return (Builder)super.setGoogleClientRequestInitializer(googleClientRequestInitializer);
    }
  }

  public class Samples {
    public Samples() {
    }

    public GetSample getSample(Long id) throws IOException {
      GetSample result = new GetSample(id);
      SamplesIndex.this.initialize(result);
      return result;
    }

    public ListSamples listSamples() throws IOException {
      ListSamples result = new ListSamples();
      SamplesIndex.this.initialize(result);
      return result;
    }

    public class ListSamples extends SamplesIndexRequest<SampleCollection> {
      private static final String REST_PATH = "sample";
      @Key
      private String category;
      @Key
      private String status;
      @Key
      private String language;
      @Key
      private String level;
      @Key
      private String solution;
      @Key
      private String technology;

      protected ListSamples() {
        super(SamplesIndex.this, "GET", "sample", (Object)null, SampleCollection.class);
      }

      public HttpResponse executeUsingHead() throws IOException {
        return super.executeUsingHead();
      }

      public HttpRequest buildHttpRequestUsingHead() throws IOException {
        return super.buildHttpRequestUsingHead();
      }

      public ListSamples setAlt(String alt) {
        return (ListSamples)super.setAlt(alt);
      }

      public ListSamples setFields(String fields) {
        return (ListSamples)super.setFields(fields);
      }

      public ListSamples setKey(String key) {
        return (ListSamples)super.setKey(key);
      }

      public ListSamples setOauthToken(String oauthToken) {
        return (ListSamples)super.setOauthToken(oauthToken);
      }

      public ListSamples setPrettyPrint(Boolean prettyPrint) {
        return (ListSamples)super.setPrettyPrint(prettyPrint);
      }

      public ListSamples setQuotaUser(String quotaUser) {
        return (ListSamples)super.setQuotaUser(quotaUser);
      }

      public ListSamples setUserIp(String userIp) {
        return (ListSamples)super.setUserIp(userIp);
      }

      public String getCategory() {
        return this.category;
      }

      public ListSamples setCategory(String category) {
        this.category = category;
        return this;
      }

      public String getStatus() {
        return this.status;
      }

      public ListSamples setStatus(String status) {
        this.status = status;
        return this;
      }

      public String getLanguage() {
        return this.language;
      }

      public ListSamples setLanguage(String language) {
        this.language = language;
        return this;
      }

      public String getLevel() {
        return this.level;
      }

      public ListSamples setLevel(String level) {
        this.level = level;
        return this;
      }

      public String getSolution() {
        return this.solution;
      }

      public ListSamples setSolution(String solution) {
        this.solution = solution;
        return this;
      }

      public String getTechnology() {
        return this.technology;
      }

      public ListSamples setTechnology(String technology) {
        this.technology = technology;
        return this;
      }

      public ListSamples set(String parameterName, Object value) {
        return (ListSamples)super.set(parameterName, value);
      }
    }

    public class GetSample extends SamplesIndexRequest<Sample> {
      private static final String REST_PATH = "sample/{id}";
      @Key
      private Long id;

      protected GetSample(Long id) {
        super(SamplesIndex.this, "GET", "sample/{id}", (Object)null, Sample.class);
        this.id = (Long)Preconditions.checkNotNull(id, "Required parameter id must be specified.");
      }

      public HttpResponse executeUsingHead() throws IOException {
        return super.executeUsingHead();
      }

      public HttpRequest buildHttpRequestUsingHead() throws IOException {
        return super.buildHttpRequestUsingHead();
      }

      public GetSample setAlt(String alt) {
        return (GetSample)super.setAlt(alt);
      }

      public GetSample setFields(String fields) {
        return (GetSample)super.setFields(fields);
      }

      public GetSample setKey(String key) {
        return (GetSample)super.setKey(key);
      }

      public GetSample setOauthToken(String oauthToken) {
        return (GetSample)super.setOauthToken(oauthToken);
      }

      public GetSample setPrettyPrint(Boolean prettyPrint) {
        return (GetSample)super.setPrettyPrint(prettyPrint);
      }

      public GetSample setQuotaUser(String quotaUser) {
        return (GetSample)super.setQuotaUser(quotaUser);
      }

      public GetSample setUserIp(String userIp) {
        return (GetSample)super.setUserIp(userIp);
      }

      public Long getId() {
        return this.id;
      }

      public GetSample setId(Long id) {
        this.id = id;
        return this;
      }

      public GetSample set(String parameterName, Object value) {
        return (GetSample)super.set(parameterName, value);
      }
    }
  }
}
