# Annotations API

Declarative command definition using annotations for options, parameters, commands, and argument groups. The annotation approach provides the most concise way to define CLI interfaces with minimal boilerplate code.

## Capabilities

### @Command Annotation

Marks a class or method as a command with metadata like name, description, version, and subcommands.

```java { .api }
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    String name() default "";
    String[] aliases() default {};
    String description() default "";
    String[] header() default {};
    String[] footer() default {};
    boolean abbreviateSynopsis() default false;
    String customSynopsis() default "";
    boolean hidden() default false;
    String version() default "";
    boolean mixinStandardHelpOptions() default false;
    Class<?>[] subcommands() default {};
    String separator() default " ";
    String synopsisSubcommandLabel() default "COMMAND";
    Class<? extends IVersionProvider> versionProvider() default IVersionProvider.class;
    boolean sortOptions() default true;
    boolean showDefaultValues() default false;
    Class<? extends IDefaultValueProvider> defaultValueProvider() default IDefaultValueProvider.class;
    Class<? extends IHelpFactory> helpFactory() default IHelpFactory.class;
    String parameterListHeading() default "";
    String optionListHeading() default "";
    String commandListHeading() default "";
    String headerHeading() default "";
    String synopsisHeading() default "";
    String descriptionHeading() default "";
    String footerHeading() default "";
    ScopeType scope() default ScopeType.INHERIT;
}
```

**Usage Example:**

```java
@Command(name = "git", 
         description = "The Git version control system",
         version = "Git 2.30.0",
         mixinStandardHelpOptions = true,
         subcommands = {GitAdd.class, GitCommit.class, GitPush.class})
public class Git implements Runnable {
    @Override
    public void run() {
        System.out.println("Git - version control system");
    }
}
```

### @Option Annotation

Marks a field or method parameter as a command line option with names, description, and various configuration attributes.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Option {
    String[] names();
    boolean required() default false;
    boolean help() default false;
    boolean usageHelp() default false;
    boolean versionHelp() default false;
    boolean interactive() default false;
    boolean echo() default true;
    String prompt() default "";
    String description() default "";
    String descriptionKey() default "";
    String paramLabel() default "";
    boolean hideParamSyntax() default false;
    boolean hidden() default false;
    Class<?>[] type() default {};
    String defaultValue() default NULL_VALUE;
    String fallbackValue() default NULL_VALUE;
    String mapFallbackValue() default NULL_VALUE;
    Help.Visibility showDefaultValue() default Help.Visibility.ON_DEMAND;
    Class<? extends ITypeConverter<?>>[] converter() default {};
    Class<? extends Iterable<String>> completionCandidates() default Iterable.class;
    String split() default "";
    String splitSynopsisLabel() default "";
    boolean negatable() default false;
    Class<? extends IParameterConsumer> parameterConsumer() default IParameterConsumer.class;
    Class<? extends IParameterPreprocessor> preprocessor() default IParameterPreprocessor.class;
    ScopeType scope() default ScopeType.INHERIT;
    int order() default -1;
    String arity() default "";
    Class<? extends IDefaultValueProvider> defaultValueProvider() default IDefaultValueProvider.class;
    
    public static final String NULL_VALUE = "__no_default_value__";
}
```

**Usage Examples:**

```java
@Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
boolean verbose;

@Option(names = {"-f", "--file"}, 
        description = "Input file", 
        required = true,
        paramLabel = "FILE")
File inputFile;

@Option(names = {"-n", "--count"}, 
        description = "Number of iterations (default: ${DEFAULT-VALUE})",
        defaultValue = "1",
        showDefaultValue = true)
int count;

@Option(names = {"-c", "--config"}, 
        arity = "0..1", 
        fallbackValue = "default.conf",
        description = "Configuration file (default: ${FALLBACK-VALUE})")
String configFile;
```

### @Parameters Annotation

Marks a field or method parameter as positional parameters with index, description, and arity specification.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Parameters {
    String index() default "";
    String arity() default "";
    String paramLabel() default "";
    boolean hideParamSyntax() default false;
    boolean hidden() default false;
    Class<?>[] type() default {};
    Class<? extends ITypeConverter<?>>[] converter() default {};
    String description() default "";
    String descriptionKey() default "";
    String defaultValue() default NULL_VALUE;
    String mapFallbackValue() default NULL_VALUE;
    Help.Visibility showDefaultValue() default Help.Visibility.ON_DEMAND;
    Class<? extends Iterable<String>> completionCandidates() default Iterable.class;
    boolean interactive() default false;
    boolean echo() default true;
    String prompt() default "";
    String split() default "";
    String splitSynopsisLabel() default "";
    Class<? extends IParameterConsumer> parameterConsumer() default IParameterConsumer.class;
    Class<? extends IParameterPreprocessor> preprocessor() default IParameterPreprocessor.class;
    ScopeType scope() default ScopeType.INHERIT;
    int order() default -1;
    Class<? extends IDefaultValueProvider> defaultValueProvider() default IDefaultValueProvider.class;
    
    public static final String NULL_VALUE = "__no_default_value__";
}
```

**Usage Examples:**

```java
@Parameters(index = "0", description = "Input file to process")
String inputFile;

@Parameters(index = "1..*", 
           description = "Output files",
           arity = "1..*")
List<String> outputFiles;

@Parameters(description = "Files to process", 
           arity = "0..*")
String[] files;

@Parameters(index = "0", 
           description = "Command to execute",
           completionCandidates = "start,stop,restart,status")
String command;
```

### @ParentCommand Annotation

Injects a reference to the parent command object into a field or parameter of a subcommand.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ParentCommand { }
```

**Usage Example:**

```java
@Command(name = "add")
public class GitAdd implements Runnable {
    @ParentCommand
    Git parent;
    
    @Override
    public void run() {
        // Access parent command configuration
        if (parent.verbose) {
            System.out.println("Verbose mode inherited from parent");
        }
    }
}
```

### @Unmatched Annotation

Collects unmatched command line arguments into a field or parameter.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Unmatched { }
```

**Usage Example:**

```java
@Command(name = "wrapper")
public class CommandWrapper implements Runnable {
    @Unmatched
    List<String> unmatchedArgs;
    
    @Override
    public void run() {
        // Pass unmatched arguments to another process
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().addAll(unmatchedArgs);
        // ... execute process
    }
}
```

### @Mixin Annotation

Includes options and parameters from another class, enabling composition and reuse of common option sets.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mixin {
    String name() default "";
}
```

**Usage Example:**

```java
public class VerbosityMixin {
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;
    
    @Option(names = {"-q", "--quiet"}, description = "Suppress output")
    boolean quiet;
}

@Command(name = "mycommand")
public class MyCommand implements Runnable {
    @Mixin
    VerbosityMixin verbosity;
    
    @Override
    public void run() {
        if (verbosity.verbose) {
            System.out.println("Verbose mode enabled");
        }
    }
}
```

### @Spec Annotation

Injects the CommandSpec model object into a field or parameter, providing access to the programmatic API.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Spec { }
```

**Usage Example:**

```java
@Command(name = "dynamic")
public class DynamicCommand implements Runnable {
    @Spec
    CommandSpec spec;
    
    @Override
    public void run() {
        // Access command metadata programmatically
        System.out.printf("Command name: %s%n", spec.name());
        System.out.printf("Number of options: %d%n", spec.options().size());
    }
}
```

### @ArgGroup Annotation

Groups related arguments with validation rules for mutually exclusive or dependent options.

```java { .api }
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ArgGroup {
    boolean exclusive() default true;
    String multiplicity() default "";
    int order() default -1;
    String heading() default "";
    boolean validate() default true;
}
```

**Usage Examples:**

```java
// Mutually exclusive options
static class ExclusiveGroup {
    @Option(names = "-a") boolean a;
    @Option(names = "-b") boolean b;
    @Option(names = "-c") boolean c;
}

@Command(name = "app")
public class App {
    @ArgGroup(exclusive = true, multiplicity = "1")
    ExclusiveGroup group;
}

// Dependent options (all or none)
static class DependentGroup {
    @Option(names = "--user", required = true) String user;
    @Option(names = "--password", required = true) String password;
}

@Command(name = "login")
public class LoginCommand {
    @ArgGroup(exclusive = false, multiplicity = "0..1")
    DependentGroup credentials;
}
```

## Supporting Types

```java { .api }
public enum ScopeType {
    /** Inherit parent command's configuration */
    INHERIT,
    /** Use local configuration only */
    LOCAL
}
```