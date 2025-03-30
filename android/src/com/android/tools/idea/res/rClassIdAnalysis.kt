package com.android.tools.idea.res

import com.android.resources.ResourceType
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

/**
 * For a given [packageName] it finds the R class (and other resource type classes) and returns
 * [RClassResources] with information about resource IDs.
 */
fun getRClassResources(
  packageName: String?,
  classContentLoader: (String) -> ByteArray?,
): RClassResources? {
  packageName ?: return null
  val resourceTypes = findResourceTypes(packageName, classContentLoader) ?: return null

  return resourceTypes
    .mapNotNull { resType ->
      val nameToId =
        findResources(packageName, resType, classContentLoader) ?: return@mapNotNull null
      resType to nameToId
    }
    .toMap()
    .let { RClassResources(it) }
}

/** Used to track mapping between resource names and constant values in the R class. */
class RClassResources(private val resources: Map<ResourceType, Map<String, Int>>) {

  /**
   * Returns all resources of the specified [resourceType]. Map has resource names as keys and IDs
   * are values. Only [Int] constants are in the returned map.
   */
  fun getResources(resourceType: ResourceType): Map<String, Int>? = resources[resourceType]
}

/** Visits resource type inner class and collects all Int constants. */
private class ExtractConstants : ClassVisitor(Opcodes.ASM9) {
  private val resources = mutableMapOf<String, Int>()
  private var finished = false

  fun getIds(): Map<String, Int> = resources.also { check(finished) { "Class is not processed." } }

  override fun visitField(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    value: Any?,
  ): FieldVisitor? {
    name ?: return null
    if (
      value !is Int ||
        (access and (Opcodes.ACC_FINAL or Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC) == 0)
    ) {
      // we only care about Int constants
      return null
    }
    resources[name] = value
    return null
  }

  override fun visitEnd() {
    finished = true
  }
}

private val classReaderFlags =
  ClassReader.SKIP_CODE or ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG

private fun findResources(
  packageName: String,
  resourceType: ResourceType,
  classContentLoader: (String) -> ByteArray?,
): Map<String, Int>? {
  val fqcn = "$packageName.R$${resourceType.getName()}"
  val innerClassBytes = classContentLoader(fqcn) ?: return null
  val visitor = ExtractConstants()
  ClassReader(innerClassBytes).accept(visitor, classReaderFlags)
  return visitor.getIds()
}

private fun findResourceTypes(
  packageName: String,
  classContentLoader: (String) -> ByteArray?,
): Set<ResourceType>? {
  val fqcn = "$packageName.R"
  val classContent = classContentLoader(fqcn) ?: return null
  val classReader = ClassReader(classContent)

  val resourceTypes = mutableSetOf<ResourceType>()
  val internalName = fqcn.replace(".", "/")
  val collectResourceTypes =
    object : ClassVisitor(Opcodes.ASM9) {
      override fun visitInnerClass(
        name: String?,
        outerName: String?,
        innerName: String?,
        access: Int,
      ) {
        innerName ?: return
        name ?: return
        if (outerName != internalName) return

        val resourceType = ResourceType.fromClassName(innerName) ?: return
        resourceTypes.add(resourceType)
      }
    }
  classReader.accept(collectResourceTypes, classReaderFlags)
  return resourceTypes
}
