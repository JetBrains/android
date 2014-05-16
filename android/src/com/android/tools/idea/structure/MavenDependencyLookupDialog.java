/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult;
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.utils.MavenLog;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MavenDependencyLookupDialog extends DialogWrapper {
  private static final String AAR_PACKAGING = "@" + SdkConstants.EXT_AAR;
  private static final String JAR_PACKAGING = "@" + SdkConstants.EXT_JAR;
  private static final int RESULT_LIMIT = 50;

  /**
   * Hardcoded list of common libraries that we will show in the dialog until the user actually does a search.
   */
  private static final List<Artifact> COMMON_LIBRARIES = ImmutableList.of(
    new Artifact(new MavenArtifactInfo("com.google.android.gms", "play-services", "+", null, null), "Google Play Services"),
    new Artifact(new MavenArtifactInfo("com.android.support", "appcompat-v7", "+", null, null), "AppCompat"),
    new Artifact(new MavenArtifactInfo("com.android.support", "support-v4", "+", null, null), "Support v4"),
    new Artifact(new MavenArtifactInfo("com.android.support", "mediarouter-v7", "+", null, null), "MediaRouter"),
    new Artifact(new MavenArtifactInfo("com.android.support", "gridlayout-v7", "+", null, null), "GridLayout"),
    new Artifact(new MavenArtifactInfo("com.android.support", "support-v13", "+", null, null), "Support v13"),
    new Artifact(new MavenArtifactInfo("com.google.code.gson", "gson", "2.2.+", null, null), "GSON"),
    new Artifact(new MavenArtifactInfo("joda-time", "joda-time", "2.3", null, null), "Joda-time"),
    new Artifact(new MavenArtifactInfo("com.squareup.picasso", "picasso", "2.2.+", null, null), "Picasso"),
    new Artifact(new MavenArtifactInfo("com.squareup", "otto", "1.3.+", null, null), "Otto"),
    new Artifact(new MavenArtifactInfo("org.slf4j", "slf4j-android", "1.6.1-RC1", null, null), "slf4j"),
    new Artifact(new MavenArtifactInfo("de.keyboardsurfer.android.widget", "crouton", "1.8.+", null, null), "Crouton"),
    new Artifact(new MavenArtifactInfo("com.nineoldandroids", "library", "2.4.+", null, null), "Nine Old Androids"),
    new Artifact(new MavenArtifactInfo("com.jakewharton", "butterknife", "4.0.+", null, null), "Butterknife"),
    new Artifact(new MavenArtifactInfo("com.google.guava", "guava", "16.0.+", null, null), "Guava"),
    new Artifact(new MavenArtifactInfo("com.squareup.okhttp", "okhttp", "1.3.+", null, null), "okhttp"),
    new Artifact(new MavenArtifactInfo("com.squareup.dagger", "dagger", "1.2.+", null, null), "Dagger")
  );

  /**
   * Hardcoded list of search rewrites to help users find common libraries.
   */
  private static final Map<String, String> SEARCH_OVERRIDES = ImmutableMap.<String, String>builder()
    .put("jodatime", "joda-time")
    .put("slf4j", "org.slf4j:slf4j-android")
    .put("slf4j-android", "org.slf4j:slf4j-android")
    .put("animation", "com.nineoldandroids:library")
    .put("pulltorefresh", "com.github.chrisbanes.actionbarpulltorefresh:library")
    .put("wire", "wire-runtime")
    .put("tape", "com.squareup:tape")
    .put("annotations", "androidannotations")
    .put("svg", "svg-android")
    .put("commons", "org.apache.commons")
    .build();

  private AsyncProcessIcon myProgressIcon;
  private TextFieldWithBrowseButton mySearchField;
  private JTextField mySearchTextField;
  private JPanel myPanel;
  private JBList myResultList;
  private final List<Artifact> myShownItems = Lists.newArrayList();
  private final ExecutorService mySearchWorker = Executors.newSingleThreadExecutor();
  private final Project myProject;
  private final boolean myAndroidModule;

  /**
   * Wraps the MavenArtifactInfo and supplies extra descriptive information we can display.
   */
  private static class Artifact extends MavenArtifactInfo {
    private final String myDescription;

    Artifact(@NotNull MavenArtifactInfo mai, @Nullable String description) {
      super(mai.getGroupId(), mai.getArtifactId(), mai.getVersion(), mai.getPackaging(), mai.getClassifier(), mai.getClassNames(),
            mai.getRepositoryId());
      myDescription = description;
    }

    public @NotNull String toString() {
      if (myDescription != null) {
        return myDescription + " (" + getCoordinates() + ")";
      } else {
        return getCoordinates();
      }
    }

    public @NotNull String getCoordinates() {
      return getGroupId() + ":" + getArtifactId() + ":" + getVersion() + (getPackaging() != null ? ("@" + getPackaging()) : "");
    }
  }

  /**
   * Comparator for Maven artifacts that does smart ordering for search results based on a given search string
   */
  private static class ArtifactComparator implements Comparator<Artifact> {
    @NotNull private final String mySearchText;

    private ArtifactComparator(@NotNull String searchText) {
      mySearchText = searchText;
    }

    @Override
    public int compare(@NotNull Artifact artifact1, @NotNull Artifact artifact2) {
      int score = calculateScore(mySearchText, artifact2) - calculateScore(mySearchText, artifact1);
      if (score != 0) {
        return score;
      } else {
        return artifact2.getVersion().compareTo(artifact1.getVersion());
      }
    }

    private static int calculateScore(@NotNull String searchText, @NotNull MavenArtifactInfo artifact) {
      int score = 0;
      if (artifact.getArtifactId().equals(searchText)) {
        score++;
      }
      if (artifact.getArtifactId().contains(searchText)) {
        score++;
      }
      if (artifact.getGroupId().contains(searchText)) {
        score++;
      }
      return score;
    }
  }

  public MavenDependencyLookupDialog(@NotNull Project project, boolean isAndroidModule) {
    super(project, true);
    myAndroidModule = isAndroidModule;
    myProgressIcon.suspend();

    mySearchField.setButtonIcon(AllIcons.Actions.Menu_find);
    mySearchField.getButton().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        startSearch();
      }
    });

    mySearchTextField = mySearchField.getTextField();
    mySearchTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (!isValidCoordinateSelected()) {
          startSearch();
        } else {
          close(OK_EXIT_CODE);
        }
      }
    });

    myShownItems.addAll(COMMON_LIBRARIES);
    myResultList.setModel(new CollectionComboBoxModel(myShownItems, null));
    myResultList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent listSelectionEvent) {
        Artifact value = (Artifact)myResultList.getSelectedValue();
        if (value != null) {
          mySearchTextField.setText(value.getCoordinates());
        }
      }
    });
    myResultList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2 && isValidCoordinateSelected()) {
          close(OK_EXIT_CODE);
        }
      }
    });

    init();
    myProject = project;
  }

  public @NotNull String getSearchText() {
    return mySearchTextField.getText();
  }

  /**
   * Prepares the search string and initiates the search in a worker thread.
   */
  private void startSearch() {
    if (myProgressIcon.isRunning()) {
      return;
    }
    myProgressIcon.resume();
    myResultList.clearSelection();
    synchronized (myShownItems) {
      myShownItems.clear();
      ((CollectionComboBoxModel)myResultList.getModel()).update();
    }
    String text = mySearchTextField.getText();
    if (StringUtil.isEmpty(text)) {
      return;
    }
    String override = SEARCH_OVERRIDES.get(text.toLowerCase(Locale.US));
    if (override != null) {
      text = override;
    }
    final String finalText = text;
    mySearchWorker.submit(new Runnable() {
      @Override
      public void run() {
        searchMavenIndex(finalText);
      }
    });
  }

  /**
   * Worker thread body that performs the search against the Maven index and interprets the result set
   */
  private void searchMavenIndex(@NotNull final String text) {
    try {
      if (!myProgressIcon.isRunning()) {
        return;
      }
      MavenArtifactSearcher searcher = new MavenArtifactSearcher();
      List<MavenArtifactSearchResult> searchResults = searcher.search(myProject, text, 100);
      if (!myProgressIcon.isRunning()) {
        return;
      }
      synchronized(myShownItems) {
        for (MavenArtifactSearchResult result : searchResults) {
          if (result.versions.isEmpty()) {
            continue;
          }
          MavenArtifactInfo artifact = result.versions.get(0);
          if (artifact == null ||
              (!SdkConstants.EXT_JAR.equals(artifact.getPackaging()) && !SdkConstants.EXT_AAR.equals(artifact.getPackaging()))) {
            continue;
          }
          if (myShownItems.size() >= RESULT_LIMIT) {
            myProgressIcon.suspend();
            break;
          }
          Artifact wrappedArtifact = new Artifact(artifact, null);
          if (!myShownItems.contains(wrappedArtifact)) {
            myShownItems.add(wrappedArtifact);
          }
        }

        Collections.sort(myShownItems, new ArtifactComparator(text));

        // In Android modules, if there are both @aar and @jar versions of the same artifact, hide the @jar one.
        if (myAndroidModule) {
          Set<String> itemsToRemove = Sets.newHashSet();
          for (Artifact art : myShownItems) {
            String s = art.getCoordinates();
            if (s.endsWith(AAR_PACKAGING)) {
              itemsToRemove.add(s.replace(AAR_PACKAGING, JAR_PACKAGING));
            }
          }
          for (Iterator<Artifact> i = myShownItems.iterator(); i.hasNext();) {
            Artifact art = i.next();
            if (itemsToRemove.contains(art.getCoordinates())) {
              i.remove();
            }
          }
        }
      }

      /**
       * Update the UI in the Swing UI thread
       */
      SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            synchronized (myShownItems) {
              ((CollectionComboBoxModel)myResultList.getModel()).update();
              if (myResultList.getSelectedIndex() == -1 && !myShownItems.isEmpty()) {
                myResultList.setSelectedIndex(0);
              }
              if (!myShownItems.isEmpty()) {
                myResultList.requestFocus();
              }
            }
          }
        });
    } catch (Exception e) {
      MavenLog.LOG.error(e);
    } finally {
      myProgressIcon.suspend();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchTextField;
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    if (!isValidCoordinateSelected()) {
      return new ValidationInfo("Please enter a valid coordinate, discover it or select one from the list", getPreferredFocusedComponent());
    }
    return super.doValidate();
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void dispose() {
    Disposer.dispose(myProgressIcon);
    mySearchWorker.shutdown();
    super.dispose();
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return MavenDependencyLookupDialog.class.getName();
  }

  private boolean isValidCoordinateSelected() {
    return mySearchTextField.getText().split(":").length == 3;
  }

  private void createUIComponents() {
    myProgressIcon = new AsyncProcessIcon("Progress");
  }
}
