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

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.util.Key;

public abstract class SamplesIndexRequest<T> extends AbstractGoogleJsonClientRequest<T> {
  @Key
  private String alt;
  @Key
  private String fields;
  @Key
  private String key;
  @Key("oauth_token")
  private String oauthToken;
  @Key
  private Boolean prettyPrint;
  @Key
  private String quotaUser;
  @Key
  private String userIp;

  public SamplesIndexRequest(SamplesIndex client, String method, String uriTemplate, Object content, Class<T> responseClass) {
    super(client, method, uriTemplate, content, responseClass);
  }

  public String getAlt() {
    return this.alt;
  }

  public SamplesIndexRequest<T> setAlt(String alt) {
    this.alt = alt;
    return this;
  }

  public String getFields() {
    return this.fields;
  }

  public SamplesIndexRequest<T> setFields(String fields) {
    this.fields = fields;
    return this;
  }

  public String getKey() {
    return this.key;
  }

  public SamplesIndexRequest<T> setKey(String key) {
    this.key = key;
    return this;
  }

  public String getOauthToken() {
    return this.oauthToken;
  }

  public SamplesIndexRequest<T> setOauthToken(String oauthToken) {
    this.oauthToken = oauthToken;
    return this;
  }

  public Boolean getPrettyPrint() {
    return this.prettyPrint;
  }

  public SamplesIndexRequest<T> setPrettyPrint(Boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
    return this;
  }

  public String getQuotaUser() {
    return this.quotaUser;
  }

  public SamplesIndexRequest<T> setQuotaUser(String quotaUser) {
    this.quotaUser = quotaUser;
    return this;
  }

  public String getUserIp() {
    return this.userIp;
  }

  public SamplesIndexRequest<T> setUserIp(String userIp) {
    this.userIp = userIp;
    return this;
  }

  public final SamplesIndex getAbstractGoogleClient() {
    return (SamplesIndex)super.getAbstractGoogleClient();
  }

  public SamplesIndexRequest<T> setDisableGZipContent(boolean disableGZipContent) {
    return (SamplesIndexRequest)super.setDisableGZipContent(disableGZipContent);
  }

  public SamplesIndexRequest<T> setRequestHeaders(HttpHeaders headers) {
    return (SamplesIndexRequest)super.setRequestHeaders(headers);
  }

  public SamplesIndexRequest<T> set(String parameterName, Object value) {
    return (SamplesIndexRequest)super.set(parameterName, value);
  }
}
