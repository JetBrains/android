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
import com.android.builder.model.AndroidLibrary;
import com.android.ide.common.blame.Message;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_DATA_BINDING;

/**
 * This class compiles a real project with data binding then checks whether the generated Binding classes match the virtual ones.
 */
public class GeneratedCodeMatchTest extends AndroidGradleTestCase {
  private static final String DATA_BINDING_COMPONENT_CLASS_NAME = SdkConstants.CLASS_DATA_BINDING_COMPONENT.replace(".", "/");
  @NotNull
  private ClassReader findViewDataBindingClass() throws IOException {
    File classes = null;

    AndroidModuleModel model = AndroidModuleModel.get(myAndroidFacet);
    for (AndroidLibrary lib : model.getMainArtifact().getDependencies().getLibraries()) {
      if (lib.getName().startsWith("com.android.databinding:library")) {
        classes = lib.getJarFile();
      }
    }
    assert classes != null;
    assertTrue(classes.exists());

    try (JarFile classesJar = new JarFile(classes, true)) {
      ZipEntry entry = classesJar.getEntry(SdkConstants.CLASS_DATA_BINDING_BASE_BINDING.replace(".", "/") + ".class");
      assertNotNull(entry);
      return new ClassReader(classesJar.getInputStream(entry));
    }
  }

  public void testGeneratedCodeMatch() throws Exception {
    loadProject(PROJECT_WITH_DATA_BINDING);
    // temporary fix until test model can detect dependencies properly
    GradleInvocationResult assembleDebug = invokeGradleTasks(getProject(), "assembleDebug");
    assertTrue(StringUtil.join(assembleDebug.getCompilerMessages(Message.Kind.ERROR), "\n"), assembleDebug.isBuildSuccessful());

    GradleSyncState syncState = GradleSyncState.getInstance(getProject());
    assertFalse(syncState.isSyncNeeded().toBoolean());
    assertTrue(ModuleDataBinding.getInstance(myAndroidFacet).isEnabled());


    // trigger initialization
    ModuleResourceRepository.getOrCreateInstance(myAndroidFacet);

    File classesOut = new File(getProject().getBaseDir().getPath(), "/app/build/intermediates/artifact_transform/compileDebugJavaWithJavac/classes");
    //noinspection unchecked
    Collection<File> classes = FileUtils.listFiles(classesOut, new String[]{"class"}, true);
    assertTrue("if we cannot find any class, something is wrong with the test", classes.size() > 0);
    ClassReader viewDataBindingClass = findViewDataBindingClass();
    Set<String> baseClassInfo = collectDescriptionSet(viewDataBindingClass, new HashSet<>());

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(getProject());
    Set<String> missingClasses = new HashSet<>();

    Map<String, ClassReader> klassMap = classes.stream().map((file) -> {
      try {
        return new ClassReader(FileUtils.readFileToByteArray(file));
      }
      catch (IOException e) {
        e.printStackTrace();
        fail(e.getMessage());
      }
      return null;
    }).filter(kls -> kls != null)
      .collect(Collectors.toMap(
        kls -> kls.getClassName(),
        kls -> kls
      ));

    Map<ClassReader, ClassReader> superClassLookup = klassMap.values().stream()
      .filter(kls -> klassMap.containsKey(kls.getSuperName()))
      .collect(Collectors.toMap(
        kls -> kls,
        kls -> klassMap.get(kls.getSuperName())
      ));

    int verifiedClassCount = 0;
    for (ClassReader classReader : klassMap.values()) {
      if (!shouldVerify(viewDataBindingClass, classReader, superClassLookup)) {
        continue;
      }
      verifiedClassCount++;
      String className = classReader.getClassName();
      PsiClass psiClass = javaPsiFacade
        .findClass(className.replace("/", "."), myAndroidFacet.getModule().getModuleWithDependenciesAndLibrariesScope(false));
      if (psiClass == null) {
        missingClasses.add(className);
        continue;
      }
      assertNotNull(psiClass);
      String asmInfo = collectDescriptions(classReader, baseClassInfo);
      String psiInfo = collectDescriptions(psiClass);
      assertEquals(className, asmInfo, psiInfo);
    }
    assertTrue("test sanity, should be able to find some data binding generated classes", verifiedClassCount > 3);
    assertEquals("These classes are missing", "", StringUtil.join(missingClasses, "\n"));
  }

  private static boolean shouldVerify(ClassReader viewDataBindingClass, ClassReader classReader, Map<ClassReader,
    ClassReader> superClassLookup) {
    return classReader != null &&
           (viewDataBindingClass.getClassName().equals(classReader.getSuperName()) ||
            DATA_BINDING_COMPONENT_CLASS_NAME.equals(classReader.getClassName()) ||
            shouldVerify(viewDataBindingClass, superClassLookup.get(classReader), superClassLookup));
  }

  @NotNull
  private static TreeSet<String> collectDescriptionSet(@NotNull ClassReader classReader, @NotNull final Set<String> exclude) {
    final TreeSet<String> set = new TreeSet<>();
    classReader.accept(new ClassVisitor(Opcodes.ASM5) {
      @Override
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        List<String> interfaceList = new ArrayList<>();
        Collections.addAll(interfaceList, interfaces);
        Collections.sort(interfaceList);
        set.add(name + " : " + superName + " -> " + StringUtil.join(interfaceList, ", "));
      }

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
    PsiClassType[] superTypes = psiClass.getSuperTypes();
    String superType = superTypes.length == 0 ? "java/lang/Object" : superTypes[0].getCanonicalText().replace('.', '/');
    List<String> sortedInterfaces = Arrays.stream(psiClass.getInterfaces()).map((klass) -> klass.getQualifiedName().replace('.', '/'))
      .sorted().collect(Collectors.toList());
    set.add(psiClass.getQualifiedName().replace('.', '/') + " : " + superType + " -> " + StringUtil.join(sortedInterfaces, ", "));
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
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
