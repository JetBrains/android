/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.AndroidModelView.ModuleNodeBuilder;
import com.android.tools.idea.gradle.util.ProxyUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import junit.framework.TestCase;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.builder.model.AndroidProject.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AndroidModelView}.
 */
public class AndroidModelViewTest extends TestCase {
  private static final String projectBasePath = new File("/p/b/p").getPath();

  public void testCreateTreeNode() throws Exception {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode("test");
    assertEquals(0, node.getChildCount());

    MyInterface proxyObject = ProxyUtil.reproxy(MyInterface.class, createProxyInstance(true));
    assertNotNull(proxyObject);

    ModuleNodeBuilder mockNodeBuilder = new ModuleNodeBuilder("", mock(AndroidGradleModel.class), projectBasePath);
    mockNodeBuilder.addProxyObject(node, proxyObject);
    assertEquals(11, node.getChildCount());

    DefaultMutableTreeNode childAtZero = (DefaultMutableTreeNode)node.getChildAt(0);
    assertEquals(0, childAtZero.getChildCount());
    assertEquals("File -> /a/sample/file", childAtZero.getUserObject().toString().replace('\\', '/'));

    DefaultMutableTreeNode childAtOne = (DefaultMutableTreeNode)node.getChildAt(1);
    assertEquals(0, childAtOne.getChildCount());
    assertEquals("FileUnderProject -> b/sample/file", childAtOne.getUserObject().toString().replace('\\', '/'));

    DefaultMutableTreeNode childAtTwo = (DefaultMutableTreeNode)node.getChildAt(2);
    assertEquals(0, childAtTwo.getChildCount());
    assertEquals("Name -> aName", childAtTwo.getUserObject());

    DefaultMutableTreeNode childAtThree = (DefaultMutableTreeNode)node.getChildAt(3);
    assertEquals(0, childAtThree.getChildCount());
    assertEquals("NativeBoolean -> true", childAtThree.getUserObject());

    DefaultMutableTreeNode childAtFour = (DefaultMutableTreeNode)node.getChildAt(4);
    assertEquals(0, childAtFour.getChildCount());
    assertEquals("doesNotExist -> Error: org.gradle.tooling.model.UnsupportedMethodException", childAtFour.getUserObject());

    // BooleanList and it's children
    DefaultMutableTreeNode childAtFive = (DefaultMutableTreeNode)node.getChildAt(5);
    assertEquals(2, childAtFive.getChildCount());
    assertEquals("BooleanList", childAtFive.getUserObject());

    DefaultMutableTreeNode booleanListChildAtZero = (DefaultMutableTreeNode)childAtFive.getChildAt(0);
    assertEquals(0, booleanListChildAtZero.getChildCount());
    assertEquals("false", booleanListChildAtZero.getUserObject());

    DefaultMutableTreeNode booleanListChildAtOne = (DefaultMutableTreeNode)childAtFive.getChildAt(1);
    assertEquals(0, booleanListChildAtOne.getChildCount());
    assertEquals("true", booleanListChildAtOne.getUserObject());

    // MapToProxy and it's children
    DefaultMutableTreeNode childAtSix = (DefaultMutableTreeNode)node.getChildAt(6);
    assertEquals(2, childAtSix.getChildCount());
    assertEquals("MapToProxy", childAtSix.getUserObject());

    DefaultMutableTreeNode mapToProxyChildAtZero = (DefaultMutableTreeNode)childAtSix.getChildAt(0);
    assertEquals(1, mapToProxyChildAtZero.getChildCount()); // The object value in the map.
    assertEquals("one", mapToProxyChildAtZero.getUserObject());

    DefaultMutableTreeNode mapToProxyChildAtOne = (DefaultMutableTreeNode)childAtSix.getChildAt(1);
    assertEquals(2, mapToProxyChildAtOne.getChildCount()); // The proxy object values in the map.
    assertEquals("two", mapToProxyChildAtOne.getUserObject());

    // ProxyCollection and it's children
    DefaultMutableTreeNode childAtSeven = (DefaultMutableTreeNode)node.getChildAt(7);
    assertEquals(1, childAtSeven.getChildCount());
    assertEquals("ProxyCollection", childAtSeven.getUserObject());

    DefaultMutableTreeNode ProxyCollectionChildAtZero = (DefaultMutableTreeNode)childAtSeven.getChildAt(0);
    assertEquals(11, ProxyCollectionChildAtZero.getChildCount()); // The child proxy object attributes.
    assertEquals("aName", ProxyCollectionChildAtZero.getUserObject());  // Name derived from child.

    // ProxyList and it's children
    DefaultMutableTreeNode childAtEight = (DefaultMutableTreeNode)node.getChildAt(8);
    assertEquals(1, childAtEight.getChildCount());
    assertEquals("ProxyList", childAtEight.getUserObject());

    DefaultMutableTreeNode ProxyListChildAtZero = (DefaultMutableTreeNode)childAtEight.getChildAt(0);
    assertEquals(11, ProxyListChildAtZero.getChildCount()); // The child proxy object attributes.
    assertEquals("aName", ProxyListChildAtZero.getUserObject());  // Name derived from child.

    // StringCollection and it's children
    DefaultMutableTreeNode childAtNine = (DefaultMutableTreeNode)node.getChildAt(9);
    assertEquals(3, childAtNine.getChildCount());
    assertEquals("StringCollection", childAtNine.getUserObject());

    DefaultMutableTreeNode stringCollectionChildAtZero = (DefaultMutableTreeNode)childAtNine.getChildAt(0);
    assertEquals(0, stringCollectionChildAtZero.getChildCount());
    assertEquals("one", stringCollectionChildAtZero.getUserObject());

    DefaultMutableTreeNode stringCollectionChildAtOne = (DefaultMutableTreeNode)childAtNine.getChildAt(1);
    assertEquals(0, stringCollectionChildAtOne.getChildCount());
    assertEquals("three", stringCollectionChildAtOne.getUserObject()); // Sorted alphabetically

    DefaultMutableTreeNode stringCollectionChildAtTwo = (DefaultMutableTreeNode)childAtNine.getChildAt(2);
    assertEquals(0, stringCollectionChildAtTwo.getChildCount());
    assertEquals("two", stringCollectionChildAtTwo.getUserObject());

    // StringSet and it's children
    DefaultMutableTreeNode childAtTen = (DefaultMutableTreeNode)node.getChildAt(10);
    assertEquals(2, childAtTen.getChildCount());
    assertEquals("StringSet", childAtTen.getUserObject());

    DefaultMutableTreeNode stringSetChildAtZero = (DefaultMutableTreeNode)childAtTen.getChildAt(0);
    assertEquals(0, stringSetChildAtZero.getChildCount());
    assertEquals("a", stringSetChildAtZero.getUserObject());

    DefaultMutableTreeNode stringSetChildAtOne = (DefaultMutableTreeNode)childAtTen.getChildAt(1);
    assertEquals(0, stringSetChildAtOne.getChildCount());
    assertEquals("b", stringSetChildAtOne.getUserObject());
  }

  public void testDefaultConfigNodeCustomization() throws Exception {
    AndroidProject androidProject = mock(AndroidProject.class);

    ProductFlavorContainer flavorContainer = mock(ProductFlavorContainer.class);
    when(androidProject.getDefaultConfig()).thenReturn(createProxyInstance(ProductFlavorContainer.class, flavorContainer));

    ProductFlavor flavor = mock(ProductFlavor.class);
    when(flavor.getName()).thenReturn("dummyFlavor");
    when(flavorContainer.getProductFlavor()).thenReturn(createProxyInstance(ProductFlavor.class, flavor));

    SourceProvider mainSourceProvider = mock(SourceProvider.class);
    when(mainSourceProvider.getName()).thenReturn("dummySourceProvider");
    when(flavorContainer.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, mainSourceProvider));

    SourceProviderContainer extraSourceProviderContainer1 = mock(SourceProviderContainer.class);
    SourceProvider extraSourceProvider1 = mock(SourceProvider.class);
    when(extraSourceProvider1.getName()).thenReturn("extraSourceProvider1");
    when(extraSourceProviderContainer1.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, extraSourceProvider1));
    SourceProviderContainer extraSourceProviderContainer2 = mock(SourceProviderContainer.class);
    SourceProvider extraSourceProvider2 = mock(SourceProvider.class);
    when(extraSourceProvider2.getName()).thenReturn("extraSourceProvider2");
    when(extraSourceProviderContainer2.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, extraSourceProvider2));
    when(flavorContainer.getExtraSourceProviders()).thenReturn(ImmutableList.of(
      createProxyInstance(SourceProviderContainer.class, extraSourceProviderContainer1),
      createProxyInstance(SourceProviderContainer.class, extraSourceProviderContainer2)));

    AndroidProject reproxyProject = ProxyUtil.reproxy(AndroidProject.class, createProxyInstance(AndroidProject.class, androidProject));
    assertNotNull(reproxyProject);

    AndroidGradleModel androidModel = mock(AndroidGradleModel.class);
    when(androidModel.waitForAndGetProxyAndroidProject()).thenReturn(reproxyProject);
    ModuleNodeBuilder nodeBuilder = new ModuleNodeBuilder("test", androidModel, projectBasePath);
    DefaultMutableTreeNode node = nodeBuilder.getNode();
    assertTrue(node.getChildCount() > 0);

    DefaultMutableTreeNode defaultConfigNode = (DefaultMutableTreeNode)node.getChildAt(node.getChildCount() - 1);
    assertEquals("DefaultConfig", defaultConfigNode.getUserObject().toString());
    assertTrue(defaultConfigNode.getChildCount() > 0);

    DefaultMutableTreeNode sourceProvidersNode =
      (DefaultMutableTreeNode)defaultConfigNode.getChildAt(defaultConfigNode.getChildCount() - 1);
    assertEquals("SourceProviders", sourceProvidersNode.getUserObject().toString());
    assertEquals(3, sourceProvidersNode.getChildCount());
    assertEquals("dummySourceProvider", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(0)).getUserObject().toString());
    assertEquals("extraSourceProvider1", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(1)).getUserObject().toString());
    assertEquals("extraSourceProvider2", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(2)).getUserObject().toString());

    DefaultMutableTreeNode sdkVersionsNode = (DefaultMutableTreeNode)defaultConfigNode.getChildAt(defaultConfigNode.getChildCount() - 2);
    assertEquals("SdkVersions", sdkVersionsNode.getUserObject().toString());
    assertEquals(3, sdkVersionsNode.getChildCount()); // Min, Max and Target Sdk versions.

    DefaultMutableTreeNode nameNode = null;
    for (int i = 0; i < defaultConfigNode.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)defaultConfigNode.getChildAt(i);
      if (child.getUserObject().toString().startsWith("Name ->")) {
        nameNode = child;
        break;
      }
    }
    assertNotNull(nameNode);
    // Verifies that the flavor node is inlined under default config.
    assertEquals("Name -> dummyFlavor", nameNode.getUserObject().toString());
  }

  public void testBuildTypesNodeCustomization() throws Exception {
    AndroidProject androidProject = mock(AndroidProject.class);

    BuildTypeContainer buildTypeContainer = mock(BuildTypeContainer.class);
    when(androidProject.getBuildTypes()).thenReturn(ImmutableList.of(createProxyInstance(BuildTypeContainer.class, buildTypeContainer)));

    BuildType buildtype = mock(BuildType.class);
    when(buildtype.getName()).thenReturn("dummyBuildType");
    when(buildTypeContainer.getBuildType()).thenReturn(createProxyInstance(BuildType.class, buildtype));

    SourceProvider mainSourceProvider = mock(SourceProvider.class);
    when(mainSourceProvider.getName()).thenReturn("dummySourceProvider");
    when(buildTypeContainer.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, mainSourceProvider));

    SourceProviderContainer extraSourceProviderContainer1 = mock(SourceProviderContainer.class);
    SourceProvider extraSourceProvider1 = mock(SourceProvider.class);
    when(extraSourceProvider1.getName()).thenReturn("extraSourceProvider1");
    when(extraSourceProviderContainer1.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, extraSourceProvider1));
    SourceProviderContainer extraSourceProviderContainer2 = mock(SourceProviderContainer.class);
    SourceProvider extraSourceProvider2 = mock(SourceProvider.class);
    when(extraSourceProvider2.getName()).thenReturn("extraSourceProvider2");
    when(extraSourceProviderContainer2.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, extraSourceProvider2));
    when(buildTypeContainer.getExtraSourceProviders()).thenReturn(ImmutableList.of(
      createProxyInstance(SourceProviderContainer.class, extraSourceProviderContainer1),
      createProxyInstance(SourceProviderContainer.class, extraSourceProviderContainer2)));

    AndroidProject reproxyProject = ProxyUtil.reproxy(AndroidProject.class, createProxyInstance(AndroidProject.class, androidProject));
    assertNotNull(reproxyProject);

    AndroidGradleModel androidModel = mock(AndroidGradleModel.class);
    when(androidModel.waitForAndGetProxyAndroidProject()).thenReturn(reproxyProject);
    ModuleNodeBuilder nodeBuilder = new ModuleNodeBuilder("test", androidModel, projectBasePath);
    DefaultMutableTreeNode node = nodeBuilder.getNode();
    assertTrue(node.getChildCount() > 0);

    DefaultMutableTreeNode buildTypesNode = (DefaultMutableTreeNode)node.getChildAt(node.getChildCount() - 1);
    assertEquals("BuildTypes", buildTypesNode.getUserObject().toString());
    assertEquals(1, buildTypesNode.getChildCount());

    DefaultMutableTreeNode buildTypeNode = (DefaultMutableTreeNode)buildTypesNode.getChildAt(0);
    assertEquals("dummyBuildType", buildTypeNode.getUserObject().toString());
    assertTrue(buildTypeNode.getChildCount() > 0);

    DefaultMutableTreeNode sourceProvidersNode = (DefaultMutableTreeNode)buildTypeNode.getChildAt(buildTypeNode.getChildCount() - 1);
    assertEquals("SourceProviders", sourceProvidersNode.getUserObject().toString());
    assertEquals(3, sourceProvidersNode.getChildCount());
    assertEquals("dummySourceProvider", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(0)).getUserObject().toString());
    assertEquals("extraSourceProvider1", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(1)).getUserObject().toString());
    assertEquals("extraSourceProvider2", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(2)).getUserObject().toString());
  }

  public void testProductFlavorsNodeCustomization() throws Exception {
    AndroidProject androidProject = mock(AndroidProject.class);

    ProductFlavorContainer flavorContainer = mock(ProductFlavorContainer.class);
    when(androidProject.getProductFlavors())
      .thenReturn(ImmutableList.of(createProxyInstance(ProductFlavorContainer.class, flavorContainer)));

    ProductFlavor flavor = mock(ProductFlavor.class);
    when(flavor.getName()).thenReturn("dummyFlavor");
    when(flavorContainer.getProductFlavor()).thenReturn(createProxyInstance(ProductFlavor.class, flavor));

    SourceProvider mainSourceProvider = mock(SourceProvider.class);
    when(mainSourceProvider.getName()).thenReturn("dummySourceProvider");
    when(flavorContainer.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, mainSourceProvider));

    SourceProviderContainer extraSourceProviderContainer1 = mock(SourceProviderContainer.class);
    SourceProvider extraSourceProvider1 = mock(SourceProvider.class);
    when(extraSourceProvider1.getName()).thenReturn("extraSourceProvider1");
    when(extraSourceProviderContainer1.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, extraSourceProvider1));
    SourceProviderContainer extraSourceProviderContainer2 = mock(SourceProviderContainer.class);
    SourceProvider extraSourceProvider2 = mock(SourceProvider.class);
    when(extraSourceProvider2.getName()).thenReturn("extraSourceProvider2");
    when(extraSourceProviderContainer2.getSourceProvider()).thenReturn(createProxyInstance(SourceProvider.class, extraSourceProvider2));
    when(flavorContainer.getExtraSourceProviders()).thenReturn(ImmutableList.of(
      createProxyInstance(SourceProviderContainer.class, extraSourceProviderContainer1),
      createProxyInstance(SourceProviderContainer.class, extraSourceProviderContainer2)));

    AndroidProject reproxyProject = ProxyUtil.reproxy(AndroidProject.class, createProxyInstance(AndroidProject.class, androidProject));
    assertNotNull(reproxyProject);

    AndroidGradleModel androidModel = mock(AndroidGradleModel.class);
    when(androidModel.waitForAndGetProxyAndroidProject()).thenReturn(reproxyProject);
    ModuleNodeBuilder nodeBuilder = new ModuleNodeBuilder("test", androidModel, projectBasePath);
    DefaultMutableTreeNode node = nodeBuilder.getNode();
    assertTrue(node.getChildCount() > 0);

    DefaultMutableTreeNode productFlavorsNode = (DefaultMutableTreeNode)node.getChildAt(node.getChildCount() - 1);
    assertEquals("ProductFlavors", productFlavorsNode.getUserObject().toString());
    assertEquals(1, productFlavorsNode.getChildCount());

    DefaultMutableTreeNode productFlavorNode = (DefaultMutableTreeNode)productFlavorsNode.getChildAt(0);
    assertEquals("dummyFlavor", productFlavorNode.getUserObject().toString());
    assertTrue(productFlavorNode.getChildCount() > 0);

    DefaultMutableTreeNode sourceProvidersNode =
      (DefaultMutableTreeNode)productFlavorNode.getChildAt(productFlavorNode.getChildCount() - 1);
    assertEquals("SourceProviders", sourceProvidersNode.getUserObject().toString());
    assertEquals(3, sourceProvidersNode.getChildCount());
    assertEquals("dummySourceProvider", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(0)).getUserObject().toString());
    assertEquals("extraSourceProvider1", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(1)).getUserObject().toString());
    assertEquals("extraSourceProvider2", ((DefaultMutableTreeNode)sourceProvidersNode.getChildAt(2)).getUserObject().toString());

    DefaultMutableTreeNode sdkVersionsNode = (DefaultMutableTreeNode)productFlavorNode.getChildAt(productFlavorNode.getChildCount() - 2);
    assertEquals("SdkVersions", sdkVersionsNode.getUserObject().toString());
    assertEquals(3, sdkVersionsNode.getChildCount()); // Min, Max and Target Sdk versions.
  }

  public void testVariantsNodeCustomization() throws Exception {
    AndroidProject androidProject = mock(AndroidProject.class);

    Variant variant = mock(Variant.class);
    when(variant.getName()).thenReturn("dummyVariant");
    when(androidProject.getVariants()).thenReturn(ImmutableList.of(createProxyInstance(Variant.class, variant)));

    AndroidArtifact mainArtifact = mock(AndroidArtifact.class);
    when(mainArtifact.getName()).thenReturn(ARTIFACT_MAIN);
    when(variant.getMainArtifact()).thenReturn(createProxyInstance(AndroidArtifact.class, mainArtifact));

    AndroidArtifact extraAndroidArtifact = mock(AndroidArtifact.class);
    when(extraAndroidArtifact.getName()).thenReturn(ARTIFACT_ANDROID_TEST);
    when(variant.getExtraAndroidArtifacts()).thenReturn(ImmutableList.of(createProxyInstance(AndroidArtifact.class, extraAndroidArtifact)));

    JavaArtifact extraJavaArtifact = mock(JavaArtifact.class);
    when(extraJavaArtifact.getName()).thenReturn(ARTIFACT_UNIT_TEST);
    when(variant.getExtraJavaArtifacts()).thenReturn(ImmutableList.of(createProxyInstance(JavaArtifact.class, extraJavaArtifact)));

    AndroidProject reproxyProject = ProxyUtil.reproxy(AndroidProject.class, createProxyInstance(AndroidProject.class, androidProject));
    assertNotNull(reproxyProject);

    AndroidGradleModel androidModel = mock(AndroidGradleModel.class);
    when(androidModel.waitForAndGetProxyAndroidProject()).thenReturn(reproxyProject);
    when(androidModel.getVariantNames()).thenReturn(ImmutableList.of("dummyVariant"));
    List<SourceProvider> mockSourceProviders =
      ImmutableList.of(createMockSourceProvider("src1"), createMockSourceProvider("src2"), createMockSourceProvider("src3"));
    when(androidModel.getMainSourceProviders("dummyVariant")).thenReturn(mockSourceProviders);
    when(androidModel.getTestSourceProviders("dummyVariant", ARTIFACT_ANDROID_TEST)).thenReturn(mockSourceProviders);
    when(androidModel.getTestSourceProviders("dummyVariant", ARTIFACT_UNIT_TEST)).thenReturn(mockSourceProviders);

    ModuleNodeBuilder nodeBuilder = new ModuleNodeBuilder("test", androidModel, projectBasePath);
    DefaultMutableTreeNode node = nodeBuilder.getNode();
    assertTrue(node.getChildCount() > 0);

    DefaultMutableTreeNode variantsNode = (DefaultMutableTreeNode)node.getChildAt(node.getChildCount() - 1);
    assertEquals("Variants", variantsNode.getUserObject().toString());
    assertEquals(1, variantsNode.getChildCount());

    DefaultMutableTreeNode variantNode = (DefaultMutableTreeNode)variantsNode.getChildAt(0);
    assertEquals("dummyVariant", variantNode.getUserObject().toString());
    assertTrue(variantNode.getChildCount() > 0);

    DefaultMutableTreeNode artifactsNode = (DefaultMutableTreeNode)variantNode.getChildAt(variantNode.getChildCount() - 1);
    assertEquals("Artifacts", artifactsNode.getUserObject().toString());
    assertEquals(3, artifactsNode.getChildCount());

    DefaultMutableTreeNode androidTestNode = (DefaultMutableTreeNode)artifactsNode.getChildAt(0);
    assertEquals(ARTIFACT_ANDROID_TEST, androidTestNode.getUserObject().toString());
    DefaultMutableTreeNode sourcesNode = (DefaultMutableTreeNode)androidTestNode.getChildAt(androidTestNode.getChildCount() - 1);
    verifySourcesNode(sourcesNode);


    DefaultMutableTreeNode mainNode = (DefaultMutableTreeNode)artifactsNode.getChildAt(1);
    assertEquals(ARTIFACT_MAIN, mainNode.getUserObject().toString());
    sourcesNode = (DefaultMutableTreeNode)mainNode.getChildAt(mainNode.getChildCount() - 1);
    verifySourcesNode(sourcesNode);

    DefaultMutableTreeNode unitTestNode = (DefaultMutableTreeNode)artifactsNode.getChildAt(2);
    assertEquals(ARTIFACT_UNIT_TEST, unitTestNode.getUserObject().toString());
    sourcesNode = (DefaultMutableTreeNode)unitTestNode.getChildAt(unitTestNode.getChildCount() - 1);
    verifySourcesNode(sourcesNode);
  }

  private static SourceProvider createMockSourceProvider(@NotNull String srcDirName) {
    SourceProvider provider = mock(SourceProvider.class);
    when(provider.getManifestFile()).thenReturn(new File(projectBasePath + "/" + srcDirName + "/ManifestFile.xml"));
    when(provider.getJavaDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/java")));
    when(provider.getCDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/jni")));
    when(provider.getJniLibsDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/jniLib")));
    when(provider.getResDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/res")));
    when(provider.getAidlDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/aidl")));
    when(provider.getResourcesDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/resources")));
    when(provider.getAssetsDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/assets")));
    when(provider.getRenderscriptDirectories()).thenReturn(ImmutableList.of(new File(projectBasePath + "/" + srcDirName + "/rs")));
    return provider;
  }

  private static void verifySourcesNode(@NotNull DefaultMutableTreeNode sourcesNode) {
    assertEquals("Sources", sourcesNode.getUserObject().toString());
    assertEquals(9, sourcesNode.getChildCount());
    DefaultMutableTreeNode manifestFilesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(0);
    assertEquals("ManifestFiles", manifestFilesNode.getUserObject().toString());
    verifySourceDirectoryPaths(manifestFilesNode, "ManifestFile.xml");

    DefaultMutableTreeNode javaDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(1);
    assertEquals("JavaDirectories", javaDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(javaDirectoriesNode, "java");

    DefaultMutableTreeNode jniDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(2);
    assertEquals("JniDirectories", jniDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(jniDirectoriesNode, "jni");

    DefaultMutableTreeNode jniLibsDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(3);
    assertEquals("JniLibsDirectories", jniLibsDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(jniLibsDirectoriesNode, "jniLib");

    DefaultMutableTreeNode resDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(4);
    assertEquals("ResDirectories", resDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(resDirectoriesNode, "res");

    DefaultMutableTreeNode aidlDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(5);
    assertEquals("AidlDirectories", aidlDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(aidlDirectoriesNode, "aidl");

    DefaultMutableTreeNode resourcesDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(6);
    assertEquals("ResourcesDirectories", resourcesDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(resourcesDirectoriesNode, "resources");

    DefaultMutableTreeNode assetsDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(7);
    assertEquals("AssetsDirectories", assetsDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(assetsDirectoriesNode, "assets");

    DefaultMutableTreeNode renderscriptDirectoriesNode = (DefaultMutableTreeNode)sourcesNode.getChildAt(8);
    assertEquals("RenderscriptDirectories", renderscriptDirectoriesNode.getUserObject().toString());
    verifySourceDirectoryPaths(renderscriptDirectoriesNode, "rs");
  }

  private static void verifySourceDirectoryPaths(@NotNull DefaultMutableTreeNode dirNode, @NotNull String dirType) {
    assertEquals(3, dirNode.getChildCount());
    dirType = File.separator + dirType;
    assertEquals("src1" + dirType, ((DefaultMutableTreeNode)dirNode.getChildAt(0)).getUserObject().toString());
    assertEquals("src2" + dirType, ((DefaultMutableTreeNode)dirNode.getChildAt(1)).getUserObject().toString());
    assertEquals("src3" + dirType, ((DefaultMutableTreeNode)dirNode.getChildAt(2)).getUserObject().toString());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  private static <T> T createProxyInstance(final Class clazz, final T delegate) {
    return (T)Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
      @Override
      public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
        try {
          return method.invoke(delegate, objects);
        }
        catch (InvocationTargetException e) {
          throw e.getCause();
        }
      }
    });
  }

  private MyInterface createProxyInstance(boolean recurse) {
    final MyInterfaceImpl delegate = new MyInterfaceImpl(recurse);
    return createProxyInstance(MyInterface.class, delegate);
  }

  @SuppressWarnings("unused") // accessed via reflection
  public interface MyInterface {
    String getName();

    File getFile();

    File getFileUnderProject();

    boolean getNativeBoolean();

    @Nullable
    Collection<String> getStringCollection();

    @Nullable
    Collection<MyInterface> getProxyCollection();

    @Nullable
    List<Boolean> getBooleanList();

    @Nullable
    List<MyInterface> getProxyList();

    @Nullable
    Set<String> getStringSet();

    @Nullable
    Map<String, Collection<MyInterface>> getMapToProxy();

    boolean doesNotExist() throws UnsupportedMethodException;
  }

  public class MyInterfaceImpl implements MyInterface {

    final boolean recurse;

    MyInterfaceImpl(boolean recurse) {
      this.recurse = recurse;
    }

    @Override
    public String getName() {
      return "aName";
    }

    @Override
    public File getFile() {
      return new File("/a/sample/file");
    }

    @Override
    public File getFileUnderProject() {
      return new File(projectBasePath + "/b/sample/file");
    }

    @Override
    public boolean getNativeBoolean() {
      return true;
    }

    @Override
    public Collection<String> getStringCollection() {
      return Lists.newArrayList("one", "two", "three");
    }

    @Override
    public Collection<MyInterface> getProxyCollection() {
      return recurse ? Sets.newHashSet(createProxyInstance(false)) : null;
    }

    @Override
    public List<Boolean> getBooleanList() {
      return ImmutableList.of(false, true);
    }

    @Override
    public List<MyInterface> getProxyList() {
      return recurse ? Lists.newArrayList(createProxyInstance(false)) : null;
    }

    @Override
    public Set<String> getStringSet() {
      return Sets.newHashSet("a", "a", "b");
    }

    @Override
    public Map<String, Collection<MyInterface>> getMapToProxy() {
      if (!recurse) return null;

      return ImmutableMap.<String, Collection<MyInterface>>of("one", Sets.<MyInterface>newHashSet(new MyInterfaceImpl(false)), "two",
                                                              Lists.newArrayList(createProxyInstance(false), createProxyInstance(false)));
    }

    @Override
    public boolean doesNotExist() throws UnsupportedMethodException {
      throw new UnsupportedMethodException("This method doesn't exist");
    }
  }
}
