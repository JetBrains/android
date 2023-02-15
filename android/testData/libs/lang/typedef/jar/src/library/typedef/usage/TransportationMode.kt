package library.typedef.usage
import androidx.annotation.StringDef
const val BIKE = "bicycle"
object BusHolder {
  const val BUS = "bus"
}
@StringDef(value = [BIKE, BusHolder.BUS, TransportationMode.LIGHT_RAIL])
annotation class TransportationMode {
  companion object {
    const val LIGHT_RAIL = "light-rail"
  }
}
