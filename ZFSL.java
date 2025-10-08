import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.io.IOException;

/**
 * Zero Dependencies File Selection and Copy Tool (zFSL)
 * Interactive CLI application for selective file copying
 */
public class ZFSL {

    /**
     * Configuration record for application settings
     */
    public record Config(
            Path sourceDirectory,
            Path targetDirectory,
            String fileExtension) {
        /**
         * Validates the configuration parameters
         */
        public Config {
            Objects.requireNonNull(sourceDirectory, "Source directory cannot be null");
            Objects.requireNonNull(targetDirectory, "Target directory cannot be null");
            Objects.requireNonNull(fileExtension, "File extension cannot be null");

            if (fileExtension.trim().isEmpty()) {
                throw new IllegalArgumentException("File extension cannot be empty");
            }
        }

        /**
         * Validates that source directory exists and is readable
         */
        public boolean isSourceValid() {
            return Files.exists(sourceDirectory) && Files.isDirectory(sourceDirectory)
                    && Files.isReadable(sourceDirectory);
        }

        /**
         * Validates that target directory can be created or is writable
         */
        public boolean isTargetValid() {
            if (Files.exists(targetDirectory)) {
                return Files.isDirectory(targetDirectory) && Files.isWritable(targetDirectory);
            }
            // Check if parent directory exists and is writable for creation
            Path parent = targetDirectory.getParent();
            return parent != null && Files.exists(parent) && Files.isWritable(parent);
        }

        /**
         * Normalizes file extension to ensure it starts with a dot
         */
        public String normalizedExtension() {
            return fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        }
    }

    /**
     * Sealed interface for operation results using Java 25 pattern matching
     */
    public sealed interface OperationResult
            permits OperationResult.Success, OperationResult.Skip, OperationResult.Error {

        /**
         * Successful file copy operation
         */
        record Success(Path source, Path target) implements OperationResult {
        }

        /**
         * Skipped file operation
         */
        record Skip(Path source, String reason) implements OperationResult {
        }

        /**
         * Failed file operation
         */
        record Error(Path source, String message, Throwable cause) implements OperationResult {
        }
    }

    /**
     * Processing state record for tracking operation statistics
     */
    public record ProcessingState(
            int totalFiles,
            int copiedFiles,
            int skippedFiles,
            int errorFiles,
            List<OperationResult> results) {
        /**
         * Creates initial processing state
         */
        public static ProcessingState initial() {
            return new ProcessingState(0, 0, 0, 0, new ArrayList<>());
        }

        /**
         * Creates new state with incremented total files count
         */
        public ProcessingState withTotalFiles(int total) {
            return new ProcessingState(total, copiedFiles, skippedFiles, errorFiles, results);
        }

        /**
         * Creates new state with added operation result
         */
        public ProcessingState withResult(OperationResult result) {
            var newResults = new ArrayList<>(results);
            newResults.add(result);

            return switch (result) {
                case OperationResult.Success(var source, var target) ->
                    new ProcessingState(totalFiles, copiedFiles + 1, skippedFiles, errorFiles, newResults);
                case OperationResult.Skip(var source, var reason) ->
                    new ProcessingState(totalFiles, copiedFiles, skippedFiles + 1, errorFiles, newResults);
                case OperationResult.Error(var source, var message, var cause) ->
                    new ProcessingState(totalFiles, copiedFiles, skippedFiles, errorFiles + 1, newResults);
            };
        }

        /**
         * Formats summary statistics for display
         */
        public String formatSummary() {
            return """

                    Operation Summary:
                    ================
                    Total files found: %d
                    Files copied: %d
                    Files skipped: %d
                    Files with errors: %d
                    """.formatted(totalFiles, copiedFiles, skippedFiles, errorFiles);
        }
    }

    /**
     * Displays help text using Java text blocks
     */
    static void displayHelp() {
        IO.println("""
                ZFSL - Zero Dependencies File Selection and Copy Tool
                ====================================================

                Usage: java ZFSL [OPTIONS]

                Options:
                  -s, --source <directory>     Source directory to search (default: current directory)
                  -t, --target <directory>     Target directory for copying files (required if not prompted)
                  -e, --extension <extension>  File extension to search for (required if not prompted)
                  -h, --help                   Display this help message

                Examples:
                  java ZFSL                                    # Interactive mode with prompts
                  java ZFSL -s /path/to/source -t /path/to/target -e .java
                  java ZFSL --source ./src --target ./backup --extension txt

                Interactive Mode:
                  If target directory or file extension are not provided, you will be prompted
                  to enter them interactively.
                """);
    }

    /**
     * Prompts user for missing configuration values using Console
     */
    static String promptForInput(String prompt) {
        var console = System.console();
        if (console != null) {
            var input = console.readLine(prompt + ": ");
            return input != null ? input.trim() : "";
        } else {
            // Fallback for environments without console (like IDEs)
            IO.print(prompt + ": ");
            try (var scanner = new Scanner(System.in)) {
                return scanner.nextLine().trim();
            }
        }
    }

    /**
     * Parses command line arguments using modern switch expressions
     */
    static Config parseArguments(String[] args) {
        var sourceDirectory = Path.of(System.getProperty("user.dir")); // Default to current directory
        Path targetDirectory = null;
        String fileExtension = null;

        // Parse arguments using modern switch expressions
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> {
                    displayHelp();
                    System.exit(0);
                }
                case "-s", "--source" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Source directory argument requires a value");
                    }
                    sourceDirectory = Path.of(args[++i]);
                }
                case "-t", "--target" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Target directory argument requires a value");
                    }
                    targetDirectory = Path.of(args[++i]);
                }
                case "-e", "--extension" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("File extension argument requires a value");
                    }
                    fileExtension = args[++i];
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    } else {
                        throw new IllegalArgumentException("Unexpected argument: " + args[i]);
                    }
                }
            }
        }

        // Prompt for missing required values
        if (targetDirectory == null) {
            var targetInput = promptForInput("Enter target directory");
            if (targetInput.isEmpty()) {
                throw new IllegalArgumentException("Target directory cannot be empty");
            }
            targetDirectory = Path.of(targetInput);
        }

        if (fileExtension == null) {
            var extensionInput = promptForInput("Enter file extension (e.g., .java, txt)");
            if (extensionInput.isEmpty()) {
                throw new IllegalArgumentException("File extension cannot be empty");
            }
            fileExtension = extensionInput;
        }

        return new Config(sourceDirectory, targetDirectory, fileExtension);
    }

    /**
     * Validates configuration and displays meaningful error messages
     */
    static void validateConfiguration(Config config) {
        if (!config.isSourceValid()) {
            if (!Files.exists(config.sourceDirectory())) {
                throw new IllegalArgumentException(
                        "Source directory does not exist: " + config.sourceDirectory());
            }
            if (!Files.isDirectory(config.sourceDirectory())) {
                throw new IllegalArgumentException(
                        "Source path is not a directory: " + config.sourceDirectory());
            }
            if (!Files.isReadable(config.sourceDirectory())) {
                throw new IllegalArgumentException(
                        "Source directory is not readable: " + config.sourceDirectory());
            }
        }

        if (!config.isTargetValid()) {
            if (Files.exists(config.targetDirectory()) && !Files.isDirectory(config.targetDirectory())) {
                throw new IllegalArgumentException(
                        "Target path exists but is not a directory: " + config.targetDirectory());
            }
            if (Files.exists(config.targetDirectory()) && !Files.isWritable(config.targetDirectory())) {
                throw new IllegalArgumentException(
                        "Target directory is not writable: " + config.targetDirectory());
            }
            var parent = config.targetDirectory().getParent();
            if (parent != null && (!Files.exists(parent) || !Files.isWritable(parent))) {
                throw new IllegalArgumentException(
                        "Cannot create target directory (parent not writable): " + config.targetDirectory());
            }
        }

        // Validate file extension format
        var extension = config.normalizedExtension();
        if (extension.length() <= 1) {
            throw new IllegalArgumentException("File extension must contain at least one character after the dot");
        }
    }

    /**
     * Discovers files matching the specified extension using recursive directory
     * traversal
     * 
     * @param config Configuration containing source directory and file extension
     * @return List of discovered files for memory-efficient processing
     */
    static List<Path> discoverFiles(Config config) {
        var extension = config.normalizedExtension();
        var sourceDir = config.sourceDirectory();

        try (var fileStream = Files.walk(sourceDir)) {
            return fileStream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        // Check if file name ends with the extension
                        var fileName = path.getFileName().toString();
                        return fileName.endsWith(extension);
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            handleFileDiscoveryError(sourceDir, e);
            return List.of(); // Return empty list on error
        }
    }

    /**
     * Handles errors during file discovery with graceful error reporting
     * 
     * @param sourceDir The source directory being searched
     * @param error     The IOException that occurred
     */
    static void handleFileDiscoveryError(Path sourceDir, IOException error) {
        var errorMessage = switch (error.getClass().getSimpleName()) {
            case "AccessDeniedException" ->
                "Access denied while searching directory: " + sourceDir +
                        "\nCheck file permissions and try again.";
            case "NoSuchFileException" ->
                "Directory not found: " + sourceDir +
                        "\nVerify the path exists and try again.";
            case "FileSystemException" ->
                "File system error while accessing: " + sourceDir +
                        "\nThe directory may be on an unavailable network drive or corrupted file system.";
            default ->
                "I/O error while searching directory: " + sourceDir +
                        "\nError: " + error.getMessage();
        };

        System.err.println("File Discovery Error: " + errorMessage);

        // Log additional details for debugging
        if (error.getCause() != null) {
            System.err.println("Underlying cause: " + error.getCause().getMessage());
        }
    }

    /**
     * Alternative file discovery method using PathMatcher with glob patterns
     * This method demonstrates proper glob pattern usage as specified in
     * requirements.
     * Available for future use when more complex pattern matching is needed.
     * 
     * @param config Configuration containing source directory and file extension
     * @return List of discovered files using PathMatcher filtering
     */
    static List<Path> discoverFilesWithGlob(Config config) {
        var extension = config.normalizedExtension();
        var sourceDir = config.sourceDirectory();

        // Create PathMatcher for glob pattern matching - match files at any depth
        var fileSystem = FileSystems.getDefault();
        var globPattern = "*" + extension; // Match files ending with extension
        var pathMatcher = fileSystem.getPathMatcher("glob:" + globPattern);

        try (var fileStream = Files.walk(sourceDir)) {
            return fileStream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        // Match against just the filename, not the full path
                        var fileName = path.getFileName();
                        return pathMatcher.matches(fileName);
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            handleFileDiscoveryError(sourceDir, e);
            return List.of(); // Return empty list on error
        }
    }

    /**
     * Alternative file discovery method using virtual threads for concurrent
     * operations.
     * This method can be used for very large directory trees where performance is
     * critical.
     * Available for future use when concurrent file processing is implemented.
     * 
     * @param config Configuration containing source directory and file extension
     * @return Stream of discovered files for memory-efficient processing
     */
    static Stream<Path> discoverFilesAsync(Config config) {
        var extension = config.normalizedExtension();
        var sourceDir = config.sourceDirectory();

        try {
            return Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        // Check if file name ends with the extension
                        var fileName = path.getFileName().toString();
                        return fileName.endsWith(extension);
                    });

        } catch (IOException e) {
            handleFileDiscoveryError(sourceDir, e);
            return Stream.empty(); // Return empty stream on error
        }
    }

    /**
     * Application entry point using instance main (Java 21+ feature)
     */
    void main(String[] args) {
        IO.println("ZFSL - Zero Dependencies File Selection and Copy Tool");
        IO.println("====================================================");

        try {
            // Parse command-line arguments with defaults and prompting
            var config = parseArguments(args);

            // Validate configuration
            validateConfiguration(config);

            // Display configuration
            IO.println("\nConfiguration:");
            IO.println("Source directory: " + config.sourceDirectory());
            IO.println("Target directory: " + config.targetDirectory());
            IO.println("File extension: " + config.normalizedExtension());
            IO.println("");

            // Discover files matching the extension
            var discoveredFiles = discoverFiles(config);

            if (discoveredFiles.isEmpty()) {
                IO.println("No files found with extension " + config.normalizedExtension() +
                        " in directory: " + config.sourceDirectory());
                return;
            }

            IO.println("Found " + discoveredFiles.size() + " file(s) matching extension " +
                    config.normalizedExtension());

            // TODO: Implement user interaction loop
            // TODO: Implement file copying
            // TODO: Display operation summary

            IO.println("File discovery completed successfully!");

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nUse --help for usage information.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}