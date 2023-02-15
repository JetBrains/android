package library.typedef.usage;
import androidx.annotation.StringDef;
public class ElectricUnicycles {
   public static final String INMOTION = "inmotion";
   public static final String BEGODE = "gotway";
   public static final String KING_SONG = "king-song";
   public static final String VETERAN = "leaperkim";

   @StringDef({INMOTION, BEGODE, KING_SONG, VETERAN})
   public @interface ElectricUnicycle {}
}
