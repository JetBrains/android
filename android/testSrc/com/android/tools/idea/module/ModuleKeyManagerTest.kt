package com.android.tools.idea.module

import com.android.testutils.MockitoKt.mock
import com.intellij.openapi.module.Module
import org.junit.Assert.*
import org.junit.Test

class ModuleKeyManagerTest {
  @Test
  fun checkModuleKeyRelation() {
    val module1 = mock<Module>()
    val module2 = mock<Module>()
    val module3 = mock<Module>()

    val module1Key = ModuleKeyManager.getKey(module1)
    val module2Key = ModuleKeyManager.getKey(module2)
    val module3Key = ModuleKeyManager.getKey(module3)

    assertNotEquals(module1Key, module2Key)
    assertNotEquals(module2Key, module3Key)
    assertNotEquals(module1Key, module3Key)
    assertEquals(module2Key, ModuleKeyManager.getKey(module2))
    assertEquals(module1Key, ModuleKeyManager.getKey(module1))
    assertEquals(module3Key, ModuleKeyManager.getKey(module3))
  }
}