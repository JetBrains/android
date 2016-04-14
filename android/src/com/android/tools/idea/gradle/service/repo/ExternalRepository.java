/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.repo;

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Facades external repositories (maven, jcenter etc).
 */
public class ExternalRepository {

  // TODO current implementation is rather straightforward, e.g. it doesn't handle concurrent requests, doesn't attempt to re-query
  // TODO when we become online etc. Also it was found out that there is also another place which has a similar job
  // TODO (MavenDependencyLookupDialog.searchMavenCentral()), so, we'd like to move that code out from a UI class and consolidate
  // TODO it inside the current one.

  private static final String URL_TEMPLATE = "http://repo1.maven.org/maven2/%s/%s/maven-metadata.xml";

  private static final String MAVEN_METADATA_VERSIONING = "versioning";
  private static final String MAVEN_METADATA_LATEST = "latest";

  private static final Logger LOG = Logger.getInstance(ExternalRepository.class);

  private final ConcurrentMap<Pair<String/*group*/, String/*artifact*/>, GradleVersion> myLatestVersionCache = Maps.newConcurrentMap();

  /**
   * Current implementation is rather straightforward, i.e. it's not expected that this service is used from a number of places
   * concurrently. However, we want to protect ourselves from background thread pool starvation, so, we allow only one request at a time.
   * <p/>
   * This field controls that.
   */
  private final AtomicBoolean myRequestInProgress = new AtomicBoolean();

  @Nullable
  public GradleVersion getLatest(@NotNull String groupId, @NotNull String artifactId) {
    return myLatestVersionCache.get(Pair.create(groupId, artifactId));
  }

  /**
   * Asks to refresh information for the target artifact, i.e. there is a possible case that we know that soon we'll need
   * to {@link #getLatest(String, String) get information about the latest availble artifact version}, so, we ask to prepare
   * it beforehand.
   *
   * @param groupId     target artifact's group id
   * @param artifactId  target artifact id
   */
  public void refreshFor(@NotNull final String groupId, @NotNull final String artifactId) {
    if (!myRequestInProgress.compareAndSet(false, true)) {
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          doRefreshFor(groupId, artifactId);
        }
        finally {
          myRequestInProgress.set(false);
        }
      }
    });
  }

  private void doRefreshFor(@NotNull final String groupId, @NotNull final String artifactId) {
    String url = String.format(URL_TEMPLATE, groupId.replaceAll("\\.", "/"), artifactId);
    Document document;
    try {
      document = JDOMUtil.loadDocument(URI.create(url).toURL());
    }
    catch (JDOMException e) {
      LOG.warn(String.format(
        "Unexpected exception occurred on attempt to parse document from %s (checking the latest version " + "for artifact '%s:%s')",
        url, groupId, artifactId));
      return;
    }
    catch (IOException e) {
      LOG.warn(String.format("Unexpected I/O exception occurred on attempt to check the latest version for artifact '%s:%s' at "
                             + "external repository (url %s)", groupId, artifactId, url));
      return;
    }
    Element versioning = document.getRootElement().getChild(MAVEN_METADATA_VERSIONING);
    if (versioning == null) {
      LOG.warn(String.format("Can't check the latest version for artifact '%s:%s'. Reason: artifact metadata info downloaded from "
                             + "%s has unknown format - expected to find a <%s> element under a root element but it's not there",
                             groupId, artifactId, url, MAVEN_METADATA_VERSIONING));
      return;
    }
    Element latest = versioning.getChild(MAVEN_METADATA_LATEST);
    if (latest == null) {
      LOG.warn(String.format("Can't check the latest version for artifact '%s:%s'. Reason: artifact metadata info downloaded from "
                             + "%s has unknown format - expected to find a <%s> element under a <%s> element but it's not there",
                             groupId, artifactId, url, MAVEN_METADATA_LATEST, MAVEN_METADATA_VERSIONING));
      return;
    }
    try {
      GradleVersion version = GradleVersion.parse(latest.getText());
      myLatestVersionCache.put(Pair.create(groupId, artifactId), version);
    }
    catch (NumberFormatException e) {
      LOG.warn(String.format("Can't check the latest version for artifact '%s:%s'. Reason: artifact metadata info downloaded from " +
                             "%s has unknown version format - '%s'", groupId, artifactId, url, latest.getText()));
    }
  }
}
