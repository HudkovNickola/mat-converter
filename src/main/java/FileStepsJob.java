import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import us.hebi.matlab.mat.format.Mat5;
import us.hebi.matlab.mat.types.MatFile;
import us.hebi.matlab.mat.types.Source;
import us.hebi.matlab.mat.types.Sources;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class FileStepsJob implements Callable<Map<String, String>> {
    private static final Logger LOGGER = LogManager.getLogger("FileStepsJob");
    public static final String IN_PROGRESS = "In progress";
    public static final String FAULT = "Fault";
    public static final String SUCCESSFULLY = "Successfully";
    public static final String MOVED_TO_TMP = "Moved to tmp";
    public static final String ERROR_MOVE_TO_TMP = "Error move to tmp";
    public static final String ERROR_READING_MAT_FILE = "Error reading MAT file";
    private final String tmpDir;
    private final String targetDir;
    private final List<String> fileNames;

    FileStepsJob(String tmpDir, String targetDir, List<String> fileNames) {
        this.targetDir = FileUtils.validatePath(targetDir);
        this.tmpDir = FileUtils.validatePath(tmpDir);
        this.fileNames = fileNames;
    }

    @Override
    public Map<String, String> call() throws Exception {
        return fileNames.parallelStream()
                .map(this::readAndConvert)
                .filter(this::moveErrorsFilesToTmp)
                .map(this::saveFiles)
                .collect(Collectors.toMap(Pair::getName, Pair::getProgress));
    }

    private Pair<String, String> readAndConvert(final String filePath) {
        try (final Source source = Sources.openFile(filePath)) {
            final MatFile mat = Mat5.newReader(source).readMat();
            final String result = JsonToMatConverter.convert(mat.getEntries());
            return new Pair<>(filePath, result, IN_PROGRESS);
        } catch (Exception e) {
            LOGGER.error("Error while try read and convert MAT file {}, \nDetails: {}", filePath, e);
            return new Pair<>(filePath, null, ERROR_READING_MAT_FILE);
        }
    }

    private boolean moveErrorsFilesToTmp(Pair<String, String> content) {
        createErrorAndProcessedFolder();
        if (Objects.isNull(content.getValue())) {
            try {
                final Path sourceFile = Paths.get(content.getName());
                final String targetFile = tmpDir + "/error/" + sourceFile.getFileName();
                Files.move(sourceFile, Paths.get(targetFile), StandardCopyOption.REPLACE_EXISTING);
                content.setProgress(MOVED_TO_TMP);
            } catch (Exception e) {
                LOGGER.error("Can't move file from {} to {}, \nDetails: {}", content.getName(), tmpDir, e);
                content.setProgress(ERROR_MOVE_TO_TMP);
            }
            return false;
        }
        moveToProcessedFolder(content);
        return true;
    }

    private void moveToProcessedFolder(Pair<String, String> content) {
        try {
            final Path sourceFile = Paths.get(content.getName());
            final String targetFile = tmpDir + "/processed/" + sourceFile.getFileName();
            Files.move(sourceFile, Paths.get(targetFile), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Can't move file from {} to {}, \nDetails: {}", content.getName(), tmpDir, e);
        }
    }

    private void createErrorAndProcessedFolder() {
        FileUtils.createDir(tmpDir, "error");
        FileUtils.createDir(tmpDir, "processed");
    }

    private Pair<String, String> saveFiles(Pair<String, String> content) {
        final String originalFilename = Paths.get(content.getName()).getFileName().toString();
        final String filename = originalFilename.substring(0, originalFilename.indexOf("."));
        final String targetFile = targetDir + filename + ".json";
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(targetFile))) {
            writer.write(content.getValue());
            content.setProgress(SUCCESSFULLY);
        } catch (Exception e) {
            LOGGER.error("Can't write to file {}, \nDetails: {}", targetFile, e);
            content.setProgress(FAULT);
        }
        return content;
    }

    static class Pair<T, S> {
        private final T name;
        private final S value;
        private String progress;

        Pair(T name, S value) {
            this.name = name;
            this.value = value;
        }

        Pair(T name, S value, String progress) {
            this(name, value);
            this.progress = progress;
        }

        public T getName() {
            return name;
        }

        public S getValue() {
            return value;
        }

        public String getProgress() {
            return progress;
        }

        public void setProgress(String progress) {
            this.progress = progress;
        }
    }
}
