/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.rendering;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.rendering.RenderErrorContributor;
import com.android.tools.idea.rendering.RenderUtils;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.rendering.HtmlLinkManager;
import com.android.tools.rendering.HtmlLinkManagerCompat;
import com.android.tools.rendering.RenderLogger;
import com.android.tools.rendering.RenderResult;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Maps;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Function;
import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;

/** Contribute blaze specific render errors. */
public class BlazeRenderErrorContributor implements RenderErrorContributor {
  private final EditorDesignSurface designSurface;
  private final RenderLogger logger;
  private final Module module;
  private final PsiFile sourceFile;
  private final Project project;
  private final HtmlLinkManager linkManager;
  private final HyperlinkListener linkHandler;
  private final Set<RenderErrorModel.Issue> issues = new LinkedHashSet<>();

  public BlazeRenderErrorContributor(EditorDesignSurface surface, RenderResult result) {
    designSurface = surface;
    logger = result.getLogger();
    module = result.getModule();
    sourceFile = result.getSourceFile();
    project = module.getProject();
    linkManager = logger.getLinkManager();
    linkHandler =
        e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            JEditorPane pane = (JEditorPane) e.getSource();
            if (e instanceof HTMLFrameHyperlinkEvent) {
              HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent) e;
              HTMLDocument doc = (HTMLDocument) pane.getDocument();
              doc.processHTMLFrameHyperlinkEvent(evt);
              return;
            }

            performClick(e.getDescription());
          }
        };
  }

  private HtmlLinkManager getLinkManager() {
    return linkManager;
  }

  private Collection<RenderErrorModel.Issue> getIssues() {
    return Collections.unmodifiableCollection(issues);
  }

  private RenderErrorModel.Issue.Builder addIssue() {
    return new RenderErrorModel.Issue.Builder() {
      @Override
      public RenderErrorModel.Issue build() {
        RenderErrorModel.Issue built = super.build();
        issues.add(built);
        return built;
      }
    }.setLinkHandler(linkHandler);
  }

  private void performClick(String url) {
    linkManager.handleUrl(
        url,
        module,
        sourceFile,
        true,
        new HtmlLinkManager.RefreshableSurface() {
          @Override
          public void handleRefreshRenderUrl() {
            if (designSurface != null) {
              // TODO(b/321801969): Remove and replace with direct call when in repo.
              // Use reflection to getConfigurations() from designSurface. Can't call directly
              // because it returns an incompatible version of ImmutableCollection.
              // RenderUtils.clearCache(designSurface.getConfigurations()); would fail at runtime.
              try {
                Method getConfigurationsMethod =
                    EditorDesignSurface.class.getMethod("getConfigurations", null);
                Object configurations = getConfigurationsMethod.invoke(designSurface);
                Method clearCacheMethod =
                    RenderUtils.class.getMethod(
                        "clearCache", getConfigurationsMethod.getReturnType());
                clearCacheMethod.invoke(null, configurations);
              } catch (NoSuchMethodException ex) {
                throw new RuntimeException(
                    "Error using reflection to get getConfigurations() instance method: " + ex);
              } catch (IllegalAccessException ex) {
                throw new RuntimeException(
                    "Error accessing getConfigurations() instance method" + ex);
              } catch (InvocationTargetException ex) {
                throw new RuntimeException("Error invoking target getConfigurations(): " + ex);
              }
              designSurface.forceUserRequestedRefresh();
            }
          }

          @Override
          public void requestRender() {
            if (designSurface != null) {
              designSurface.forceUserRequestedRefresh();
            }
          }
        });
  }

  @Override
  public Collection<RenderErrorModel.Issue> reportIssues() {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null || !logger.hasErrors()) {
      return getIssues();
    }

    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      // TODO(b/284002829): Setup resource-module specific issue reporting
      return getIssues();
    }

    TargetMap targetMap = blazeProjectData.getTargetMap();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    AndroidResourceModule resourceModule =
        AndroidResourceModuleRegistry.getInstance(project).get(module);
    if (resourceModule == null) {
      return getIssues();
    }

    TargetIdeInfo target = targetMap.get(resourceModule.targetKey);
    if (target == null) {
      return getIssues();
    }

    reportGeneratedResources(resourceModule, targetMap, decoder);
    reportNonStandardAndroidManifestName(target, decoder);
    reportResourceTargetShouldDependOnClassTarget(target, targetMap, decoder);
    return getIssues();
  }

  /**
   * We can't find generated resources. If a layout uses them, the layout won't render correctly.
   */
  private void reportGeneratedResources(
      AndroidResourceModule resourceModule, TargetMap targetMap, ArtifactLocationDecoder decoder) {
    Map<String, Throwable> brokenClasses = logger.getBrokenClasses();
    if (brokenClasses == null || brokenClasses.isEmpty()) {
      return;
    }

    // Sorted entries for deterministic error message.
    SortedMap<ArtifactLocation, TargetIdeInfo> generatedResources =
        Maps.newTreeMap(getGeneratedResources(targetMap.get(resourceModule.targetKey)));

    for (TargetKey dependency : resourceModule.transitiveResourceDependencies) {
      generatedResources.putAll(getGeneratedResources(targetMap.get(dependency)));
    }

    if (generatedResources.isEmpty()) {
      return;
    }

    HtmlBuilder builder = new HtmlBuilder();
    builder.add("Generated resources will not be discovered by the IDE:");
    builder.beginList();
    for (Map.Entry<ArtifactLocation, TargetIdeInfo> entry : generatedResources.entrySet()) {
      ArtifactLocation resource = entry.getKey();
      TargetIdeInfo target = entry.getValue();
      builder.listItem().add(resource.getRelativePath()).add(" from ");
      addTargetLink(builder, target, decoder);
    }
    builder
        .endList()
        .add("Please avoid using generated resources, ")
        .addLink("then ", "sync the project", " ", getLinkManager().createSyncProjectUrl())
        .addLink("and ", "refresh the layout", ".", getLinkManager().createRefreshRenderUrl());
    addIssue()
        .setSeverity(HighlightSeverity.ERROR, HIGH_PRIORITY + 1) // Reported above broken classes
        .setSummary("Generated resources")
        .setHtmlContent(builder)
        .build();
  }

  private static SortedMap<ArtifactLocation, TargetIdeInfo> getGeneratedResources(
      TargetIdeInfo target) {
    if (target == null || target.getAndroidIdeInfo() == null) {
      return Collections.emptySortedMap();
    }
    SortedMap<ArtifactLocation, TargetIdeInfo> generatedResources = Maps.newTreeMap();
    generatedResources.putAll(
        target.getAndroidIdeInfo().getResources().stream()
            .map(AndroidResFolder::getRoot)
            .filter(ArtifactLocation::isGenerated)
            .collect(toImmutableMap(Function.identity(), resource -> target)));
    return generatedResources;
  }

  /**
   * When the Android manifest isn't AndroidManifest.xml, resolving resource IDs would fail. This
   * doesn't seem to be an issue if the manifest belongs to one of the target's dependencies.
   */
  private void reportNonStandardAndroidManifestName(
      TargetIdeInfo target, ArtifactLocationDecoder decoder) {
    if (target.getAndroidIdeInfo() == null || target.getAndroidIdeInfo().getManifest() == null) {
      return;
    }

    Map<String, Throwable> brokenClasses = logger.getBrokenClasses();
    if (brokenClasses == null || brokenClasses.isEmpty()) {
      return;
    }

    ArtifactLocation maniftestArtifactLocation = target.getAndroidIdeInfo().getManifest();

    File manifest =
        Preconditions.checkNotNull(
            OutputArtifactResolver.resolve(project, decoder, maniftestArtifactLocation),
            "Fail to find file %s",
            maniftestArtifactLocation.getRelativePath());
    if (manifest.getName().equals(ANDROID_MANIFEST_XML)) {
      return;
    }

    HtmlBuilder builder = new HtmlBuilder();
    addTargetLink(builder, target, decoder)
        .add(" uses a non-standard name for the Android manifest: ");
    String linkToManifest = HtmlLinkManagerCompat.createFilePositionUrl(manifest, -1, 0);
    if (linkToManifest != null) {
      builder.addLink(manifest.getName(), linkToManifest);
    } else {
      builder.newline().add(manifest.getPath());
    }
    // TODO: add a link to automatically rename the file and refactor all references.
    builder
        .newline()
        .add("Please rename it to ")
        .add(ANDROID_MANIFEST_XML)
        .addLink(", then ", "sync the project", "", getLinkManager().createSyncProjectUrl())
        .addLink(" and ", "refresh the layout", ".", getLinkManager().createRefreshRenderUrl());
    addIssue()
        .setSeverity(HighlightSeverity.ERROR, HIGH_PRIORITY + 1) // Reported above broken classes.
        .setSummary("Non-standard manifest name")
        .setHtmlContent(builder)
        .build();
  }

  /**
   * Blaze doesn't resolve class dependencies from resources until building the final
   * android_binary, so we could end up with resources that ultimately build correctly, but fail to
   * find their class dependencies during rendering in the layout editor.
   */
  @SuppressWarnings("rawtypes")
  private void reportResourceTargetShouldDependOnClassTarget(
      TargetIdeInfo target, TargetMap targetMap, ArtifactLocationDecoder decoder) {
    Set<String> missingClasses = logger.getMissingClasses();
    if (missingClasses == null || missingClasses.isEmpty()) {
      return;
    }

    // Sorted entries for deterministic error message.
    SortedSetMultimap<String, TargetKey> missingClassToTargetMap = TreeMultimap.create();

    SourceToTargetMap sourceToTargetMap = SourceToTargetMap.getInstance(project);
    ImmutableCollection transitiveDependencies =
        TransitiveDependencyMap.getInstance(project).getTransitiveDependencies(target.getKey());

    for (String missingClass : missingClasses) {
      File sourceFile = getSourceFileForClass(missingClass);
      if (sourceFile == null) {
        continue;
      }
      ImmutableCollection<TargetKey> sourceTargets =
          sourceToTargetMap.getRulesForSourceFile(sourceFile);
      if (sourceTargets.stream()
          .noneMatch(
              sourceTarget ->
                  sourceTarget.equals(target.getKey())
                      || transitiveDependencies.contains(sourceTarget))) {
        missingClassToTargetMap.putAll(missingClass, sourceTargets);
      }
    }

    if (missingClassToTargetMap.isEmpty()) {
      return;
    }

    HtmlBuilder builder = new HtmlBuilder();
    addTargetLink(builder, target, decoder)
        .add(" contains resource files that reference these classes:")
        .beginList();
    for (String missingClass : missingClassToTargetMap.keySet()) {
      builder
          .listItem()
          .addLink(missingClass, getLinkManager().createOpenClassUrl(missingClass))
          .add(" from ");
      for (TargetKey targetKey : missingClassToTargetMap.get(missingClass)) {
        addTargetLink(builder, targetMap.get(targetKey), decoder).add(" ");
      }
    }
    builder.endList().add("Please fix your dependencies so that ");
    addTargetLink(builder, target, decoder)
        .add(" correctly depends on these classes, ")
        .addLink("then ", "sync the project", " ", getLinkManager().createSyncProjectUrl())
        .addLink("and ", "refresh the layout", ".", getLinkManager().createRefreshRenderUrl())
        .newline()
        .newline()
        .addBold(
            "NOTE: blaze can still build with the incorrect dependencies "
                + "due to the way it handles resources, "
                + "but the layout editor needs them to be correct.");

    addIssue()
        .setSeverity(HighlightSeverity.ERROR, HIGH_PRIORITY + 1) // Reported above missing classes.
        .setSummary("Missing class dependencies")
        .setHtmlContent(builder)
        .build();
  }

  private File getSourceFileForClass(String className) {
    return ApplicationManager.getApplication()
        .runReadAction(
            (Computable<File>)
                () -> {
                  try {
                    PsiClass psiClass =
                        JavaPsiFacade.getInstance(project)
                            .findClass(className, GlobalSearchScope.projectScope(project));
                    if (psiClass == null) {
                      return null;
                    }
                    return VfsUtilCore.virtualToIoFile(
                        psiClass.getContainingFile().getVirtualFile());
                  } catch (IndexNotReadyException ignored) {
                    // We're in dumb mode. Abort! Abort!
                    return null;
                  }
                });
  }

  @CanIgnoreReturnValue
  private HtmlBuilder addTargetLink(
      HtmlBuilder builder, TargetIdeInfo target, ArtifactLocationDecoder decoder) {
    File buildFile =
        Preconditions.checkNotNull(
            OutputArtifactResolver.resolve(project, decoder, target.getBuildFile()),
            "Fail to find file %s",
            target.getBuildFile().getRelativePath());
    int line =
        ApplicationManager.getApplication()
            .runReadAction(
                (Computable<Integer>)
                    () -> {
                      PsiElement buildTargetPsi =
                          BuildReferenceManager.getInstance(project)
                              .resolveLabel(target.getKey().getLabel());
                      if (buildTargetPsi == null) {
                        return -1;
                      }
                      PsiFile psiFile = buildTargetPsi.getContainingFile();
                      if (psiFile == null) {
                        return -1;
                      }
                      return StringUtil.offsetToLineNumber(
                          psiFile.getText(), buildTargetPsi.getTextOffset());
                    });
    String url = HtmlLinkManagerCompat.createFilePositionUrl(buildFile, line, 0);
    if (url != null) {
      return builder.addLink(target.toString(), url);
    }
    return builder.add(target.toString());
  }
}
