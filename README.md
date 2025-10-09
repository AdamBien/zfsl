# ZFSL - Zero Dependencies File Selection and Copy Tool

Interactive CLI for selective file copying with content preview. Built with Java 25 as a single-file unnamed class.

## Usage

```bash
# Interactive mode
java ZFSL.java

# Command line mode
java ZFSL.java -s ./src -t ./backup -e .java
```

### Options

```
-s, --source <directory>     Source directory (default: current directory)
-t, --target <directory>     Target directory (prompts if not provided)
-e, --extension <extension>  File extension filter (prompts if not provided)
-h, --help                   Display help
```

### Interactive Flow

For each discovered file:
1. Preview first 20 lines with metadata (size, last modified)
2. Choose: `y` (copy), `n` (skip), or `q` (quit)
3. Confirm overwrite if target file exists
4. View operation summary with detailed error report

## Technical Details

**Requirements:** Java 25+

**Design:**
- Single file unnamed class
- Zero dependencies (Java SE only)
- Records for immutable data (`Config`, `ProcessingState`)
- Sealed interfaces for type-safe result handling (`OperationResult`, `UserAction`)
- Pattern matching in switch expressions
- Text blocks for multi-line output

**Key Components:**
- `Config`: Validates source/target directories and normalizes file extensions
- `OperationResult`: Success/Skip/Error outcomes with detailed context
- `ProcessingState`: Tracks file counts and results, formats summary reports
- `UserAction`: Copy/Skip/Quit decisions from user input

**File Operations:**
- Recursive discovery via `Files.walk()`
- Flat copy structure (no subdirectories preserved)
- Overwrites only with explicit confirmation
- Specific error messages for `AccessDeniedException`, `FileSystemException`, etc.

## Examples

```bash
# Copy Java files from src to backup
java ZFSL.java -s ./src -t ./backup -e .java

# Interactive selection from current directory
java ZFSL.java -t ./output -e txt
```