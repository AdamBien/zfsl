import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.io.IOException;

/**
 * Zero Dependencies File Selection and Copy Tool (zFSL)
 * Interactive CLI application for selective file copying
 */
public class ZFSL {

    record Config(
            Path sourceDirectory,
            Path targetDirectory,
            String fileExtension) {
        public Config{Objects.requireNonNull(sourceDirectory,"Source directory cannot be null");Objects.requireNonNull(targetDirectory,"Target directory cannot be null");Objects.requireNonNull(fileExtension,"File extension cannot be null");

        if(fileExtension.trim().isEmpty()){throw new IllegalArgumentException("File extension cannot be empty");}}

        boolean isSourceValid() {
            return Files.exists(sourceDirectory) && Files.isDirectory(sourceDirectory)
                    && Files.isReadable(sourceDirectory);
        }

        boolean isTargetValid() {
            if (Files.exists(targetDirectory)) {
                return Files.isDirectory(targetDirectory) && Files.isWritable(targetDirectory);
            }
            // Check if parent directory exists and is writable for creation
            var parent = targetDirectory.getParent();
            return parent != null && Files.exists(parent) && Files.isWritable(parent);
        }

        String normalizedExtension() {
            return fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        }
    }

    sealed interface OperationResult
            permits OperationResult.Success, OperationResult.Skip, OperationResult.Error {

        record Success(Path source, Path target) implements OperationResult {
        }

        record Skip(Path source, String reason) implements OperationResult {
        }

        record Error(Path source, String message, Throwable cause) implements OperationResult {
        }
    }

    record ProcessingState(
            int totalFiles,
            int copiedFiles,
            int skippedFiles,
            int errorFiles,
            List<OperationResult> results) {
        static ProcessingState initial() {
            return new ProcessingState(0, 0, 0, 0, new ArrayList<>());
        }

        ProcessingState withTotalFiles(int total) {
            return new ProcessingState(total, copiedFiles, skippedFiles, errorFiles, results);
        }

        ProcessingState withResult(OperationResult result) {
            var newResults = new ArrayList<>(results);
            newResults.add(result);

            return switch (result) {
                case OperationResult.Success success ->
                    new ProcessingState(totalFiles, copiedFiles + 1, skippedFiles, errorFiles, newResults);
                case OperationResult.Skip skip ->
                    new ProcessingState(totalFiles, copiedFiles, skippedFiles + 1, errorFiles, newResults);
                case OperationResult.Error error ->
                    new ProcessingState(totalFiles, copiedFiles, skippedFiles, errorFiles + 1, newResults);
            };
        }

        String formatSummary() {
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

    sealed interface UserAction
            permits UserAction.Copy, UserAction.Skip, UserAction.Quit {

        record Copy(Path filePath) implements UserAction {
        }

        record Skip(Path filePath) implements UserAction {
        }

        record Quit(Path filePath) implements UserAction {
        }
    }

    static void displayHelp() {
        System.out.println("""
                ZFSL - Zero Dependencies File Selection and Copy Tool
                ====================================================

                Usage: java ZFSL.java [OPTIONS]

                Options:
                  -s, --source <directory>     Source directory to search (default: current directory)
                  -t, --target <directory>     Target directory for copying files (required if not prompted)
                  -e, --extension <extension>  File extension to search for (required if not prompted)
                  -h, --help                   Display this help message

                Examples:
                  java ZFSL.java                                    # Interactive mode with prompts
                  java ZFSL.java -s /path/to/source -t /path/to/target -e .java
                  java ZFSL.java --source ./src --target ./backup --extension txt

                Interactive Mode:
                  If target directory or file extension are not provided, you will be prompted
                  to enter them interactively.
                """);
    }

    static String promptForInput(String prompt) {
        var console = System.console();
        if (console != null) {
            var input = console.readLine(prompt + ": ");
            return input != null ? input.trim() : "";
        } else {
            // Fallback for environments without console (like IDEs)
            System.out.print(prompt + ": ");
            try (var scanner = new Scanner(System.in)) {
                return scanner.nextLine().trim();
            }
        }
    }

    static Config parseArguments(String[] args) {
        var sourceDirectory = Path.of(System.getProperty("user.dir")); // Default to current directory
        var targetDirectory = (Path) null;
        var fileExtension = (String) null;

        // Parse arguments using modern switch expressions
        for (var i = 0; i < args.length; i++) {
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

    static List<Path> discoverFiles(Config config) {
        var extension = config.normalizedExtension();
        var sourceDir = config.sourceDirectory();

        try (var fileStream = Files.walk(sourceDir)) {
            return fileStream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        var fileName = path.getFileName().toString();
                        return fileName.endsWith(extension);
                    })
                    .collect(Collectors.toList());

        } catch (IOException e) {
            handleFileDiscoveryError(sourceDir, e);
            return List.of();
        }
    }

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

        if (error.getCause() != null) {
            System.err.println("Underlying cause: " + error.getCause().getMessage());
        }
    }

    static String promptUserForFile(Path filePath) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("File: " + filePath);
        System.out.println("Size: " + getFileSize(filePath));
        System.out.println("Last modified: " + getLastModified(filePath));
        System.out.println("=".repeat(60));

        displayFileContents(filePath);

        System.out.println("\nWhat would you like to do with this file?");
        System.out.println("  y/yes - Copy this file");
        System.out.println("  n/no  - Skip this file");
        System.out.println("  q/quit - Exit the application");

        return readUserResponse();
    }

    static String getFileSize(Path filePath) {
        try {
            var size = Files.size(filePath);
            if (size < 1024) {
                return size + " bytes";
            } else if (size < 1024 * 1024) {
                return String.format("%.1f KB", size / 1024.0);
            } else {
                return String.format("%.1f MB", size / (1024.0 * 1024.0));
            }
        } catch (IOException e) {
            return "Unknown size";
        }
    }

    static String getLastModified(Path filePath) {
        try {
            var lastModified = Files.getLastModifiedTime(filePath);
            return lastModified.toString();
        } catch (IOException e) {
            return "Unknown date";
        }
    }

    // Shows first 20 lines to help user decide whether to copy
    static void displayFileContents(Path filePath) {
        try {
            var lines = Files.readAllLines(filePath);
            var totalLines = lines.size();

            System.out.println("\nFile contents (" + totalLines + " lines):");
            System.out.println("-".repeat(40));

            var linesToShow = Math.min(20, totalLines);
            for (var i = 0; i < linesToShow; i++) {
                System.out.println(String.format("%3d: %s", i + 1, lines.get(i)));
            }

            if (totalLines > 20) {
                System.out.println("... (" + (totalLines - 20) + " more lines)");
            }

            System.out.println("-".repeat(40));

        } catch (IOException e) {
            System.out.println("\nCould not read file contents: " + e.getMessage());
        }
    }

    // Loops until valid response, handles both console and scanner input
    static String readUserResponse() {
        var console = System.console();
        var scanner = (Scanner) null;

        if (console == null) {
            scanner = new Scanner(System.in);
        }

        while (true) {
            System.out.print("\nYour choice [y/n/q]: ");

            var input = (String) null;
            if (console != null) {
                input = console.readLine();
            } else {
                input = scanner.nextLine();
            }

            if (input == null) {
                input = "";
            }

            var response = parseUserResponse(input.trim());
            if (response != null) {
                return response;
            }

            System.out.println("Invalid response. Please enter:");
            System.out.println("  y or yes - to copy the file");
            System.out.println("  n or no  - to skip the file");
            System.out.println("  q or quit - to exit the application");
        }
    }

    static String parseUserResponse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        return switch (input.toLowerCase()) {
            case "y", "yes" -> "y";
            case "n", "no" -> "n";
            case "q", "quit" -> "q";
            default -> null;
        };
    }

    static UserAction processUserDecision(String response, Path filePath) {
        return switch (response) {
            case "y" -> new UserAction.Copy(filePath);
            case "n" -> new UserAction.Skip(filePath);
            case "q" -> new UserAction.Quit(filePath);
            default -> throw new IllegalArgumentException("Invalid response: " + response);
        };
    }

    public static void main(String[] args) {
        System.out.println("ZFSL - Zero Dependencies File Selection and Copy Tool");
        System.out.println("====================================================");

        try {
            var config = parseArguments(args);
            validateConfiguration(config);

            System.out.println("\nConfiguration:");
            System.out.println("Source directory: " + config.sourceDirectory());
            System.out.println("Target directory: " + config.targetDirectory());
            System.out.println("File extension: " + config.normalizedExtension());
            System.out.println("");

            var discoveredFiles = discoverFiles(config);

            if (discoveredFiles.isEmpty()) {
                System.out.println("No files found with extension " + config.normalizedExtension() +
                        " in directory: " + config.sourceDirectory());
                return;
            }

            System.out.println("Found " + discoveredFiles.size() + " file(s) matching extension " +
                    config.normalizedExtension());

            // TODO: Implement user interaction loop
            // TODO: Implement file copying
            // TODO: Display operation summary

            System.out.println("File discovery completed successfully!");

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