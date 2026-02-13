# Parsing and Execution

Parse result handling, execution strategies, and exception management for robust command line processing with detailed error reporting and flexible execution models.

## Capabilities

### ParseResult Class

Contains the results of parsing command line arguments with detailed information about matched options, parameters, and subcommands.

```java { .api }
public static class ParseResult {
    /**
     * Checks if the specified option was matched during parsing
     * @param option the option name to check
     * @return true if the option was matched
     */
    public boolean hasMatchedOption(String option);
    
    /**
     * Gets the value of a matched option with default fallback
     * @param option the option name
     * @param defaultValue default value if option not matched
     * @return the option value or default
     */
    public <T> T matchedOptionValue(String option, T defaultValue);
    
    /**
     * Gets matched positional parameter values
     * @param index the parameter index
     * @param defaultValue default value if not matched
     * @return list of matched values or default
     */
    public List<String> matchedPositionalValue(int index, List<String> defaultValue);
    
    /**
     * Gets all matched options
     * @return map of option names to matched values
     */
    public Map<String, Object> matchedOptions();
    
    /**
     * Gets all matched positional parameters
     * @return list of matched positional parameters
     */
    public List<String> matchedPositionals();
    
    /**
     * Checks if help was requested
     * @return true if help flag was matched
     */
    public boolean isUsageHelpRequested();
    
    /**
     * Checks if version help was requested
     * @return true if version flag was matched
     */
    public boolean isVersionHelpRequested();
    
    /**
     * Gets unmatched arguments
     * @return list of arguments that couldn't be matched
     */
    public List<String> unmatched();
    
    /**
     * Gets the CommandSpec for the parsed command
     * @return the command specification
     */
    public CommandSpec commandSpec();
    
    /**
     * Gets subcommand parse results
     * @return list of ParseResult for matched subcommands
     */
    public List<ParseResult> subcommands();
}
```

**Usage Examples:**

```java
@Command(name = "app")
class MyApp {
    @Option(names = {"-v", "--verbose"}) boolean verbose;
    @Option(names = {"-f", "--file"}) String filename;
    @Parameters List<String> files;
}

CommandLine cmd = new CommandLine(new MyApp());
ParseResult parseResult = cmd.parseArgs("-v", "--file", "config.xml", "input1.txt", "input2.txt");

// Check for specific options
if (parseResult.hasMatchedOption("-v")) {
    System.out.println("Verbose mode enabled");
}

// Get option values with defaults
String filename = parseResult.matchedOptionValue("--file", "default.xml");
List<String> files = parseResult.matchedPositionalValue(0, Collections.emptyList());

// Handle help requests
if (parseResult.isUsageHelpRequested()) {
    cmd.usage(System.out);
    return;
}
```

### Execution Strategies

Different strategies for executing parsed commands in complex command hierarchies.

```java { .api }
/**
 * Interface for command execution strategies
 */
public interface IExecutionStrategy {
    /**
     * Executes the command based on parse results
     * @param parseResult the parsing results
     * @return exit code
     * @throws ExecutionException if execution fails
     */
    int execute(ParseResult parseResult) throws ExecutionException;
}

/**
 * Executes only the first command in the parse result
 */
public static class RunFirst extends AbstractParseResultHandler<List<Object>> implements IParseResultHandler {
    /**
     * Executes the first command only
     */
    public int execute(ParseResult parseResult) throws ExecutionException;
}

/**
 * Executes only the last command in the parse result
 */
public static class RunLast extends AbstractParseResultHandler<List<Object>> implements IParseResultHandler {
    /**
     * Executes the last command only
     */
    public int execute(ParseResult parseResult) throws ExecutionException;
}

/**
 * Executes all commands in the parse result
 */
public static class RunAll extends AbstractParseResultHandler<List<Object>> implements IParseResultHandler {
    /**
     * Executes all commands in order
     */
    public int execute(ParseResult parseResult) throws ExecutionException;
}
```

**Usage Examples:**

```java
@Command(name = "parent")
class ParentCommand implements Runnable {
    public void run() { System.out.println("Parent executed"); }
}

@Command(name = "child")
class ChildCommand implements Runnable {
    public void run() { System.out.println("Child executed"); }
}

CommandLine parent = new CommandLine(new ParentCommand());
parent.addSubcommand("child", new ChildCommand());

// Using different execution strategies
CommandLine.RunFirst runFirst = new CommandLine.RunFirst();
CommandLine.RunLast runLast = new CommandLine.RunLast();
CommandLine.RunAll runAll = new CommandLine.RunAll();

ParseResult parseResult = parent.parseArgs("child");

// Only executes ParentCommand
runFirst.execute(parseResult);

// Only executes ChildCommand
runLast.execute(parseResult);

// Executes both ParentCommand and ChildCommand
runAll.execute(parseResult);
```

### Exception Handling

Comprehensive exception hierarchy for handling parsing and execution errors.

```java { .api }
/**
 * Base exception for all picocli exceptions
 */
public static class PicocliException extends RuntimeException {
    public PicocliException(String message);
    public PicocliException(String message, Throwable cause);
}

/**
 * Exception during command initialization
 */
public static class InitializationException extends PicocliException {
    public InitializationException(String message);
    public InitializationException(String message, Throwable cause);
}

/**
 * Exception during command execution
 */
public static class ExecutionException extends PicocliException {
    public ExecutionException(CommandLine commandLine, String message);
    public ExecutionException(CommandLine commandLine, String message, Throwable cause);
}

/**
 * Exception during type conversion
 */
public static class TypeConversionException extends PicocliException {
    public TypeConversionException(String message);
}

/**
 * Base exception for parameter parsing errors
 */
public static class ParameterException extends PicocliException {
    public ParameterException(CommandLine commandLine, String message);
    public ParameterException(CommandLine commandLine, String message, Throwable cause);
    
    /**
     * Gets the CommandLine where the error occurred
     */
    public CommandLine getCommandLine();
}

/**
 * Required parameter was not provided
 */
public static class MissingParameterException extends ParameterException {
    public MissingParameterException(CommandLine commandLine, ArgSpec missing, String message);
}

/**
 * Mutually exclusive arguments were both provided
 */
public static class MutuallyExclusiveArgsException extends ParameterException {
    public MutuallyExclusiveArgsException(CommandLine commandLine, String message);
}

/**
 * Unmatched command line arguments
 */
public static class UnmatchedArgumentException extends ParameterException {
    public UnmatchedArgumentException(CommandLine commandLine, List<String> unmatched);
    
    /**
     * Gets the unmatched arguments
     */
    public List<String> getUnmatched();
}

/**
 * Maximum number of values exceeded for an option
 */
public static class MaxValuesExceededException extends ParameterException {
    public MaxValuesExceededException(CommandLine commandLine, String message);
}

/**
 * Option value was overwritten when not allowed
 */
public static class OverwrittenOptionException extends ParameterException {
    public OverwrittenOptionException(CommandLine commandLine, ArgSpec overwritten, String message);
}

/**
 * Type converter missing for parameter type
 */
public static class MissingTypeConverterException extends ParameterException {
    public MissingTypeConverterException(CommandLine commandLine, String message);
}
```

**Usage Example:**

```java
try {
    int exitCode = new CommandLine(new MyApp()).execute(args);
    System.exit(exitCode);
} catch (ParameterException ex) {
    System.err.println("Parameter error: " + ex.getMessage());
    ex.getCommandLine().usage(System.err);
    System.exit(2);
} catch (ExecutionException ex) {
    System.err.println("Execution error: " + ex.getMessage());
    ex.printStackTrace();
    System.exit(1);
}
```

### Exception Handlers

Interfaces for custom exception handling during parsing and execution.

```java { .api }
/**
 * Handles parameter parsing exceptions
 */
public interface IParameterExceptionHandler {
    /**
     * Handles a parameter parsing exception
     * @param ex the parameter exception
     * @param args the command line arguments
     * @return exit code
     * @throws Exception if handling fails
     */
    int handleParseException(ParameterException ex, String[] args) throws Exception;
}

/**
 * Handles execution exceptions
 */
public interface IExecutionExceptionHandler {
    /**
     * Handles an execution exception
     * @param ex the execution exception
     * @param commandLine the CommandLine where the exception occurred
     * @param parseResult the parse result
     * @return exit code
     * @throws Exception if handling fails
     */
    int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) throws Exception;
}

/**
 * Default exception handler implementation
 */
public static class DefaultExceptionHandler<R> implements IParameterExceptionHandler, IExecutionExceptionHandler {
    public int handleParseException(ParameterException ex, String[] args);
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult);
}
```

**Usage Example:**

```java
// Custom parameter exception handler
IParameterExceptionHandler paramHandler = (ex, args) -> {
    System.err.println("Custom parameter error: " + ex.getMessage());
    ex.getCommandLine().usage(System.err);
    return 2; // Custom exit code
};

// Custom execution exception handler
IExecutionExceptionHandler execHandler = (ex, cmd, parseResult) -> {
    System.err.println("Custom execution error: " + ex.getMessage());
    if (ex instanceof FileNotFoundException) {
        System.err.println("File not found. Please check the file path.");
        return 3; // File not found exit code
    }
    return 1; // General error
};

CommandLine cmd = new CommandLine(new MyApp());
cmd.setParameterExceptionHandler(paramHandler);
cmd.setExecutionExceptionHandler(execHandler);

int exitCode = cmd.execute(args);
```

### Exit Code Management

Interfaces and utilities for managing command exit codes.

```java { .api }
/**
 * Generates exit codes from command execution results
 */
public interface IExitCodeGenerator {
    /**
     * Generates an exit code for the command result
     * @return exit code
     */
    int generateExitCode();
}

/**
 * Maps exceptions to exit codes
 */
public interface IExitCodeExceptionMapper {
    /**
     * Maps an exception to an exit code
     * @param ex the exception
     * @return exit code
     */
    int getExitCode(Throwable ex);
}

/**
 * Standard exit codes
 */
public static final class ExitCode {
    public static final int OK = 0;        // Success
    public static final int SOFTWARE = 1;  // Software error
    public static final int USAGE = 2;     // Usage error
}
```

**Usage Example:**

```java
@Command(name = "processor")
class DataProcessor implements Runnable, IExitCodeGenerator {
    @Option(names = "-f") String filename;
    
    private int exitCode = 0;
    
    public void run() {
        try {
            processFile(filename);
            exitCode = ExitCode.OK;
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + filename);
            exitCode = 3; // Custom file not found exit code
        } catch (Exception e) {
            System.err.println("Processing error: " + e.getMessage());
            exitCode = ExitCode.SOFTWARE;
        }
    }
    
    @Override
    public int generateExitCode() {
        return exitCode;
    }
}

// The exit code will be determined by the IExitCodeGenerator
int exitCode = new CommandLine(new DataProcessor()).execute(args);
```