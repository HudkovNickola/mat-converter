import java.io.File;

public final class FileUtils {

    public static String validatePath(String dir) {
        final boolean isLastCharSlash = '/' == (dir.charAt(dir.length() - 1));
        return isLastCharSlash ? dir : dir + "/";
    }

    public static String createAndReturnDir(String path, String targetDir) {
        createDir(path, targetDir);
        return path + targetDir;
    }

    public static void createDir(String path, String targetDir) {
        final String dir = FileUtils.validatePath(path);
        final File targetFolder = new File(dir + targetDir);
        if (!targetFolder.exists()){
            targetFolder.mkdirs();
        }
    }
}
