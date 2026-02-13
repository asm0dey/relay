# Picocli

Picocli is a comprehensive Java command line parsing library that provides both an annotations API and a programmatic API for creating rich CLI applications. It enables developers to create professional command-line tools with minimal boilerplate code, supporting advanced features like ANSI colored help messages, TAB autocompletion, nested subcommands, argument validation, and type conversion.

## Package Information

- **Package Name**: picocli
- **Package Type**: maven
- **Language**: Java
- **Installation**: Add to Maven: `<dependency><groupId>info.picocli</groupId><artifactId>picocli</artifactId><version>4.7.7</version></dependency>`
- **Gradle**: `implementation 'info.picocli:picocli:4.7.7'`

## Core Imports

```java
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
```

For autocompletion:

```java
import picocli.AutoComplete;
```

## Basic Usage

### Annotation-based API

```java
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "myapp", mixinStandardHelpOptions = true, version = "1.0",
         description = "Demonstrates picocli usage")
class MyApp implements Runnable {
    
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;
    
    @Option(names = {"-c", "--count"}, description = "Number of iterations", defaultValue = "1")
    int count;
    
    @Parameters(index = "0", description = "Input file")
    String inputFile;
    
    @Override
    public void run() {
        System.out.printf("Processing %s %d times%s%n", 
            inputFile, count, verbose ? " (verbose)" : "");
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MyApp()).execute(args);
        System.exit(exitCode);
    }
}
```

### Programmatic API

```java
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;

CommandSpec spec = CommandSpec.create()
    .name("myapp")
    .addOption(OptionSpec.builder("-v", "--verbose")
        .description("Verbose output")
        .type(boolean.class)
        .build())
    .addOption(OptionSpec.builder("-c", "--count")
        .description("Number of iterations")
        .type(int.class)
        .defaultValue("1")
        .build());

CommandLine cmd = new CommandLine(spec);
CommandLine.ParseResult parseResult = cmd.parseArgs(args);

if (parseResult.hasMatchedOption("-v")) {
    System.out.println("Verbose mode enabled");
}
```

## Architecture

Picocli is built around several key components:

- **CommandLine**: Central parsing and execution engine that coordinates all functionality
- **Annotation API**: Declarative approach using `@Command`, `@Option`, `@Parameters` annotations
- **Programmatic API**: Imperative approach using `CommandSpec`, `OptionSpec`, `PositionalParamSpec` classes
- **Type System**: Extensible type conversion system supporting built-in and custom converters
- **Help System**: Comprehensive help generation with ANSI styling and customizable sections
- **Execution Model**: Flexible execution strategies with built-in exception handling and exit code management
- **Autocompletion**: TAB completion script generation for bash and other shells

## Capabilities

### Annotations API

Declarative command definition using annotations for options, parameters, commands, and argument groups. The annotation approach provides the most concise way to define CLI interfaces.

```java { .api }
@Command(name = "mycommand", description = "Command description")
public class MyCommand implements Runnable {
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;
    
    @Parameters(index = "0", description = "Input file")
    String inputFile;
}
```

[Annotations](./annotations.md)

### CommandLine Core

Central command line parsing and execution functionality including argument parsing, subcommand handling, configuration options, and execution strategies.

```java { .api }
public class CommandLine {
    public CommandLine(Object command);
    public CommandLine(Object command, IFactory factory);
    
    public int execute(String... args);
    public ParseResult parseArgs(String... args);
    public CommandLine addSubcommand(String name, Object command);
}
```

[CommandLine Core](./command-line.md)

### Help System

Comprehensive help generation system with ANSI styling, customizable sections, and usage message formatting. Supports both automatic help generation and custom help rendering.

```java { .api }
public static class Help {
    public Help(CommandSpec commandSpec);
    public String fullSynopsis();
    public String detailedSynopsis(int synopsisHeadingLength, Comparator<OptionSpec> optionSort, boolean clusterOptions);
}

public static void usage(Object command, PrintStream out);
public static void usage(Object command, PrintStream out, Help.Ansi ansi);
```

[Help System](./help-system.md)

### Parsing and Execution

Parse result handling, execution strategies, and exception management for robust command line processing with detailed error reporting.

```java { .api }
public static class ParseResult {
    public boolean hasMatchedOption(String option);
    public <T> T matchedOptionValue(String option, T defaultValue);
    public List<String> matchedPositionalValue(int index, List<String> defaultValue);
}

public interface IExecutionStrategy {
    int execute(ParseResult parseResult) throws ExecutionException;
}
```

[Parsing and Execution](./parsing-execution.md)

### Type Conversion

Extensible type conversion system for converting string command line arguments to strongly typed Java objects, with built-in converters and custom converter support.

```java { .api }
public interface ITypeConverter<K> {
    K convert(String value) throws Exception;
}

public interface IDefaultValueProvider {
    String defaultValue(ArgSpec argSpec) throws Exception;
}
```

[Type Conversion](./type-conversion.md)

### Autocompletion

TAB completion script generation and completion candidate suggestion for creating shell autocompletion functionality.

```java { .api }
public class AutoComplete {
    public static void main(String... args);
    public static String bash(String scriptName, CommandLine commandLine);
    public static int complete(CommandSpec spec, String[] args, int argIndex, int positionInArg, int cursor, List<CharSequence> candidates);
}
```

[Autocompletion](./autocompletion.md)

## Common Types

```java { .api }
public static final class ExitCode {
    public static final int OK = 0;
    public static final int SOFTWARE = 1;
    public static final int USAGE = 2;
}

public enum TraceLevel { OFF, WARN, INFO, DEBUG }

public enum ScopeType { LOCAL, INHERIT }

public static class Help {
    public enum Ansi { AUTO, ON, OFF }
    
    public enum Visibility { ALWAYS, NEVER, ON_DEMAND }
    
    public static class ColorScheme {
        public ColorScheme(Ansi ansi);
        public Ansi ansi();
    }
}

public static class Range implements Comparable<Range> {
    public Range(int min, int max);
    public int min();
    public int max();
    public boolean contains(int value);
    public static Range valueOf(String range);
}
```