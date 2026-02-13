# Type Conversion

Extensible type conversion system for converting string command line arguments to strongly typed Java objects, with built-in converters and custom converter support for complex data types.

## Capabilities

### Type Converter Interface

Core interface for implementing custom type converters that transform string arguments into typed values.

```java { .api }
/**
 * Interface for converting string command line arguments to typed values
 * @param <K> the target type
 */
public interface ITypeConverter<K> {
    /**
     * Converts a string value to the target type
     * @param value the string value from command line
     * @return the converted typed value
     * @throws Exception if conversion fails
     */
    K convert(String value) throws Exception;
}

/**
 * Marker type converter indicating that the default converter should be used
 */
public static final class UseDefaultConverter implements ITypeConverter<Object> {
    public Object convert(String value) throws Exception;
}
```

**Usage Examples:**

```java
// Custom converter for parsing date strings
public class DateConverter implements ITypeConverter<LocalDate> {
    @Override
    public LocalDate convert(String value) throws Exception {
        return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}

// Using custom converter in option
@Option(names = "--start-date", 
        converter = DateConverter.class,
        description = "Start date in YYYY-MM-DD format")
LocalDate startDate;

// Custom converter for complex objects
public class PersonConverter implements ITypeConverter<Person> {
    @Override
    public Person convert(String value) throws Exception {
        String[] parts = value.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected format: name,age");
        }
        return new Person(parts[0], Integer.parseInt(parts[1]));
    }
}

@Option(names = "--person", 
        converter = PersonConverter.class,
        description = "Person in format: name,age")
Person person;
```

### Default Value Providers

Interface for providing default values for options and parameters, enabling dynamic defaults based on environment or configuration.

```java { .api }
/**
 * Provides default values for options and parameters
 */
public interface IDefaultValueProvider {
    /**
     * Gets the default value for the specified argument
     * @param argSpec the argument specification
     * @return default value string or null if no default
     * @throws Exception if default value calculation fails
     */
    String defaultValue(ArgSpec argSpec) throws Exception;
}

/**
 * Provides default values from properties files
 */
public static class PropertiesDefaultProvider implements IDefaultValueProvider {
    /**
     * Creates provider from properties file
     * @param propertiesFile the properties file
     */
    public PropertiesDefaultProvider(File propertiesFile);
    
    /**
     * Creates provider from properties
     * @param properties the properties object
     */
    public PropertiesDefaultProvider(Properties properties);
    
    @Override
    public String defaultValue(ArgSpec argSpec) throws Exception;
}
```

**Usage Examples:**

```java
// Environment-based default value provider
public class EnvironmentDefaultProvider implements IDefaultValueProvider {
    @Override
    public String defaultValue(ArgSpec argSpec) throws Exception {
        if (argSpec.isOption()) {
            OptionSpec option = (OptionSpec) argSpec;
            // Map option names to environment variables
            if (option.names().contains("--database-url")) {
                return System.getenv("DATABASE_URL");
            }
            if (option.names().contains("--api-key")) {
                return System.getenv("API_KEY");
            }
        }
        return null; // No default available
    }
}

// Using custom default provider
@Command(name = "app", defaultValueProvider = EnvironmentDefaultProvider.class)
class MyApp {
    @Option(names = "--database-url") String databaseUrl;
    @Option(names = "--api-key") String apiKey;
}

// Properties file default provider
Properties props = new Properties();
props.setProperty("output.directory", "/tmp/output");
props.setProperty("max.threads", "4");

CommandLine cmd = new CommandLine(new MyApp());
cmd.setDefaultValueProvider(new PropertiesDefaultProvider(props));
```

### Parameter Consumers

Advanced parameter processing interface for custom argument consumption patterns.

```java { .api }
/**
 * Consumes command line arguments for options
 */
public interface IParameterConsumer {
    /**
     * Consumes parameters for the specified option
     * @param stack the argument stack
     * @param argSpec the argument specification
     * @param commandSpec the command specification
     * @throws Exception if consumption fails
     */
    void consumeParameters(Stack<String> stack, ArgSpec argSpec, CommandSpec commandSpec) throws Exception;
}
```

**Usage Example:**

```java
// Custom parameter consumer for key=value pairs
public class KeyValueConsumer implements IParameterConsumer {
    @Override
    public void consumeParameters(Stack<String> stack, ArgSpec argSpec, CommandSpec commandSpec) throws Exception {
        Map<String, String> map = new HashMap<>();
        
        while (!stack.isEmpty() && !stack.peek().startsWith("-")) {
            String arg = stack.pop();
            String[] parts = arg.split("=", 2);
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            } else {
                throw new ParameterException(commandSpec.commandLine(), 
                    "Expected key=value format, got: " + arg);
            }
        }
        
        argSpec.setValue(map);
    }
}

@Option(names = "--properties",
        parameterConsumer = KeyValueConsumer.class,
        description = "Key-value pairs in format key1=value1 key2=value2")
Map<String, String> properties;

// Usage: --properties name=John age=30 city=Seattle
```

### Parameter Preprocessors

Interface for preprocessing command line arguments before parsing begins.

```java { .api }
/**
 * Preprocesses command line arguments before parsing
 */
public interface IParameterPreprocessor {
    /**
     * Preprocesses the command line arguments
     * @param args the original arguments
     * @param commandSpec the command specification
     * @return preprocessed arguments
     */
    String[] preprocess(String[] args, CommandSpec commandSpec);
}
```

**Usage Example:**

```java
// Preprocessor that expands configuration files
public class ConfigFilePreprocessor implements IParameterPreprocessor {
    @Override
    public String[] preprocess(String[] args, CommandSpec commandSpec) {
        List<String> expanded = new ArrayList<>();
        
        for (String arg : args) {
            if (arg.startsWith("@")) {
                // Expand configuration file
                String filename = arg.substring(1);
                try {
                    List<String> fileArgs = Files.readAllLines(Paths.get(filename));
                    expanded.addAll(fileArgs);
                } catch (IOException e) {
                    throw new ParameterException(commandSpec.commandLine(),
                        "Cannot read config file: " + filename);
                }
            } else {
                expanded.add(arg);
            }
        }
        
        return expanded.toArray(new String[0]);
    }
}

// Usage: myapp @config.txt
// Where config.txt contains one argument per line
```

### Negatable Option Transformers

Interface for transforming negatable boolean options with custom patterns.

```java { .api }
/**
 * Transforms negatable boolean options
 */
public interface INegatableOptionTransformer {
    /**
     * Creates a negated version of the option name
     * @param optionName the original option name
     * @param cmd the command line instance
     * @return negated option name or null if not negatable
     */
    String makeNegative(String optionName, CommandLine cmd);
    
    /**
     * Creates a synopsis for the negatable option
     * @param optionName the option name
     * @param cmd the command line instance
     * @return synopsis text
     */
    String makeSynopsis(String optionName, CommandLine cmd);
}

/**
 * Regex-based negatable option transformer
 */
public static class RegexTransformer implements INegatableOptionTransformer {
    /**
     * Creates transformer with custom regex patterns
     * @param negationPattern pattern for creating negated forms
     * @param synopsisPattern pattern for synopsis generation
     */
    public RegexTransformer(String negationPattern, String synopsisPattern);
    
    @Override
    public String makeNegative(String optionName, CommandLine cmd);
    
    @Override
    public String makeSynopsis(String optionName, CommandLine cmd);
}
```

**Usage Example:**

```java
// Custom negatable options
@Option(names = {"--enable-feature", "--disable-feature"}, 
        negatable = true,
        description = "Enable or disable the feature")
boolean featureEnabled;

// Custom transformer for different negation pattern
RegexTransformer transformer = new RegexTransformer(
    "^--enable-(.*)$", "--disable-$1",  // Transform --enable-X to --disable-X
    "--[enable|disable]-$1"             // Synopsis pattern
);

CommandLine cmd = new CommandLine(new MyApp());
cmd.setNegatableOptionTransformer(transformer);

// Usage: --enable-feature or --disable-feature
```

### Built-in Type Support

Picocli provides built-in type converters for common Java types without requiring custom converters.

```java { .api }
// Built-in types supported automatically:
// - All primitive types and their wrapper classes
// - String, StringBuilder, CharSequence
// - File, Path (java.nio.file.Path)
// - URL, URI
// - Pattern (java.util.regex.Pattern)
// - Enum types
// - Date and time types (java.time.* and java.util.Date)
// - Network types (InetAddress)
// - Arrays and Collections of supported types
// - BigInteger, BigDecimal
// - Currency, Locale, TimeZone, Charset
```

**Usage Examples:**

```java
@Command(name = "examples")
class TypeExamples {
    // Primitive types
    @Option(names = "--count") int count;
    @Option(names = "--ratio") double ratio;
    @Option(names = "--enabled") boolean enabled;
    
    // File system
    @Option(names = "--file") File file;
    @Option(names = "--path") Path path;
    
    // Network
    @Option(names = "--url") URL url;
    @Option(names = "--uri") URI uri;
    @Option(names = "--host") InetAddress host;
    
    // Time
    @Option(names = "--date") LocalDate date;
    @Option(names = "--time") LocalTime time;
    @Option(names = "--instant") Instant instant;
    
    // Collections
    @Option(names = "--items") List<String> items;
    @Option(names = "--numbers") int[] numbers;
    
    // Enums
    @Option(names = "--level") LogLevel level;
    
    // Pattern matching
    @Option(names = "--pattern") Pattern pattern;
    
    // Locale and charset
    @Option(names = "--locale") Locale locale;
    @Option(names = "--charset") Charset charset;
}

enum LogLevel { DEBUG, INFO, WARN, ERROR }
```

### Range Type

Special type for representing arity ranges in option and parameter specifications.

```java { .api }
/**
 * Represents an arity range for options and parameters
 */
public static class Range implements Comparable<Range> {
    /**
     * Creates a range with specified min and max values
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive)
     */
    public Range(int min, int max);
    
    /**
     * Gets the minimum value
     */
    public int min();
    
    /**
     * Gets the maximum value
     */
    public int max();
    
    /**
     * Creates a range from string representation
     * @param range string like "1..3", "2..*", "1"
     */
    public static Range valueOf(String range);
    
    /**
     * Checks if a value is within this range
     */
    public boolean contains(int value);
}
```

**Usage Example:**

```java
// Using Range for custom arity validation
@Option(names = "--files", 
        arity = "1..5",  // Accept 1 to 5 files
        description = "Input files (1-5 files)")
List<File> inputFiles;

@Parameters(arity = "2..*",  // At least 2 parameters
           description = "At least 2 output files required")
String[] outputFiles;

// Programmatic range usage
Range range = Range.valueOf("1..3");
if (range.contains(fileList.size())) {
    System.out.println("Valid number of files");
}
```

### Additional Interfaces

Extended interfaces for advanced parameter processing and customization.

```java { .api }
/**
 * Interface for consuming parameters from the command line arguments stack
 */
public interface IParameterConsumer {
    /**
     * Consumes parameters from the arguments stack
     * @param args the arguments stack
     * @param argSpec the argument specification
     * @param commandSpec the command specification
     */
    void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec);
}

/**
 * Interface for preprocessing parameters before consumption
 */
public interface IParameterPreprocessor {
    /**
     * Preprocesses a parameter before it is consumed
     * @param args the arguments stack
     * @param commandSpec the command specification  
     * @param argSpec the argument specification
     * @param info additional preprocessing information
     * @return true if preprocessing consumed the parameter, false otherwise
     */
    boolean preprocess(Stack<String> args, CommandSpec commandSpec, ArgSpec argSpec, Map<String, Object> info);
}

/**
 * Interface for transforming negatable option names
 */
public interface INegatableOptionTransformer {
    /**
     * Creates the negative form of an option name
     * @param optionName the original option name
     * @param cmd the command specification
     * @return the negative option name
     */
    String makeNegative(String optionName, CommandSpec cmd);
    
    /**
     * Creates the synopsis representation for negatable options
     * @param optionName the original option name
     * @param cmd the command specification
     * @return the synopsis representation
     */
    String makeSynopsis(String optionName, CommandSpec cmd);
}
```