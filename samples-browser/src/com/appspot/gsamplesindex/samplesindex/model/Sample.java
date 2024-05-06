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
package com.appspot.gsamplesindex.samplesindex.model;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Data;
import com.google.api.client.util.Key;
import java.util.List;

public final class Sample extends GenericJson {
  @Key
  private List<ApiRef> apiRefs;
  @Key
  private List<String> categories;
  @Key
  private String cloneUrl;
  @Key
  private String description;
  @Key
  private List<String> devConsoleApis;
  @Key
  private List<DocRef> docRefs;
  @Key
  private String github;
  @Key
  private String icon;
  @Key
  private String id;
  @Key
  private String instructions;
  @Key
  private List<String> languages;
  @Key
  private String level;
  @Key
  private License license;
  @Key
  private String path;
  @Key
  private String prerequisites;
  @Key
  private List<Screenshot> screenshots;
  @Key
  private List<String> solutions;
  @Key
  private String status;
  @Key
  private String statusNote;
  @Key
  private List<String> technologies;
  @Key
  private String title;

  public Sample() {
  }

  public List<ApiRef> getApiRefs() {
    return this.apiRefs;
  }

  public Sample setApiRefs(List<ApiRef> apiRefs) {
    this.apiRefs = apiRefs;
    return this;
  }

  public List<String> getCategories() {
    return this.categories;
  }

  public Sample setCategories(List<String> categories) {
    this.categories = categories;
    return this;
  }

  public String getCloneUrl() {
    return this.cloneUrl;
  }

  public Sample setCloneUrl(String cloneUrl) {
    this.cloneUrl = cloneUrl;
    return this;
  }

  public String getDescription() {
    return this.description;
  }

  public Sample setDescription(String description) {
    this.description = description;
    return this;
  }

  public List<String> getDevConsoleApis() {
    return this.devConsoleApis;
  }

  public Sample setDevConsoleApis(List<String> devConsoleApis) {
    this.devConsoleApis = devConsoleApis;
    return this;
  }

  public List<DocRef> getDocRefs() {
    return this.docRefs;
  }

  public Sample setDocRefs(List<DocRef> docRefs) {
    this.docRefs = docRefs;
    return this;
  }

  public String getGithub() {
    return this.github;
  }

  public Sample setGithub(String github) {
    this.github = github;
    return this;
  }

  public String getIcon() {
    return this.icon;
  }

  public Sample setIcon(String icon) {
    this.icon = icon;
    return this;
  }

  public String getId() {
    return this.id;
  }

  public Sample setId(String id) {
    this.id = id;
    return this;
  }

  public String getInstructions() {
    return this.instructions;
  }

  public Sample setInstructions(String instructions) {
    this.instructions = instructions;
    return this;
  }

  public List<String> getLanguages() {
    return this.languages;
  }

  public Sample setLanguages(List<String> languages) {
    this.languages = languages;
    return this;
  }

  public String getLevel() {
    return this.level;
  }

  public Sample setLevel(String level) {
    this.level = level;
    return this;
  }

  public License getLicense() {
    return this.license;
  }

  public Sample setLicense(License license) {
    this.license = license;
    return this;
  }

  public String getPath() {
    return this.path;
  }

  public Sample setPath(String path) {
    this.path = path;
    return this;
  }

  public String getPrerequisites() {
    return this.prerequisites;
  }

  public Sample setPrerequisites(String prerequisites) {
    this.prerequisites = prerequisites;
    return this;
  }

  public List<Screenshot> getScreenshots() {
    return this.screenshots;
  }

  public Sample setScreenshots(List<Screenshot> screenshots) {
    this.screenshots = screenshots;
    return this;
  }

  public List<String> getSolutions() {
    return this.solutions;
  }

  public Sample setSolutions(List<String> solutions) {
    this.solutions = solutions;
    return this;
  }

  public String getStatus() {
    return this.status;
  }

  public Sample setStatus(String status) {
    this.status = status;
    return this;
  }

  public String getStatusNote() {
    return this.statusNote;
  }

  public Sample setStatusNote(String statusNote) {
    this.statusNote = statusNote;
    return this;
  }

  public List<String> getTechnologies() {
    return this.technologies;
  }

  public Sample setTechnologies(List<String> technologies) {
    this.technologies = technologies;
    return this;
  }

  public String getTitle() {
    return this.title;
  }

  public Sample setTitle(String title) {
    this.title = title;
    return this;
  }

  public Sample set(String fieldName, Object value) {
    return (Sample)super.set(fieldName, value);
  }

  public Sample clone() {
    return (Sample)super.clone();
  }

  static {
    Data.nullOf(ApiRef.class);
    Data.nullOf(DocRef.class);
  }
}
