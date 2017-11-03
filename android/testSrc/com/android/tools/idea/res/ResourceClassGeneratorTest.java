/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceRepository;
import com.android.ide.common.res2.ResourceTable;
import com.android.ide.common.resources.TestResourceRepository;
import com.android.resources.ResourceType;
import com.android.util.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static com.android.tools.idea.gradle.project.model.AndroidModuleModel.EXPLODED_AAR;
import static java.io.File.separatorChar;

public class ResourceClassGeneratorTest extends AndroidTestCase {
  private static final String LIBRARY_NAME = "com.test:test-library:1.0.0";

  public void test() throws Exception {
    final ResourceRepository repository = TestResourceRepository.createRes2(new Object[]{
      "layout/layout1.xml", "<!--contents doesn't matter-->",

      "layout-land/layout1.xml", "<!--contents doesn't matter-->",

      "values/styles.xml", "" +
                           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<resources>\n" +
                           "    <style name=\"MyTheme.Dark\" parent=\"android:Theme.Light\">\n" +
                           "        <item name=\"android:textColor\">#999999</item>\n" +
                           "        <item name=\"foo\">?android:colorForeground</item>\n" +
                           "    </style>\n" +
                           "    <declare-styleable name=\"GridLayout_Layout\">\n" +
                           "        <attr name=\"android:layout_width\" />\n" +
                           "        <attr name=\"android:layout_height\" />\n" +
                           "        <attr name=\"layout_columnSpan\" format=\"integer\" min=\"1\" />\n" +
                           "        <attr name=\"layout_gravity\">\n" +
                           "            <flag name=\"top\" value=\"0x30\" />\n" +
                           "            <flag name=\"bottom\" value=\"0x50\" />\n" +
                           "            <flag name=\"center_vertical\" value=\"0x10\" />\n" +
                           "        </attr>\n" +
                           "    </declare-styleable>\n" +
                           "</resources>\n",

      "values/strings.xml", "" +
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<resources>\n" +
                            "    <item type=\"id\" name=\"action_bar_refresh\" />\n" +
                            "    <item type=\"dimen\" name=\"dialog_min_width_major\">45%</item>\n" +
                            "    <string name=\"show_all_apps\">All</string>\n" +
                            "    <string name=\"menu_wallpaper\">Wallpaper</string>\n" +
                            "</resources>\n",

      "values-es/strings.xml", "" +
                               "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                               "<resources>\n" +
                               "    <string name=\"show_all_apps\">Todo</string>\n" +
                               "</resources>\n",});
    LocalResourceRepository resources = new LocalResourceRepositoryDelegate("test", repository);
    AppResourceRepository appResources = new AppResourceRepository(myFacet, Collections.singletonList(resources),
                                                                   Collections.emptyList());

    ResourceClassGenerator generator = ResourceClassGenerator.create(appResources);
    assertNotNull(generator);
    String name = "my.test.pkg.R";
    Class<?> clz = generateClass(generator, name);
    assertNotNull(clz);
    assertEquals(name, clz.getName());
    assertTrue(Modifier.isPublic(clz.getModifiers()));
    assertTrue(Modifier.isFinal(clz.getModifiers()));
    assertFalse(Modifier.isInterface(clz.getModifiers()));
    Object r = clz.newInstance();
    assertNotNull(r);

    name = "my.test.pkg.R$string";
    clz = generateClass(generator, name);
    assertNotNull(clz);
    assertEquals(name, clz.getName());
    assertTrue(Modifier.isPublic(clz.getModifiers()));
    assertTrue(Modifier.isFinal(clz.getModifiers()));
    assertFalse(Modifier.isInterface(clz.getModifiers()));

    try {
      clz.getField("nonexistent");
      fail("Shouldn't find nonexistent fields");
    } catch (NoSuchFieldException e) {
      // pass
    }
    Field field1 = clz.getField("menu_wallpaper");
    Object value1 = field1.get(null);
    assertEquals(Integer.TYPE, field1.getType());
    assertNotNull(value1);
    assertEquals(2, clz.getFields().length);
    Field field2 = clz.getField("show_all_apps");
    assertNotNull(field2);
    assertEquals(Integer.TYPE, field2.getType());
    assertTrue(Modifier.isPublic(field2.getModifiers()));
    assertTrue(Modifier.isFinal(field2.getModifiers()));
    assertTrue(Modifier.isStatic(field2.getModifiers()));
    assertFalse(Modifier.isSynchronized(field2.getModifiers()));
    assertFalse(Modifier.isTransient(field2.getModifiers()));
    assertFalse(Modifier.isStrict(field2.getModifiers()));
    assertFalse(Modifier.isVolatile(field2.getModifiers()));
    r = clz.newInstance();
    assertNotNull(r);

    // Make sure the id's match what we've dynamically allocated in the resource repository
    @SuppressWarnings("deprecation")
    Pair<ResourceType,String> pair = appResources.resolveResourceId((Integer)clz.getField("menu_wallpaper").get(null));
    assertNotNull(pair);
    assertEquals(ResourceType.STRING, pair.getFirst());
    assertEquals("menu_wallpaper", pair.getSecond());
    assertEquals(clz.getField("menu_wallpaper").get(null), appResources.getResourceId(ResourceType.STRING, "menu_wallpaper"));
    assertEquals(clz.getField("show_all_apps").get(null), appResources.getResourceId(ResourceType.STRING, "show_all_apps"));

    // Test attr class!
    name = "my.test.pkg.R$attr";
    clz = generateClass(generator, name);
    assertNotNull(clz);
    assertEquals(name, clz.getName());
    assertTrue(Modifier.isPublic(clz.getModifiers()));
    assertTrue(Modifier.isFinal(clz.getModifiers()));
    assertFalse(Modifier.isInterface(clz.getModifiers()));
    assertEquals(2, clz.getFields().length);
    field1 = clz.getField("layout_gravity");
    assertNotNull(field1);
    Object gravityValue = field1.get(null);
    Object layoutColumnSpanValue = clz.getField("layout_columnSpan").get(null);

    // Test style class
    styleTest(generator);
    // Run the same test to check caching.
    styleTest(generator);

    // Test styleable class!
    styleableTest(generator, gravityValue, layoutColumnSpanValue);
    // Run the same test again to ensure that caching is working as expected.
    styleableTest(generator, gravityValue, layoutColumnSpanValue);


    name = "my.test.pkg.R$id";
    clz = generateClass(generator, name);
    assertNotNull(clz);
    r = clz.newInstance();
    assertNotNull(r);
    assertEquals(name, clz.getName());
    // getEnclosingClass() results in generating all R classes. So, this should be called at the end
    // so that tests for caching work as expected.
    Class<?> enclosingClass = clz.getEnclosingClass();
    assertNotNull(enclosingClass);

    // TODO: Flag and enum values should also be created as id's by the ValueResourceParser
    //assertNotNull(clz.getField("top"));
    //assertNotNull(clz.getField("bottom"));
    //assertNotNull(clz.getField("center_vertical"));
  }

  public void testStyleableMerge() throws Exception {
    final ResourceRepository repositoryA = TestResourceRepository.createRes2(new Object[]{
      "values/styles.xml", "" +
                           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<resources>\n" +
                           "    <attr name=\"app_declared_attr\" />\n" +
                           "    <declare-styleable name=\"Styleable1\">\n" +
                           "    </declare-styleable>\n" +
                           "    <declare-styleable name=\"Styleable.with.dots\">\n" +
                           "        <attr name=\"app_declared_attr\" />\n" +
                           "        <attr name=\"some_attr\" />\n" + // Duplicate attr
                           "        <attr name=\"android:layout_height\" />\n" +
                           "    </declare-styleable>\n" +
                           "    <declare-styleable name=\"AppStyleable\">\n" +
                           "    </declare-styleable>" +
                           "</resources>\n"});
    LocalResourceRepository resourcesA = new LocalResourceRepositoryDelegate("A", repositoryA);
    String aarPath = AndroidTestBase.getTestDataPath() + separatorChar +
                     "rendering" + separatorChar +
                     EXPLODED_AAR + separatorChar +
                     "my_aar_lib" + separatorChar +
                     "res";
    FileResourceRepository libraryRepository = FileResourceRepository.get(new File(aarPath), LIBRARY_NAME);
    AppResourceRepository appResources = new AppResourceRepository(myFacet, ImmutableList.of(resourcesA, libraryRepository),
                                                                   Collections.singletonList(libraryRepository));

    // 3 declared in the library, 3 declared in the "project", 2 of them are duplicated so:
    //
    //    1 unique styleable from the app
    //    1 unique styleable from the library
    //    2 styles declared in both
    //------------------------------------------
    //    4 total styles
    assertEquals(4, appResources.getItemsOfType(ResourceType.DECLARE_STYLEABLE).size());

    ResourceClassGenerator generator = ResourceClassGenerator.create(appResources);
    assertNotNull(generator);
    String name = "my.test.pkg.R";
    Class<?> clz = generateClass(generator, name);
    assertNotNull(clz);
    assertEquals(name, clz.getName());
    assertNotNull(clz.newInstance());

    name = "my.test.pkg.R$styleable";
    clz = generateClass(generator, name);
    assertNotNull(clz);
    assertEquals(name, clz.getName());
    String rStyleable = Arrays.stream(clz.getDeclaredFields()).map(Field::toString).reduce((a, b) -> a + "\n" + b).orElse("");
    assertEquals(
      "public static final int[] my.test.pkg.R$styleable.Styleable_with_underscore\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_underscore_app_attr1\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_underscore_app_attr2\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_underscore_app_attr3\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_underscore_android_framework_attr1\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_underscore_android_framework_attr2\n" +
      "public static final int[] my.test.pkg.R$styleable.Styleable_with_dots\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_dots_some_attr\n" + // Duplicated attr
      "public static final int my.test.pkg.R$styleable.Styleable_with_dots_app_declared_attr\n" +
      "public static final int my.test.pkg.R$styleable.Styleable_with_dots_android_layout_height\n" +
      "public static final int[] my.test.pkg.R$styleable.AppStyleable\n" +
      "public static final int[] my.test.pkg.R$styleable.Styleable1\n" +
      "public static final int my.test.pkg.R$styleable.Styleable1_some_attr",
      rStyleable);
    assertNotNull(clz.newInstance());

    name = "my.test.pkg.R$attr";
    clz = generateClass(generator, name);
    assertNotNull(clz);
    assertEquals(name, clz.getName());
    String rAttr = Arrays.stream(clz.getDeclaredFields()).map(Field::toString).reduce((a, b) -> a + "\n" + b).orElse("");
    assertEquals("public static final int my.test.pkg.R$attr.app_declared_attr", rAttr);
    assertNotNull(clz.newInstance());
  }

  private static class LocalResourceRepositoryDelegate extends LocalResourceRepository {

    private final ResourceRepository myDelegate;

    protected LocalResourceRepositoryDelegate(@NotNull String displayName, ResourceRepository delegate) {
      super(displayName);
      myDelegate = delegate;
    }

    @NonNull
    @Override
    protected ResourceTable getFullTable() {
      return myDelegate.getItems();
    }

    @Nullable
    @Override
    protected ListMultimap<String, ResourceItem> getMap(@Nullable String namespace, @NonNull ResourceType type, boolean create) {
      return myDelegate.getItems().get(namespace, type);
    }

    @NonNull
    @Override
    public Set<String> getNamespaces() {
      return myDelegate.getNamespaces();
    }

    @NotNull
    @Override
    protected Set<VirtualFile> computeResourceDirs() {
      return ImmutableSet.of();
    }
  }

  private static void styleTest(ResourceClassGenerator generator)
    throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    String name;
    Class<?> clz;
    name = "my.test.pkg.R$style";
    clz = generateClass(generator, name);
    assertNotNull(clz);
    clz.newInstance();
    assertEquals(name, clz.getName());
    assertTrue(Modifier.isPublic(clz.getModifiers()));
    assertTrue(Modifier.isFinal(clz.getModifiers()));
    assertFalse(Modifier.isInterface(clz.getModifiers()));
  }

  private static void styleableTest(ResourceClassGenerator generator, Object gravityValue, Object layoutColumnSpanValue)
    throws Exception {
    String name = "my.test.pkg.R$styleable";
    Class<?> clz = generateClass(generator, name);
    assertNotNull(clz);
    Object r = clz.newInstance();
    assertEquals(name, clz.getName());
    assertTrue(Modifier.isPublic(clz.getModifiers()));
    assertTrue(Modifier.isFinal(clz.getModifiers()));
    assertFalse(Modifier.isInterface(clz.getModifiers()));

    try {
      clz.getField("nonexistent");
      fail("Shouldn't find nonexistent fields");
    } catch (NoSuchFieldException e) {
      // pass
    }
    Field field1 = clz.getField("GridLayout_Layout");
    Object value1 = field1.get(null);
    assertEquals("[I", field1.getType().getName());
    assertNotNull(value1);
    assertEquals(5, clz.getFields().length);
    Field field2 = clz.getField("GridLayout_Layout_android_layout_height");
    assertNotNull(field2);
    assertNotNull(clz.getField("GridLayout_Layout_android_layout_width"));
    assertNotNull(clz.getField("GridLayout_Layout_layout_columnSpan"));
    assertEquals(Integer.TYPE, field2.getType());
    assertTrue(Modifier.isPublic(field2.getModifiers()));
    assertTrue(Modifier.isFinal(field2.getModifiers()));
    assertTrue(Modifier.isStatic(field2.getModifiers()));
    assertFalse(Modifier.isSynchronized(field2.getModifiers()));
    assertFalse(Modifier.isTransient(field2.getModifiers()));
    assertFalse(Modifier.isStrict(field2.getModifiers()));
    assertFalse(Modifier.isVolatile(field2.getModifiers()));

    int[] indices = (int[])clz.getField("GridLayout_Layout").get(r);

    Object layoutColumnSpanIndex = clz.getField("GridLayout_Layout_layout_columnSpan").get(null);
    assertTrue(layoutColumnSpanIndex instanceof Integer);
    int id = indices[(Integer)layoutColumnSpanIndex];
    assertEquals(id, layoutColumnSpanValue);

    Object gravityIndex = clz.getField("GridLayout_Layout_layout_gravity").get(null);
    assertTrue(gravityIndex instanceof Integer);
    id = indices[(Integer)gravityIndex];
    assertEquals(id, gravityValue);

    // The exact source order of attributes must be matched such that array indexing of the styleable arrays
    // reaches the right elements. For this reason, we use a LinkedHashMap in DeclareStyleableResourceValue.
    // Without this, using the v7 GridLayout widget and putting app:layout_gravity="left" on a child will
    // give value conversion errors.
    assertEquals(2, layoutColumnSpanIndex);
    assertEquals(3, gravityIndex);
  }

  public void testWithAars() throws Exception {
    AppResourceRepository appResources = AppResourceRepositoryTest.createTestAppResourceRepository(myFacet);
    ResourceClassGenerator generator = ResourceClassGenerator.create(appResources);
    assertNotNull(generator);
    Class<?> clz = generateClass(generator, "pkg.R$id");
    assertNotNull(clz);
    assertNotNull(clz.newInstance());
    Field[] declaredFields = clz.getDeclaredFields();
    String[] fieldNames = new String[declaredFields.length];
    for (int i = 0; i < declaredFields.length; i++) {
      fieldNames[i] = declaredFields[i].getName();
    }
    assertSameElements(fieldNames, "id1", "id2", "id3");
    styleableTestWithAars(generator);
    // Run same test again to ensure that caching is working as expected.
    styleableTestWithAars(generator);
  }

  private static void styleableTestWithAars(ResourceClassGenerator generator) throws Exception {
    Class<?> clz = generateClass(generator, "pkg.R$styleable");
    assertNotNull(clz);
    assertNotNull(clz.newInstance());
    assertNotNull(clz.getDeclaredField("Styleable_with_dots"));
    Field styleable3 = clz.getDeclaredField("Styleable_with_underscore");
    assertEquals(styleable3.getType(), (new int[0]).getClass());
    int[] array = (int[])styleable3.get(null);
    int idx = (Integer)clz.getDeclaredField("Styleable_with_underscore_android_framework_attr1").get(null);
    assertEquals(0x01010125, array[idx]);
    idx = (Integer)clz.getDeclaredField("Styleable_with_underscore_android_framework_attr2").get(null);
    assertEquals(0x01010142, array[idx]);
  }

  @Nullable
  protected static Class<?> generateClass(final ResourceClassGenerator generator, String name) throws ClassNotFoundException {
    ClassLoader classLoader = new ClassLoader(ResourceClassGeneratorTest.class.getClassLoader()) {
      @Override
      public Class<?> loadClass(String s) throws ClassNotFoundException {
        if (!s.startsWith("java")) { // Don't try to load super class
          final byte[] data = generator.generate(s);
          if (data != null) {
            return defineClass(null, data, 0, data.length);
          }
        }
        return super.loadClass(s);
      }
    };
    return classLoader.loadClass(name);
  }
}
