import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    private static final Logger LOGGER = LogManager.getLogger("Main");
    private static final int BATCH = 50;

    public static void main(String[] args) throws Exception {
        try (final InputStream input = new FileInputStream(args[0])) {
            final Properties prop = new Properties();
            prop.load(input);
            final String sourceDir = prop.getProperty("source.dir");
            final String targetDir = prop.getProperty("target.dir");
            final List<FileStepsJob> jobs = prepareJobs(sourceDir, targetDir);
            final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
            final List<Future<Map<String, String>>> result = submitCalls(executorService, jobs);
            printResult(result);
            executorService.shutdown();
        } catch (ArrayIndexOutOfBoundsException e) {
            LOGGER.error("Please set argument path to config file. \n Example java Main <path_to_config>");
        } catch (IOException e) {
            LOGGER.error("Can't load properties file");
        }
    }

    private static List<FileStepsJob> prepareJobs(String sourceDir, String targetDir) throws Exception {
        final String dir = FileUtils.validatePath(targetDir);
        final String tmp = FileUtils.createAndReturnDir(dir, "tmp");
        final String resultDir = FileUtils.createAndReturnDir(dir, "result");
        return prepareCalls(sourceDir, tmp, resultDir);
    }

    private static List<FileStepsJob> prepareCalls(String sourceDir, String tmp, String resultDir) throws Exception {
        final List<File> listOfFiles = getListOfFiles(sourceDir);
        return prepareCallByBatch(tmp, resultDir, listOfFiles);
    }

    private static void printResult(List<Future<Map<String, String>>> result) throws InterruptedException, java.util.concurrent.ExecutionException {
        final Map<String, String> finalResult = new HashMap<>();
        for (final Future<Map<String, String>> mapFuture : result) {
            final Map<String, String> printResult = mapFuture.get();
            printResult.forEach(
                    (k, v) -> {
                        if (finalResult.containsKey(k)) {
                            finalResult.put(k, finalResult.get(k) + v);
                        } else {
                            finalResult.put(k, v);
                        }
                    }
            );
        }
        LOGGER.info("=================RESULT===============");
        finalResult.forEach((filePath, status) -> LOGGER.info("File: {} | Status: {}", filePath, status));
    }

    private static List<Future<Map<String, String>>> submitCalls(ExecutorService executorService, List<FileStepsJob> listOfCalls) {
        return listOfCalls.stream().map(executorService::submit)
                .collect(Collectors.toList());
    }

    private static List<FileStepsJob> prepareCallByBatch(String tmpDir, String targetDir, List<File> listOfFiles) {
        return IntStream.range(0, (listOfFiles.size() + BATCH - 1) / BATCH)
                .mapToObj(i -> listOfFiles.subList(i * BATCH, Math.min(listOfFiles.size(), (i + 1) * BATCH)))
                .map(files -> files.stream().map(File::getAbsolutePath).collect(Collectors.toList()))
                .map(batch -> new FileStepsJob(tmpDir, targetDir, batch))
                .collect(Collectors.toList());
    }

    private static List<File> getListOfFiles(String sourceDir) throws Exception {
        final File file = new File(sourceDir);
        List<File> listOfFiles = new ArrayList<>();
        if (!file.isFile()) {
            final List<File> files = Optional.ofNullable(file.listFiles())
                    .map(Arrays::asList)
                    .orElseThrow(() -> new Exception("Source folder should have files"));
            listOfFiles.addAll(files);
        }
        return listOfFiles;
    }
}
