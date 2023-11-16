package library.function.definition
import library.typedef.usage.TransportationMode
class TransportationModeUsage(p1: String, p2: Int, p3: Long, @TransportationMode param: String) {
  companion object {
    @JvmStatic
    fun useTransportationMode(p1: String, p2: Int, p3: Long, @TransportationMode param: String) {}
  }
}
