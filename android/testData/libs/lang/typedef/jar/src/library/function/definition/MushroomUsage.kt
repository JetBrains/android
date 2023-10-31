package library.function.definition
import library.typedef.usage.Mushroom
class MushroomUsage(p1: String, p2: Int, p3: Long, @Mushroom param: Long) {
  companion object {
    @JvmStatic
    fun useMushroom(p1: String, p2: Int, p3: Long, @Mushroom param: Long) {}
  }
}
