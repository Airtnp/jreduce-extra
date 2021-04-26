package helper;

public class GlobalConfig {
    public static boolean debug = true;
    public static boolean debugPredicate = false;
    public static boolean debugCommand = false;
    public static boolean debugPredicateDiff = false;
    public static boolean debugClassVerifier = false;
    public static boolean debugClassLoader = false;

    public static void println(final String s) {
        System.out.println(s);
    }
}
