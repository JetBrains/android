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
package com.android.tools.idea.gradle.structure.editors;

import com.android.SdkConstants;
import com.android.builder.model.ApiVersion;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.templates.SupportLibrary;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.android.tools.idea.templates.RepositoryUrlManager.REVISION_ANY;

public class MavenDependencyLookupDialog extends DialogWrapper {
  private static final String AAR_PACKAGING = "@" + SdkConstants.EXT_AAR;
  private static final String JAR_PACKAGING = "@" + SdkConstants.EXT_JAR;
  private static final int RESULT_LIMIT = 50;
  private static final String MAVEN_CENTRAL_SEARCH_URL = "https://search.maven.org/solrsearch/select?rows=%d&wt=xml&q=\"%s\"";
  private static final Logger LOG = Logger.getInstance(MavenDependencyLookupDialog.class);

  /**
   * Hardcoded list of common libraries that we will show in the dialog until the user actually does a search.
   */
  private static final List<Artifact> COMMON_LIBRARIES = ImmutableList.of(
    new Artifact("com.google.code.gson", "gson", "2.2.4", "GSON"),
    new Artifact("joda-time", "joda-time", "2.3", "Joda-time"),
    new Artifact("com.squareup.picasso", "picasso", "2.3.2", "Picasso"),
    new Artifact("com.squareup", "otto", "1.3.5", "Otto"),
    new Artifact("org.slf4j", "slf4j-android", "1.7.7", "slf4j"),
    new Artifact("de.keyboardsurfer.android.widget", "crouton", "1.8.4", "Crouton"),
    new Artifact("com.nineoldandroids", "library", "2.4.0", "Nine Old Androids"),
    new Artifact("com.jakewharton", "butterknife", "5.1.1", "Butterknife"),
    new Artifact("com.google.guava", "guava", "16.0.1", "Guava"),
    new Artifact("com.squareup.okhttp", "okhttp", "2.0.0", "okhttp"),
    new Artifact("com.squareup.dagger", "dagger", "1.2.1", "Dagger")
  );

  /**
   * Hard-coded list of search rewrites to help users find common libraries.
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
  private final ExecutorService mySearchWorker = Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Maven dependency lookup"));
  private final boolean myAndroidModule;

  private final List<String> myAndroidSdkLibraries = Lists.newArrayList();

  private static class Artifact {
    @NotNull private final String myGroupId;
    @NotNull private final String myArtifactId;
    @NotNull private final String myVersion;

    @Nullable private final String myDescription;

    public Artifact(@NotNull String groupId, @NotNull String artifactId, @NotNull String version, @Nullable String description) {
      myGroupId = groupId;
      myArtifactId = artifactId;
      myVersion = version;
      myDescription = description;
    }

    @Nullable
    public static Artifact fromCoordinate(@NotNull String libraryCoordinate) {
      GradleCoordinate gradleCoordinate = GradleCoordinate.parseCoordinateString(libraryCoordinate);
      if (gradleCoordinate == null) {
        return null;
      }
      String groupId = gradleCoordinate.getGroupId();
      String artifactId = gradleCoordinate.getArtifactId();
      if (groupId == null || artifactId == null) {
        return null;
      }
      return new Artifact(groupId, artifactId, gradleCoordinate.getRevision(), groupId + ":" + artifactId);
    }

    @NotNull
    public String toString() {
      if (myDescription != null) {
        return myDescription + " (" + getCoordinates() + ")";
      }
      else {
        return getCoordinates();
      }
    }

    @NotNull
    public String getCoordinates() {
      String version = REVISION_ANY.equals(myVersion) ? "" : ':' + myVersion;
      return myGroupId + ":" + myArtifactId + version;
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
      }
      else {
        return artifact2.myVersion.compareTo(artifact1.myVersion);
      }
    }

    private static int calculateScore(@NotNull String searchText, @NotNull Artifact artifact) {
      int score = 0;
      if (artifact.myArtifactId.equals(searchText)) {
        score++;
      }
      if (artifact.myArtifactId.contains(searchText)) {
        score++;
      }
      if (artifact.myGroupId.contains(searchText)) {
        score++;
      }
      return score;
    }
  }

  public MavenDependencyLookupDialog(@NotNull Project project, @Nullable Module module) {
    super(project, true);
    myAndroidModule = module != null && AndroidFacet.getInstance(module) != null;
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
        if (StringUtil.isEmpty(mySearchField.getText())) {
          return;
        }
        if (!isValidCoordinateSelected()) {
          startSearch();
        }
        else {
          close(OK_EXIT_CODE);
        }
      }
    });

    boolean preview = false;
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
        if (androidModel != null) {
          ApiVersion minSdkVersion = androidModel.getSelectedVariant().getMergedFlavor().getMinSdkVersion();
          if (minSdkVersion != null) {
            preview = new AndroidVersion(minSdkVersion.getApiLevel(), minSdkVersion.getCodename()).isPreview();
          }
        }
      }
    }

    RepositoryUrlManager manager = RepositoryUrlManager.get();
    for (SupportLibrary library : SupportLibrary.values()) {
      String libraryCoordinate = manager.getLibraryStringCoordinate(library, true);
      if (libraryCoordinate != null) {
        Artifact artifact = Artifact.fromCoordinate(libraryCoordinate);
        if (artifact != null) {
          myAndroidSdkLibraries.add(libraryCoordinate);
          myShownItems.add(artifact);
        }
      }
    }
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

    myOKAction = new OkAction() {
      @Override
      protected void doAction(ActionEvent e) {
        String text = mySearchField.getText();
        if (text != null && !hasVersion(text) && isKnownLocalLibrary(text)) {
          // If it's a known library that doesn't exist in the local repository, we don't display the version for it. Add it back so that
          // final string is a valid gradle coordinate.
          mySearchField.setText(text + ':' + REVISION_ANY);
        }
        super.doAction(e);
      }
    };
    init();
  }

  private static boolean isKnownLocalLibrary(@NotNull String text) {
    String group = getGroup(text);
    String artifact = getArtifact(text);

    if (group == null || artifact == null) {
      return false;
    }

    SupportLibrary library = SupportLibrary.find(group, artifact);
    return library != null;
  }

  @Nullable
  private static String getArtifact(@NotNull String coordinate) {
    int i = coordinate.indexOf(':');
    if (i >= 0 && i + 1 < coordinate.length()) {
      // There's at least one char after the first ':'
      coordinate = coordinate.substring(i + 1);
      i = coordinate.indexOf(':');
      if (i < 0) {
        i = coordinate.length();
      }
      return coordinate.substring(0, i);
    }
    return null;
  }

  @Nullable
  private static String getGroup(@NotNull String coordinate) {
    int i = coordinate.indexOf(':');
    if (i > 0) {
      return coordinate.substring(0, i);
    }
    return null;
  }

  @NotNull
  public String getSearchText() {
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
    synchronized (myShownItems) {
      myResultList.clearSelection();
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
        searchAllRepositories(finalText);
      }
    });
  }

  /**
   * Worker thread body that performs the search against the Maven index and interprets the result set
   */
  private void searchAllRepositories(@NotNull final String text) {
    try {
      if (!myProgressIcon.isRunning()) {
        return;
      }
      List<String> results = Lists.newArrayList();
      results.addAll(searchMavenCentral(text));
      results.addAll(searchSdkRepositories(text));

      if (!myProgressIcon.isRunning()) {
        return;
      }
      synchronized (myShownItems) {
        for (String s : results) {
          Artifact wrappedArtifact = Artifact.fromCoordinate(s);
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
          for (Iterator<Artifact> i = myShownItems.iterator(); i.hasNext(); ) {
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
      SwingUtilities.invokeLater(new Runnable() {
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
      LOG.error(e);
    }
    finally {
      myProgressIcon.suspend();
    }
  }

  @NotNull
  private List<String> searchSdkRepositories(@NotNull String text) {
    List<String> results = Lists.newArrayList();
    for (String library : myAndroidSdkLibraries) {
      if (library.contains(text)) {
        results.add(library);
      }
    }
    return results;
  }

  @NotNull
  private static List<String> searchMavenCentral(@NotNull String text) {
    return HttpRequests.request(String.format(MAVEN_CENTRAL_SEARCH_URL, RESULT_LIMIT, text))
      .accept("application/xml")
      .connect(new HttpRequests.RequestProcessor<List<String>>() {
        @Override
        public List<String> process(@NotNull HttpRequests.Request request) throws IOException {
          try {
            XPath idPath = XPath.newInstance("str[@name='id']");
            XPath versionPath = XPath.newInstance("str[@name='latestVersion']");
            //noinspection unchecked
            List<Element> artifacts = (List<Element>)XPath.newInstance("/response/result/doc").selectNodes(new SAXBuilder().build(request.getReader()));
            List<String> results = Lists.newArrayListWithExpectedSize(artifacts.size());
            for (Element element : artifacts) {
              try {
                String id = ((Element)idPath.selectSingleNode(element)).getValue();
                results.add(id + ":" + ((Element)versionPath.selectSingleNode(element)).getValue());
              }
              catch (NullPointerException ignored) {
                // A result is missing an ID or version. Just skip it.
              }
            }
            return results;
          }
          catch (JDOMException e) {
            LOG.error(e);
          }
          return Collections.emptyList();
        }
      }, Collections.<String>emptyList(), LOG);
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
    String text = mySearchTextField.getText();
    return GradleCoordinate.parseCoordinateString(text) != null;
  }

  private void createUIComponents() {
    myProgressIcon = new AsyncProcessIcon("Progress");
  }

  public static boolean hasVersion(String coordinateText) {
    return StringUtil.countChars(coordinateText, ':') > 1;
  }
}
