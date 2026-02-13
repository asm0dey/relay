# CommandLine Core

Central command line parsing and execution functionality including argument parsing, subcommand handling, configuration options, and execution strategies. The CommandLine class serves as the main entry point for all picocli functionality.

## Capabilities

### CommandLine Class

Main class for parsing command line arguments and executing commands with extensive configuration options.

```java { .api }
public class CommandLine {
    public static final String VERSION = "4.7.7";
    
    /**
     * Creates a CommandLine instance from an annotated command object
     * @param command the annotated command object
     */
    public CommandLine(Object command);
    
    /**
     * Creates a CommandLine instance with a custom factory
     * @param command the annotated command object
     * @param factory factory for creating instances of command classes
     */
    public CommandLine(Object command, IFactory factory);
    
    /**
     * Parses and executes the command with the specified arguments
     * @param args the command line arguments
     * @return exit code (0 for success, non-zero for errors)
     */
    public int execute(String... args);
    
    /**
     * Parses command line arguments without executing
     * @param args the command line arguments to parse
     * @return ParseResult containing parsed arguments
     */
    public ParseResult parseArgs(String... args);
    
    /**
     * Gets the parse result from the last parsing operation
     * @return ParseResult or null if no parsing has occurred
     */
    public ParseResult getParseResult();
    
    /**
     * Gets the command specification for this CommandLine
     * @return the CommandSpec model
     */
    public CommandSpec getCommandSpec();
    
    /**
     * Gets the command object instance
     * @return the command instance
     */
    public <T> T getCommand();
    
    // Configuration Methods
    public boolean isPosixClusteredShortOptionsAllowed();
    public CommandLine setPosixClusteredShortOptionsAllowed(boolean newValue);
    public boolean isCaseInsensitiveEnumValuesAllowed();
    public CommandLine setCaseInsensitiveEnumValuesAllowed(boolean newValue);
    public boolean isTrimQuotes();
    public CommandLine setTrimQuotes(boolean newValue);
    public String getEndOfOptionsDelimiter();
    public CommandLine setEndOfOptionsDelimiter(String delimiter);
    public boolean isSubcommandsCaseInsensitive();
    public CommandLine setSubcommandsCaseInsensitive(boolean newValue);
    public boolean isOptionsCaseInsensitive();
    public CommandLine setOptionsCaseInsensitive(boolean newValue);
    public boolean isAbbreviatedSubcommandsAllowed();
    public CommandLine setAbbreviatedSubcommandsAllowed(boolean newValue);
    public boolean isAbbreviatedOptionsAllowed();
    public CommandLine setAbbreviatedOptionsAllowed(boolean newValue);
    public boolean isOverwrittenOptionsAllowed();
    public CommandLine setOverwrittenOptionsAllowed(boolean newValue);
    public boolean isStopAtPositional();
    public CommandLine setStopAtPositional(boolean newValue);
    public boolean isStopAtUnmatched();
    public CommandLine setStopAtUnmatched(boolean newValue);
    public boolean isUnmatchedArgumentsAllowed();
    public CommandLine setUnmatchedArgumentsAllowed(boolean newValue);
    public List<String> getUnmatchedArguments();
    
    // I/O Configuration
    public PrintWriter getOut();
    public CommandLine setOut(PrintWriter out);
    public PrintWriter getErr();
    public CommandLine setErr(PrintWriter err);
    public Help.ColorScheme getColorScheme();
    public CommandLine setColorScheme(Help.ColorScheme colorScheme);
    
    // Help Methods
    public Help getHelp();
    public IHelpFactory getHelpFactory();
    public CommandLine setHelpFactory(IHelpFactory helpFactory);
    public void usage(PrintStream out);
    public void usage(PrintWriter writer);
    public void usage(PrintStream out, Help.Ansi ansi);
    public void usage(PrintWriter writer, Help.ColorScheme colorScheme);
    public String getUsageMessage();
    public String getUsageMessage(Help.Ansi ansi);
    public String getUsageMessage(Help.ColorScheme colorScheme);
    public void printVersionHelp(PrintStream out);
    public void printVersionHelp(PrintWriter out, Help.Ansi ansi, Object... params);
    
    // Exception Handling
    public IExitCodeExceptionMapper getExitCodeExceptionMapper();
    public CommandLine setExitCodeExceptionMapper(IExitCodeExceptionMapper mapper);
    public IExecutionStrategy getExecutionStrategy();
    public CommandLine setExecutionStrategy(IExecutionStrategy executionStrategy);
    public IParameterExceptionHandler getParameterExceptionHandler();
    public CommandLine setParameterExceptionHandler(IParameterExceptionHandler handler);
    public IExecutionExceptionHandler getExecutionExceptionHandler();
    public CommandLine setExecutionExceptionHandler(IExecutionExceptionHandler handler);
}
```

**Usage Examples:**

```java
// Basic execution
@Command(name = "myapp")
class MyApp implements Runnable {
    @Option(names = "-v") boolean verbose;
    
    public void run() {
        System.out.println("Running " + (verbose ? "verbosely" : "quietly"));
    }
}

public static void main(String[] args) {
    int exitCode = new CommandLine(new MyApp()).execute(args);
    System.exit(exitCode);
}

// Parse without executing
CommandLine cmd = new CommandLine(new MyApp());
ParseResult parseResult = cmd.parseArgs("-v", "input.txt");
if (parseResult.hasMatchedOption("-v")) {
    System.out.println("Verbose flag was set");
}
```

### Subcommand Management

Methods for adding and managing subcommands in hierarchical command structures.

```java { .api }
/**
 * Adds a subcommand with the class name as command name
 * @param command the subcommand object
 * @return this CommandLine instance for method chaining
 */
public CommandLine addSubcommand(Object command);

/**
 * Adds a subcommand with specified name
 * @param name the command name
 * @param command the subcommand object
 * @return this CommandLine instance for method chaining
 */
public CommandLine addSubcommand(String name, Object command);

/**
 * Adds a subcommand with name and aliases
 * @param name the command name
 * @param command the subcommand object
 * @param aliases alternative names for the command
 * @return this CommandLine instance for method chaining
 */
public CommandLine addSubcommand(String name, Object command, String... aliases);

/**
 * Gets all registered subcommands
 * @return map of command names to CommandLine instances
 */
public Map<String, CommandLine> getSubcommands();

/**
 * Gets the parent CommandLine if this is a subcommand
 * @return parent CommandLine or null if this is the root command
 */
public CommandLine getParent();
```

**Usage Example:**

```java
@Command(name = "git")
class Git implements Runnable {
    public void run() { /* show general help */ }
}

@Command(name = "add")
class GitAdd implements Runnable {
    @Parameters String[] files;
    public void run() { /* implement git add */ }
}

@Command(name = "commit")
class GitCommit implements Runnable {
    @Option(names = "-m") String message;
    public void run() { /* implement git commit */ }
}

// Set up command hierarchy
CommandLine git = new CommandLine(new Git());
git.addSubcommand("add", new GitAdd());
git.addSubcommand("commit", new GitCommit());

// Execute: git add file1.txt file2.txt
int exitCode = git.execute("add", "file1.txt", "file2.txt");
```

### Configuration Options

Methods for configuring parsing behavior and command execution.

```java { .api }
/**
 * Sets whether boolean flags can be toggled with += syntax
 */
public CommandLine setToggleBooleanFlags(boolean newValue);
public boolean isToggleBooleanFlags();

/**
 * Sets whether options can be overwritten by later occurrences
 */
public CommandLine setOverwrittenOptionsAllowed(boolean newValue);
public boolean isOverwrittenOptionsAllowed();

/**
 * Sets whether POSIX-style clustered short options are allowed (-abc = -a -b -c)
 */
public CommandLine setPosixClusteredShortOptionsAllowed(boolean newValue);
public boolean isPosixClusteredShortOptionsAllowed();

/**
 * Sets whether enum values are case insensitive
 */
public CommandLine setCaseInsensitiveEnumValuesAllowed(boolean newValue);
public boolean isCaseInsensitiveEnumValuesAllowed();

/**
 * Sets whether quotes are trimmed from option values
 */
public CommandLine setTrimQuotes(boolean newValue);
public boolean isTrimQuotes();

/**
 * Sets whether subcommands are case insensitive
 */
public CommandLine setSubcommandsCaseInsensitive(boolean newValue);
public boolean isSubcommandsCaseInsensitive();

/**
 * Sets whether options are case insensitive
 */
public CommandLine setOptionsCaseInsensitive(boolean newValue);
public boolean isOptionsCaseInsensitive();

/**
 * Sets whether abbreviated subcommands are allowed
 */
public CommandLine setAbbreviatedSubcommandsAllowed(boolean newValue);
public boolean isAbbreviatedSubcommandsAllowed();

/**
 * Sets whether abbreviated options are allowed
 */
public CommandLine setAbbreviatedOptionsAllowed(boolean newValue);
public boolean isAbbreviatedOptionsAllowed();

/**
 * Sets whether parsing stops at first positional parameter
 */
public CommandLine setStopAtPositional(boolean newValue);
public boolean isStopAtPositional();

/**
 * Sets whether parsing stops at first unmatched argument
 */
public CommandLine setStopAtUnmatched(boolean newValue);
public boolean isStopAtUnmatched();

/**
 * Sets the end-of-options delimiter (default: "--")
 */
public CommandLine setEndOfOptionsDelimiter(String delimiter);
public String getEndOfOptionsDelimiter();
```

**Usage Example:**

```java
CommandLine cmd = new CommandLine(new MyApp());

// Configure parsing behavior
cmd.setPosixClusteredShortOptionsAllowed(true)   // Allow -abc
   .setCaseInsensitiveEnumValuesAllowed(true)    // Case insensitive enums
   .setToggleBooleanFlags(false)                 // Disable toggle syntax
   .setTrimQuotes(true)                          // Trim quotes from values
   .setAbbreviatedOptionsAllowed(true);          // Allow abbreviated options

int exitCode = cmd.execute(args);
```

### Factory and Provider Configuration

Methods for configuring factories and providers for customization.

```java { .api }
/**
 * Gets the factory used for creating command instances
 */
public IFactory getFactory();

/**
 * Sets the default value provider for options and parameters
 */
public CommandLine setDefaultValueProvider(IDefaultValueProvider provider);
public IDefaultValueProvider getDefaultValueProvider();

/**
 * Sets the help factory for custom help generation
 */
public CommandLine setHelpFactory(IHelpFactory helpFactory);
public IHelpFactory getHelpFactory();
```

### Help Generation

Methods for generating and customizing usage help messages.

```java { .api }
/**
 * Gets the Help object for this command
 */
public Help getHelp();

/**
 * Checks if help was requested in the last parse operation
 */
public boolean isUsageHelpRequested();

/**
 * Checks if version help was requested in the last parse operation
 */
public boolean isVersionHelpRequested();

/**
 * Gets the list of help section keys in display order
 */
public List<String> getHelpSectionKeys();

/**
 * Sets the help section keys in display order
 */
public CommandLine setHelpSectionKeys(List<String> keys);

/**
 * Gets the map of help section renderers
 */
public Map<String, IHelpSectionRenderer> getHelpSectionMap();

/**
 * Sets the map of help section renderers
 */
public CommandLine setHelpSectionMap(Map<String, IHelpSectionRenderer> map);
```

### Static Utility Methods

Convenience methods for common operations without creating CommandLine instances.

```java { .api }
/**
 * Populates an annotated command object with parsed arguments
 */
public static <T> T populateCommand(T command, String... args);

/**
 * Populates a command specification class with parsed arguments
 */
public static <T> T populateSpec(Class<T> spec, String... args);

/**
 * Prints usage help for a command to System.out
 */
public static void usage(Object command, PrintStream out);
public static void usage(Object command, PrintStream out, Help.Ansi ansi);
public static void usage(Object command, PrintStream out, Help.ColorScheme colorScheme);

/**
 * Handles help requests from parse results
 */
public static boolean printHelpIfRequested(ParseResult parseResult);
public static Integer executeHelpRequest(ParseResult parseResult);

/**
 * Gets methods annotated with @Command in a class
 */
public static List<Method> getCommandMethods(Class<?> cls, String methodName);

/**
 * Gets the default factory instance
 */
public static IFactory defaultFactory();

/**
 * Gets the default exception handler
 */
public static DefaultExceptionHandler<List<Object>> defaultExceptionHandler();
```

**Usage Examples:**

```java
// Quick population without CommandLine instance
@Command(name = "quick")
class QuickCommand {
    @Option(names = "-v") boolean verbose;
    @Parameters String[] files;
}

QuickCommand cmd = CommandLine.populateCommand(new QuickCommand(), args);

// Print usage help
CommandLine.usage(new MyCommand(), System.out);

// Handle help requests
ParseResult parseResult = commandLine.parseArgs(args);
if (CommandLine.printHelpIfRequested(parseResult)) {
    return; // Help was printed, exit
}
```

## Supporting Interfaces

```java { .api }
/**
 * Factory for creating instances of command classes
 */
public interface IFactory {
    <K> K create(Class<K> cls) throws Exception;
}

/**
 * Strategy for executing parsed commands
 */
public interface IExecutionStrategy {
    int execute(ParseResult parseResult) throws ExecutionException;
}

/**
 * Handles parameter parsing exceptions
 */
public interface IParameterExceptionHandler {
    int handleParseException(ParameterException ex, String[] args) throws Exception;
}

/**
 * Handles execution exceptions
 */
public interface IExecutionExceptionHandler {
    int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) throws Exception;
}
```

### Static Convenience Methods

Utility methods for common operations without needing to create CommandLine instances explicitly.

```java { .api }
/**
 * Parses the specified command line arguments and populates the annotated fields and methods
 * @param command the command object to populate
 * @param args the command line arguments
 * @return the populated command object
 */
public static <T> T populateCommand(T command, String... args);

/**
 * Parses the specified command line arguments and populates a new instance of the specified class
 * @param spec the command class
 * @param args the command line arguments
 * @return the populated command instance
 */
public static <T> T populateSpec(Class<T> spec, String... args);

/**
 * Delegates to {@link #usage(Object, PrintStream, Help.Ansi)} with the platform default Ansi setting
 */
public static void usage(Object command, PrintStream out);

/**
 * Delegates to {@link #usage(Object, PrintStream, Help.ColorScheme)} with a default color scheme
 */
public static void usage(Object command, PrintStream out, Help.Ansi ansi);

/**
 * Prints usage help message for the specified command to the specified PrintStream
 */
public static void usage(Object command, PrintStream out, Help.ColorScheme colorScheme);

/**
 * Returns true if help was requested, after printing help if requested
 */
public static boolean printHelpIfRequested(ParseResult parseResult);

/**
 * Executes help request if any, returns exit code or null if no help was requested
 */
public static Integer executeHelpRequest(ParseResult parseResult);

/**
 * Returns the list of annotated methods in the specified class with the specified name
 */
public static List<Method> getCommandMethods(Class<?> cls, String methodName);
```