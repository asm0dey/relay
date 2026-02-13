# Autocompletion

TAB completion script generation and completion candidate suggestion for creating shell autocompletion functionality that enhances user experience with intelligent command line completion.

## Capabilities

### AutoComplete Class

Main class for generating shell completion scripts and providing completion suggestions.

```java { .api }
public class AutoComplete {
    // Exit codes for the AutoComplete main method
    public static final int EXIT_CODE_SUCCESS = 0;
    public static final int EXIT_CODE_INVALID_INPUT = 1;
    public static final int EXIT_CODE_COMMAND_SCRIPT_EXISTS = 2;
    public static final int EXIT_CODE_COMPLETION_SCRIPT_EXISTS = 3;
    public static final int EXIT_CODE_EXECUTION_ERROR = 4;
    
    /**
     * Main entry point for autocompletion script generation
     * Usage: java -cp myapp.jar picocli.AutoComplete [-f] [-o completionFile] [--] commandLineClass
     * @param args command line arguments for script generation
     */
    public static void main(String... args);
    
    /**
     * Generates bash completion script and writes to file
     * @param scriptName name of the script/command
     * @param out output file for the completion script
     * @param command file containing the command class
     * @param commandLine CommandLine instance for the command
     * @throws IOException if file operations fail
     */
    public static void bash(String scriptName, File out, File command, CommandLine commandLine) throws IOException;
    
    /**
     * Generates bash completion script as string
     * @param scriptName name of the script/command
     * @param commandLine CommandLine instance for the command
     * @return bash completion script as string
     */
    public static String bash(String scriptName, CommandLine commandLine);
    
    /**
     * Provides completion candidates for interactive completion
     * @param spec command specification
     * @param args current command line arguments
     * @param argIndex index of argument being completed
     * @param positionInArg cursor position within the argument
     * @param cursor absolute cursor position
     * @param candidates list to populate with completion candidates
     * @return number of completion candidates found
     */
    public static int complete(CommandSpec spec, String[] args, int argIndex, int positionInArg, int cursor, List<CharSequence> candidates);
}
```

**Usage Examples:**

```java
// Generate completion script for a command
@Command(name = "myapp", subcommands = {SubCmd1.class, SubCmd2.class})
class MyApp implements Runnable {
    @Option(names = {"-v", "--verbose"}) boolean verbose;
    @Parameters String[] files;
    
    public void run() { /* implementation */ }
}

// Generate bash completion script
CommandLine cmd = new CommandLine(new MyApp());
String completionScript = AutoComplete.bash("myapp", cmd);

// Write to file
try (FileWriter writer = new FileWriter("myapp_completion.bash")) {
    writer.write(completionScript);
}

// Or use the file-based method
AutoComplete.bash("myapp", 
                 new File("myapp_completion.bash"), 
                 new File("myapp.jar"), 
                 cmd);
```

### Command Line Generation

Using the AutoComplete main method to generate completion scripts from command line.

**Command Line Usage:**

```bash
# Generate completion script
java -cp myapp.jar picocli.AutoComplete com.example.MyApp

# Generate to specific file
java -cp myapp.jar picocli.AutoComplete -o myapp_completion.bash com.example.MyApp

# Force overwrite if file exists
java -cp myapp.jar picocli.AutoComplete -f -o myapp_completion.bash com.example.MyApp

# Install the generated completion (example for bash)
source myapp_completion.bash
# or
sudo cp myapp_completion.bash /etc/bash_completion.d/
```

### Interactive Completion

Programmatic completion for building custom completion systems or integrating with shells.

```java { .api }
/**
 * Completion method for interactive use
 * @param spec the command specification to complete against
 * @param args the current command line arguments
 * @param argIndex index of the argument being completed (0-based)
 * @param positionInArg position within the current argument being completed
 * @param cursor absolute cursor position in the command line
 * @param candidates list to be populated with completion candidates
 * @return number of candidates found
 */
public static int complete(CommandSpec spec, String[] args, int argIndex, int positionInArg, int cursor, List<CharSequence> candidates);
```

**Usage Examples:**

```java
@Command(name = "app")
class MyApp {
    @Option(names = {"-f", "--file"}) File file;
    @Option(names = {"-t", "--type"}, 
            completionCandidates = "text,binary,xml,json") 
    String type;
    @Parameters String[] inputs;
}

CommandLine cmd = new CommandLine(new MyApp());
CommandSpec spec = cmd.getCommandSpec();

// Complete for: "app -t "
String[] args = {"app", "-t", ""};
List<CharSequence> candidates = new ArrayList<>();
int count = AutoComplete.complete(spec, args, 2, 0, 7, candidates);

// candidates now contains: [text, binary, xml, json]
System.out.println("Completion candidates: " + candidates);

// Complete for: "app --f"
args = new String[]{"app", "--f"};
candidates.clear();
count = AutoComplete.complete(spec, args, 1, 3, 6, candidates);

// candidates now contains: [--file]
System.out.println("Option completions: " + candidates);
```

### Completion Candidates Configuration

Configuring completion candidates for options and parameters through annotations.

```java { .api }
// In @Option annotation:
String completionCandidates() default "";

// In @Parameters annotation:
String completionCandidates() default "";
```

**Usage Examples:**

```java
@Command(name = "processor")
class DataProcessor {
    // Static completion candidates
    @Option(names = {"-f", "--format"},
            completionCandidates = "json,xml,csv,yaml",
            description = "Output format")
    String format;
    
    // File path completion (automatic)
    @Option(names = {"-i", "--input"},
            description = "Input file")
    File inputFile;
    
    // Enum completion (automatic)
    @Option(names = {"-l", "--level"},
            description = "Log level")
    LogLevel logLevel;
    
    // Directory completion
    @Option(names = {"-o", "--output-dir"},
            description = "Output directory")
    File outputDir;
    
    // Custom completion candidates class
    @Option(names = {"-e", "--encoding"},
            completionCandidates = "EncodingCandidates",
            description = "Character encoding")
    String encoding;
}

enum LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

// Custom completion candidates provider
class EncodingCandidates extends ArrayList<String> {
    EncodingCandidates() {
        addAll(Arrays.asList("UTF-8", "UTF-16", "ASCII", "ISO-8859-1"));
    }
}
```

### Advanced Completion Features

Advanced completion features for complex command structures and dynamic candidates.

**Subcommand Completion:**

```java
@Command(name = "git", subcommands = {GitAdd.class, GitCommit.class, GitPush.class})
class Git {
    // Subcommands are automatically completed
}

@Command(name = "add")
class GitAdd {
    @Parameters(description = "Files to add") File[] files;
}

// Completion works for: git <TAB> -> [add, commit, push]
// And for: git add <TAB> -> [file completions]
```

**Dynamic Completion with Custom Logic:**

```java
// Custom completion through ICompletionCandidates interface
public class DatabaseTableCandidates implements Iterable<String> {
    @Override
    public Iterator<String> iterator() {
        // Connect to database and get table names
        List<String> tables = getDatabaseTables();
        return tables.iterator();
    }
    
    private List<String> getDatabaseTables() {
        // Implementation to fetch table names from database
        return Arrays.asList("users", "orders", "products", "categories");
    }
}

@Option(names = "--table",
        completionCandidates = "DatabaseTableCandidates",
        description = "Database table name")
String tableName;
```

### Installation and Integration

Setting up completion scripts in different shell environments.

**Bash Installation:**

```bash
# Generate the completion script
java -cp myapp.jar picocli.AutoComplete com.example.MyApp > myapp_completion.bash

# Install globally
sudo cp myapp_completion.bash /etc/bash_completion.d/

# Or install for current user
mkdir -p ~/.bash_completion.d
cp myapp_completion.bash ~/.bash_completion.d/
echo "source ~/.bash_completion.d/myapp_completion.bash" >> ~/.bashrc

# Test completion
source ~/.bashrc
myapp <TAB><TAB>
```

**Integration with Build Tools:**

```xml
<!-- Maven plugin configuration -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>generate-completion</id>
            <phase>package</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>picocli.AutoComplete</mainClass>
                <commandlineArgs>-o ${project.build.directory}/myapp_completion.bash com.example.MyApp</commandlineArgs>
            </configuration>
        </execution>
    </executions>
</plugin>
```

```gradle
// Gradle task for completion generation
task generateCompletion(type: JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    main = 'picocli.AutoComplete'
    args = ['-o', "$buildDir/myapp_completion.bash", 'com.example.MyApp']
}

build.dependsOn generateCompletion
```

### Completion Script Customization

The generated bash completion scripts can be customized for specific needs.

**Generated Script Structure:**

```bash
# Example generated completion script structure
_myapp() {
    local cur prev opts
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"
    
    # Command completion logic generated by picocli
    # Handles options, subcommands, and file completion
    
    # Custom completion logic can be added here
}

complete -F _myapp myapp
```

**Custom Enhancement Example:**

```bash
# Enhanced completion with additional logic
_myapp_enhanced() {
    local cur prev opts
    COMPREPLY=()
    cur="${COMP_WORDS[COMP_CWORD]}"
    prev="${COMP_WORDS[COMP_CWORD-1]}"
    
    # Generated picocli completion
    _myapp
    
    # Add custom hostname completion for --host option
    if [[ ${prev} == "--host" ]]; then
        COMPREPLY=( $(compgen -W "$(cat ~/.ssh/known_hosts | cut -d' ' -f1)" -- ${cur}) )
        return 0
    fi
}

complete -F _myapp_enhanced myapp
```