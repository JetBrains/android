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
package org.jetbrains.android.databinding;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.FieldVisitor;
import org.jetbrains.asm4.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 * This test requires DataBinding's rebuildRepo task to be run first otherwise it will fail because it won't find the snapshot versions.
 */
public class GeneratedCodeMatchTest extends AndroidGradleTestCase {
  private static final String DATA_BINDING_COMPONENT_CLASS_NAME = SdkConstants.CLASS_DATA_BINDING_COMPONENT.replace(".", "/");
  @NotNull
  private ClassReader findViewDataBindingClass() throws IOException {
    File explodedAars =
      new File(getProject().getBaseDir().getCanonicalPath(), "app/build/intermediates/exploded-aar/com.android.databinding/library");
    File[] contents = explodedAars.listFiles();
    assert contents != null;
    assertEquals(1, contents.length);
    File container = contents[0]; //version
    File classes = new File(container, "jars/classes.jar");
    assertTrue(classes.exists());
    JarFile classesJar = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      classesJar = new JarFile(classes, true);
      ZipEntry entry = classesJar.getEntry(SdkConstants.CLASS_DATA_BINDING_BASE_BINDING.replace(".", "/") + ".class");
      assertNotNull(entry);
      return new ClassReader(classesJar.getInputStream(entry));
    }
    finally {
      if (classesJar != null) {
        classesJar.close();
      }
    }
  }

  public void testGeneratedCodeMatch() throws Exception {
    File projectFolder = virtualToIoFile(myFixture.getProject().getBaseDir());
    createGradlePropertiesFile(projectFolder);
    loadProject("projects/projectWithDataBinding");
    compile();

    GradleSyncState syncState = GradleSyncState.getInstance(myFixture.getProject());
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertTrue(myAndroidFacet.isDataBindingEnabled());
    // trigger initialization
    myAndroidFacet.getModuleResources(true);

    File classesOut = new File(projectFolder, "/app/build/intermediates/classes/debug");
    //noinspection unchecked
    Collection<File> classes = FileUtils.listFiles(classesOut, new String[]{"class"}, true);
    assertTrue("if we cannot find any class, something is wrong with the test", classes.size() > 0);
    ClassReader viewDataBindingClass = findViewDataBindingClass();
    Set<String> baseClassInfo = collectDescriptionSet(viewDataBindingClass, new HashSet<>());

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(myAndroidFacet.getModule().getProject());
    Set<String> missingClasses = new HashSet<>();
    boolean foundOne = false;
    for (File classFile : classes) {
      ClassReader classReader = new ClassReader(FileUtils.readFileToByteArray(classFile));
      if (!shouldVerify(classReader, viewDataBindingClass)) {
        continue;
      }
      foundOne = true;
      String className = classReader.getClassName();
      PsiClass psiClass =  javaPsiFacade
        .findClass(className.replace("/", "."), myAndroidFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (psiClass == null) {
        missingClasses.add(className);
        continue;
      }
      assertNotNull(psiClass);
      String asmInfo = collectDescriptions(classReader, baseClassInfo);
      String psiInfo = collectDescriptions(psiClass);
      assertEquals(asmInfo, psiInfo);
    }
    assertTrue("test sanity, should be able to find some data binding generated classes", foundOne);
    assertEquals("These classes are missing", "", StringUtil.join(missingClasses, "\n"));
  }

  private boolean shouldVerify(ClassReader classReader, ClassReader viewDataBindingClass) {
    return viewDataBindingClass.getClassName().equals(classReader.getSuperName())
           || DATA_BINDING_COMPONENT_CLASS_NAME.equals(classReader.getClassName());
  }

  private static void createGradlePropertiesFile(@NotNull File projectFolder) throws IOException {
    File dataBindingRoot = new File(getTestDataPath(), "/../../../../data-binding");
    File out = new File(projectFolder, SdkConstants.FN_GRADLE_PROPERTIES);
    FileUtils.writeStringToFile(out, "dataBindingRoot=" + dataBindingRoot.getCanonicalPath());
  }

  private void compile() throws Exception {
    String javaHome = System.getenv().get("JAVA_HOME");
    assertTrue("this test requires java 8", StringUtil.isNotEmpty(javaHome));
    assertBuildsCleanly(getProject(), true, "-Dorg.gradle.java.home=" + javaHome);
  }

  @NotNull
  private static TreeSet<String> collectDescriptionSet(@NotNull ClassReader classReader, @NotNull final Set<String> exclude) {
    final TreeSet<String> set = new TreeSet<>();
    classReader.accept(new ClassVisitor(Opcodes.ASM5) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        String info = modifierDescription(access) + " " + name + " : " + desc;
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !name.startsWith("<") && !exclude.contains(info)) {
          set.add(info);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
      }

      @Override
      public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        String info = modifierDescription(access) + " " + name + " : " + desc;
        if ((access & Opcodes.ACC_PUBLIC) != 0 && !exclude.contains(info)) {
          set.add(info);
        }
        return super.visitField(access, name, desc, signature, value);
      }
    }, 0);
    return set;
  }

  @NotNull
  private static String collectDescriptions(@NotNull ClassReader classReader, @NotNull Set<String> exclude) {
    return StringUtil.join(collectDescriptionSet(classReader, exclude), "\n");
  }

  @NotNull
  private static String collectDescriptions(@NotNull PsiClass psiClass) {
    final TreeSet<String> set = new TreeSet<>();
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.getModifierList() != null && method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
        set.add(modifierDescription(method) + " " + method.getName() + " : " + createMethodDescription(method));
      }
    }
    for (PsiField field : psiClass.getFields()) {
      if (field.getModifierList() != null && field.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
        set.add(modifierDescription(field) + " " + field.getName() + " : " + createFieldDescription(field));
      }
    }
    return StringUtil.join(set, "\n");
  }

  @NotNull
  private static String createFieldDescription(@NotNull PsiField field) {
    return typeToAsm(field.getType());
  }

  @NotNull
  private static String createMethodDescription(@NotNull PsiMethod psiMethod) {
    StringBuilder res = new StringBuilder();
    PsiType returnType = psiMethod.getReturnType();
    assertNotNull(returnType);
    res.append("(");
    for (PsiParameter param : psiMethod.getParameterList().getParameters()) {
      res.append(typeToAsm(param.getType()));
    }
    res.append(")").append(typeToAsm(returnType));
    return res.toString();
  }

  @NotNull
  private static String typeToAsm(@NotNull PsiType psiType) {
    String conv = BASIC_ASM_TYPES.get(psiType);
    if (conv != null) {
      return conv;
    }
    if (psiType instanceof PsiArrayType) {
      PsiArrayType arrType = (PsiArrayType)psiType;
      return "[" + typeToAsm(arrType.getComponentType());
    }
    else {
      return "L" + psiType.getCanonicalText().replace(".", "/") + ";";
    }
  }

  @NotNull
  protected static String modifierDescription(@NotNull final PsiModifierListOwner owner) {
    return Joiner.on(" ").join(Iterables.filter(IMPORTANT_MODIFIERS.keySet(), new Predicate<String>() {
      @Override
      public boolean apply(String modifier) {
        //noinspection ConstantConditions
        return owner.getModifierList().hasModifierProperty(modifier);
      }
    }));
  }

  @NotNull
  protected static String modifierDescription(final int access) {
    return Joiner.on(" ").join(Iterables.filter(IMPORTANT_MODIFIERS.keySet(), new Predicate<String>() {
      @Override
      public boolean apply(String modifier) {
        //noinspection ConstantConditions
        return (IMPORTANT_MODIFIERS.get(modifier).intValue() & access) != 0;
      }
    }));
  }

  static Map<String, Integer> IMPORTANT_MODIFIERS = new ContainerUtil.ImmutableMapBuilder<String, Integer>()
    .put(PsiModifier.PUBLIC, Opcodes.ACC_PUBLIC)
    .put(PsiModifier.STATIC, Opcodes.ACC_STATIC)
    .build();

  static Map<PsiType, String> BASIC_ASM_TYPES =
    new ContainerUtil.ImmutableMapBuilder<PsiType, String>()
      .put(PsiType.VOID, "V")
      .put(PsiType.BOOLEAN, "Z")
      .put(PsiType.CHAR, "C")
      .put(PsiType.BYTE, "B")
      .put(PsiType.SHORT, "S")
      .put(PsiType.INT, "I")
      .put(PsiType.FLOAT, "F")
      .put(PsiType.LONG, "J")
      .put(PsiType.DOUBLE, "d").build();
}
