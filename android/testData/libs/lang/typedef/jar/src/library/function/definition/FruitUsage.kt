package library.function.definition
import library.typedef.usage.Fruit
class FruitUsage(p1: String, p2: Int, p3: Long, @Fruit param: Long) {
  companion object {
    @JvmStatic
    fun useFruit(p1: String, p2: Int, p3: Long, @Fruit param: Long) {}
  }
}
