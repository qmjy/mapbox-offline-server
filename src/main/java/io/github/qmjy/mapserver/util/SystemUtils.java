package io.github.qmjy.mapserver.util;

public class SystemUtils {
    public static boolean checkTilesetName(String tileset) {
        return tileset.contains("..") || tileset.contains("/") || tileset.contains("\\");
    }
}
