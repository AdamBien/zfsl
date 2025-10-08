import java.nio.file.*;
import java.util.*;

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
     * Application entry point using instance main (Java 21+ feature)
     */
    void main(String[] args) {
        System.out.println("ZFSL - Zero Dependencies File Selection and Copy Tool");
        System.out.println("====================================================");

        // TODO: Implement command-line argument parsing
        // TODO: Implement file discovery
        // TODO: Implement user interaction loop
        // TODO: Implement file copying
        // TODO: Display operation summary

        System.out.println("Application structure initialized successfully!");
    }
}