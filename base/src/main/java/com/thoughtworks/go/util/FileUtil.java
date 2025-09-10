/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.util;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class FileUtil {
    public static final String TMP_PARENT_DIR = "data";
    private static final String CRUISE_TMP_FOLDER = "cruise" + "-" + UUID.randomUUID();

    private FileUtil() {}

    public static boolean isFolderEmpty(File folder) {
        if (folder == null) {
            return true;
        }
        File[] files = folder.listFiles();
        return files == null || files.length == 0;
    }

    /**
     * Makes parent directories, ignoring if it already exists
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void mkdirsParentQuietly(File file) {
        File directory = file.getParentFile();
        mkdirsQuietly(directory);
    }

    /**
     * Makes directories, ignoring if null or already exists
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void mkdirsQuietly(File directory) {
        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }
    }

    public static boolean isSubdirectoryOf(File parent, File subdirectory) throws IOException {
        File parentFile = parent.getCanonicalFile();
        File current = subdirectory.getCanonicalFile();
        while (current != null) {
            if (current.equals(parentFile)) {
                return true;
            }
            current = current.getParentFile();
        }
        return false;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void createFilesByPath(File baseDir, String... files) throws IOException {
        for (String file : files) {
            File file1 = new File(baseDir, file);
            if (file.endsWith("/")) {
                file1.mkdirs();
            } else {
                file1.getParentFile().mkdirs();
                file1.createNewFile();
            }
        }
    }

    public static File createTempFolder() {
        File tempDir = new File(TMP_PARENT_DIR, CRUISE_TMP_FOLDER);
        File dir = new File(tempDir, UUID.randomUUID().toString());
        boolean ret = dir.mkdirs();
        if (!ret) {
            throw new RuntimeException("FileUtil#createTempFolder - Could not create temp folder");
        }
        return dir;
    }
}


