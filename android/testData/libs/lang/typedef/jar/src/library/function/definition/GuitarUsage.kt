package library.function.definition
import library.typedef.usage.Guitar
class GuitarUsage(p1: String, p2: Int, p3: Long, @Guitar param: Int) {
  companion object {
    @JvmStatic
    fun useGuitar(p1: String, p2: Int, p3: Long, @Guitar param: Int) {}
  }
}
