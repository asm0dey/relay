# Help System

Comprehensive help generation system with ANSI styling, customizable sections, and usage message formatting. Supports both automatic help generation and custom help rendering with extensive customization options.

## Capabilities

### Help Class

Core help generation functionality with support for ANSI styling and custom formatting.

```java { .api }
public static class Help {
    /**
     * Creates a Help instance for the specified command
     * @param commandSpec the command specification
     */
    public Help(CommandSpec commandSpec);
    
    /**
     * Creates a Help instance with custom color scheme
     * @param commandSpec the command specification
     * @param colorScheme the color scheme for styling
     */
    public Help(CommandSpec commandSpec, ColorScheme colorScheme);
    
    /**
     * Gets the full synopsis including all options and parameters
     */
    public String fullSynopsis();
    
    /**
     * Gets a detailed synopsis with custom formatting
     * @param synopsisHeadingLength length of synopsis heading
     * @param optionSort comparator for sorting options
     * @param clusterOptions whether to cluster boolean options
     */
    public String detailedSynopsis(int synopsisHeadingLength, Comparator<OptionSpec> optionSort, boolean clusterOptions);
    
    /**
     * Gets the complete usage help message
     */
    public String toString();
    
    /**
     * Gets formatted option list
     */
    public String optionList();
    
    /**
     * Gets formatted parameter list
     */
    public String parameterList();
    
    /**
     * Gets formatted command list for subcommands
     */
    public String commandList();
}
```

**Usage Examples:**

```java
@Command(name = "myapp", description = "Example application")
class MyApp {
    @Option(names = {"-v", "--verbose"}) boolean verbose;
    @Parameters(description = "Input files") List<String> files;
}

CommandLine cmd = new CommandLine(new MyApp());
Help help = cmd.getHelp();

// Generate full help text
System.out.println(help.toString());

// Generate just the synopsis
System.out.println("Usage: " + help.fullSynopsis());

// Generate option list only
System.out.println("Options:");
System.out.println(help.optionList());
```

### Help Section Customization

Customizable help sections with predefined section keys and custom renderers.

```java { .api }
/**
 * Standard help section keys in display order
 */
public static final String SECTION_KEY_HEADER_HEADING = "headerHeading";
public static final String SECTION_KEY_HEADER = "header";
public static final String SECTION_KEY_SYNOPSIS_HEADING = "synopsisHeading";
public static final String SECTION_KEY_SYNOPSIS = "synopsis";
public static final String SECTION_KEY_DESCRIPTION_HEADING = "descriptionHeading";
public static final String SECTION_KEY_DESCRIPTION = "description";
public static final String SECTION_KEY_PARAMETER_LIST_HEADING = "parameterListHeading";
public static final String SECTION_KEY_PARAMETER_LIST = "parameterList";
public static final String SECTION_KEY_OPTION_LIST_HEADING = "optionListHeading";
public static final String SECTION_KEY_OPTION_LIST = "optionList";
public static final String SECTION_KEY_COMMAND_LIST_HEADING = "commandListHeading";
public static final String SECTION_KEY_COMMAND_LIST = "commandList";
public static final String SECTION_KEY_EXIT_CODE_LIST_HEADING = "exitCodeListHeading";
public static final String SECTION_KEY_EXIT_CODE_LIST = "exitCodeList";
public static final String SECTION_KEY_FOOTER_HEADING = "footerHeading";
public static final String SECTION_KEY_FOOTER = "footer";

/**
 * Interface for rendering help sections
 */
public interface IHelpSectionRenderer {
    String render(Help help);
}

/**
 * Factory for creating Help instances
 */
public interface IHelpFactory {
    Help create(CommandSpec commandSpec, ColorScheme colorScheme);
}
```

**Usage Example:**

```java
CommandLine cmd = new CommandLine(new MyApp());

// Customize help section order
List<String> sections = Arrays.asList(
    Help.SECTION_KEY_HEADER_HEADING,
    Help.SECTION_KEY_HEADER,
    Help.SECTION_KEY_SYNOPSIS_HEADING,
    Help.SECTION_KEY_SYNOPSIS,
    Help.SECTION_KEY_DESCRIPTION_HEADING,
    Help.SECTION_KEY_DESCRIPTION,
    Help.SECTION_KEY_OPTION_LIST_HEADING,
    Help.SECTION_KEY_OPTION_LIST,
    Help.SECTION_KEY_FOOTER_HEADING,
    Help.SECTION_KEY_FOOTER
);
cmd.setHelpSectionKeys(sections);

// Custom section renderer
Map<String, IHelpSectionRenderer> sectionMap = cmd.getHelpSectionMap();
sectionMap.put(Help.SECTION_KEY_HEADER, help -> 
    "@|bold,underline Custom Application Header|@%n");
cmd.setHelpSectionMap(sectionMap);
```

### ANSI Color Support

ANSI color and styling support for enhanced help message formatting.

```java { .api }
public enum Ansi {
    AUTO, ON, OFF;
    
    /**
     * Enables or disables ANSI colors globally
     */
    public static void setEnabled(boolean enabled);
    
    /**
     * Checks if ANSI colors are enabled
     */
    public static boolean isEnabled();
}

/**
 * Color scheme for styling help messages
 */
public static class ColorScheme {
    /**
     * Creates a default color scheme
     */
    public ColorScheme();
    
    /**
     * Creates a color scheme with ANSI preference
     */
    public ColorScheme(Ansi ansi);
    
    /**
     * Applies color markup to text
     */
    public Text apply(String plainText, String markup);
}
```

**Usage Examples:**

```java
// Enable ANSI colors
Help.Ansi.setEnabled(true);

// Create help with color scheme
CommandLine cmd = new CommandLine(new MyApp());
Help.ColorScheme colorScheme = new Help.ColorScheme(Help.Ansi.ON);
cmd.setColorScheme(colorScheme);

// Print colored help
cmd.usage(System.out);

// Manual color application
@Command(name = "app", 
         header = "@|bold,underline My Application|@",
         description = "@|yellow This is a colored description.|@")
class ColoredApp { }
```

### Built-in Help Command

Pre-built help command for subcommand hierarchies.

```java { .api }
public static final class HelpCommand implements IHelpCommandInitializable, IHelpCommandInitializable2, Runnable, Callable<Integer> {
    /**
     * Default constructor for built-in help command
     */
    public HelpCommand();
    
    /**
     * Initializes the help command with parent command context
     */
    public void init(CommandLine helpCommandLine, Help.Ansi ansi, PrintStream out, PrintStream err);
    
    /**
     * Executes the help command
     */
    public void run();
    
    /**
     * Executes the help command and returns exit code
     */
    public Integer call();
}
```

**Usage Example:**

```java
@Command(name = "myapp", 
         subcommands = {HelpCommand.class, SubCommand1.class, SubCommand2.class})
class MyApp {
    // Main command implementation
}

// Usage: myapp help [COMMAND]
// Shows help for main command or specific subcommand
```

### Help Message Formatting

Control over help message formatting and layout options.

```java { .api }
/**
 * Sets whether line breaks are adjusted for wide CJK characters
 */
public CommandLine setAdjustLineBreaksForWideCJKCharacters(boolean adjustForWideChars);
public boolean isAdjustLineBreaksForWideCJKCharacters();

/**
 * Sets interpolation of variables in help text
 */
public CommandLine setInterpolateVariables(boolean interpolate);
public boolean isInterpolateVariables();
```

**Usage Example:**

```java
@Command(name = "app",
         description = "Application version ${COMMAND-VERSION}",
         footer = "For more information, visit ${COMMAND-NAME}.example.com")
class InterpolatedApp { }

CommandLine cmd = new CommandLine(new InterpolatedApp());
cmd.setInterpolateVariables(true); // Enable variable interpolation

// Variables like ${COMMAND-NAME}, ${COMMAND-VERSION} will be replaced
cmd.usage(System.out);
```

### Static Help Utilities

Static methods for quick help generation without creating CommandLine instances.

```java { .api }
/**
 * Prints usage help to specified output stream
 */
public static void usage(Object command, PrintStream out);
public static void usage(Object command, PrintStream out, Help.Ansi ansi);
public static void usage(Object command, PrintStream out, Help.ColorScheme colorScheme);

/**
 * Handles help requests from parse results
 */
public static boolean printHelpIfRequested(ParseResult parseResult);

/**
 * Executes help request and returns appropriate exit code
 */
public static Integer executeHelpRequest(ParseResult parseResult);
```

**Usage Examples:**

```java
// Quick help printing
CommandLine.usage(new MyApp(), System.out);

// With ANSI colors
CommandLine.usage(new MyApp(), System.out, Help.Ansi.ON);

// Handle help in parse loop
ParseResult parseResult = cmd.parseArgs(args);
if (CommandLine.printHelpIfRequested(parseResult)) {
    return; // Help was printed, exit early
}

// Execute help with proper exit code
Integer exitCode = CommandLine.executeHelpRequest(parseResult);
if (exitCode != null) {
    System.exit(exitCode);
}
```

## Supporting Types

```java { .api }
/**
 * Represents styled text with ANSI markup
 */
public static class Text {
    public Text(String plainText);
    public String toString();
    public int length();
}

/**
 * Interface for initializing help commands
 */
public interface IHelpCommandInitializable {
    void init(CommandLine helpCommandLine, Help.Ansi ansi, PrintStream out, PrintStream err);
}

/**
 * Extended interface for help command initialization
 */
public interface IHelpCommandInitializable2 extends IHelpCommandInitializable {
    void init(CommandLine helpCommandLine, Help.Ansi ansi, PrintStream out, PrintStream err);
}
```