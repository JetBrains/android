import androidx.navigation.*;

@Navigator.Name("duplicate")
public class OtherNavigatorWithDestination extends Navigator<OtherNavigatorWithDestination.MyDestination> {

  @NavDestination.ClassType(MyActualDestination.class)
  public static class MyDestination extends NavDestination {}

  public static class MyActualDestination {}
}
