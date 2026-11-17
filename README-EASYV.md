Setting up and running DevForge
1. Install the prerequisites

You need JDK 21 or newer and Apache Maven 3.9+.

OS	Install
Windows	winget install EclipseAdoptium.Temurin.21.JDK and winget install Apache.Maven (or download from adoptium.net and maven.apache.org)
macOS	brew install openjdk@21 maven
Ubuntu/Debian	sudo apt install openjdk-21-jdk maven

Open a new terminal afterwards and check:

bash
java -version     # should say 21 or higher
mvn -version      # should say 3.9 or higher, and "Java version: 21..."

If mvn -version shows an older Java, set JAVA_HOME to your JDK 21 folder.

2. Unzip and build
bash
unzip devforge.zip        # Windows: right-click > Extract All
cd devforge
mvn clean verify

The first build downloads dependencies, so it needs internet and takes a few minutes. It compiles everything, runs all the tests, and creates the runnable jar at target/devforge-0.1.0-SNAPSHOT.jar.

If the build fails, paste me the output. This code has never been run against the real libraries, so a first-run failure is plausible, and the error text is what I need to fix it. To get the jar while sorting out test failures, use mvn clean package -DskipTests.

3. Run it
bash
java -jar target/devforge-0.1.0-SNAPSHOT.jar --help
java -jar target/devforge-0.1.0-SNAPSHOT.jar status

Typing the long command gets tedious, so add a shortcut:

Linux/macOS: alias devforge='java -jar /full/path/to/devforge/target/devforge-0.1.0-SNAPSHOT.jar' (put it in ~/.bashrc or ~/.zshrc to keep it)
PowerShell: function devforge { java -jar C:\full\path\to\devforge\target\devforge-0.1.0-SNAPSHOT.jar @args } (put it in your $PROFILE to keep it)

The examples below assume that shortcut.

4. Try it safely first

Point DevForge at a throwaway data folder, so your real data isn’t touched while you experiment:

bash
# Linux/macOS
export DEVFORGE_HOME=/tmp/df-try
# PowerShell
$env:DEVFORGE_HOME = "$env:TEMP\df-try"

Or run the ready-made demo (Linux/macOS, or Git Bash on Windows). It exercises every module with temporary data:

bash
scripts/demo.sh
5. Use it on a real project
bash
cd path/to/your/project

devforge init .                                   # register the project
devforge task add "Build authentication" --priority high --due 2026-12-31
devforge task list
devforge task stats

devforge search index .                           # build the search index
devforge search "authentication"
devforge search duplicates

devforge security scan .                          # needs internet
devforge security scan . --offline                # no network, cached data only
devforge security report

devforge env profile create dev --include '~/.gitconfig' --include '~/.bashrc'
devforge env backup
devforge env verify
devforge env restore --dry-run                    # preview only, writes nothing

devforge analyze .                                # does index + scan + full report

Add --help to any command to see its options, and --json for machine-readable output.

6. Good to know
Where your data goes: the database is at ~/.local/share/devforge/ on Linux, ~/Library/Application Support/DevForge/ on macOS, and %LOCALAPPDATA%\DevForge\ on Windows. Backups are stored in a backups folder there.
Exit codes: 0 is success and 1 is a failure. 3 means vulnerabilities were found, and 4 means the result is incomplete (for example, the scan couldn’t reach the advisory service). A code of 4 is never a clean result.
Using an IDE: open the devforge folder in IntelliJ IDEA, VS Code (with the Java extension pack), or Eclipse as a Maven project. The entry point is com.devforge.Main.
A real installer (optional): scripts/package.sh deb|dmg on Linux/macOS, or powershell -File scripts\package.ps1 -Type exe on Windows. These build an installer that bundles its own Java. Run them on the target OS (the Windows installer also needs the WiX Toolset). They are untested.