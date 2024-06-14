package app.cbo.gitstats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This application reads a git log file and outputs some statistics about the commits
 *
 * This is a One file application to allow execution wihtout a compilationstep. Acutally I am not sure sure why this is still a Maven project.
 */
public class GitStatsApplication  {


    public static void main(String... args)  {
        try{
            new GitStatsApplication().instanceMain(args);
        }catch (Exception e){
            System.err.println(e.getMessage());
            e.printStackTrace(System.err);
            System.exit(5);
        }
    }

    /**
     * Main method of the application (but non staitc)
     * @param args path to the file containing the git log
     * @throws IOException if the file is not found
     */
    public void instanceMain(String... args) throws IOException {

        var path = parseArgs(args);

        var allCommits = readFile(path);

        //including creation
        analysis(allCommits, "ALL", Predicates.allCommits, Predicates.srcMain);

        //[CBO][10/06/2024] Too many false    positive/negative in isFixCommit
        //analysis(allCommits, "FIX", Predicates.isFixCommit, Predicates.srcMain);



        System.exit(0);
    }

    /**
     * @param allCommits list of all commits
     * @param title title of the analysis
     * @param commitFilter filter to apply on commits
     * @param fileFilter filter to apply on updated files
     */
    private static void analysis(List<Commit> allCommits , String title, Predicate<Commit> commitFilter, Predicate<String> fileFilter) throws IOException {

        //TITLE
        System.out.println();
        System.out.println("*".repeat(5+ title.trim().length() +5));
        System.out.println("*    "+title.trim()+"    *");
        System.out.println("*".repeat(5+ title.trim().length() +5));
        System.out.println();

        // Map<filePath, nb of commits on this file>
        var commitCountByFile = regroupCommits(allCommits, commitFilter);

        System.out.println("Number of files " + commitCountByFile.size());


        Path top50file = Path.of("./top50_"+ title + ".txt");
        System.out.printf("%n50 most touched files -> %s %n",top50file);
        try(var writer = Files.newBufferedWriter(top50file)) {
            commitCountByFile.entrySet()
                    .stream()
                    .filter(e -> fileFilter.test(e.getKey()))
                    .sorted(Comparator.comparing(e -> -1 * e.getValue().get()))
                    .filter(e -> e.getValue().get() > 4)
                    .limit(50)
                    .forEach(e -> {
                        try {
                            writer.write(String.format("  %s : %s%n", e.getKey(), e.getValue().get()));
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }


        Path csvFile = Path.of("./" + title + ".csv");
        System.out.printf("%nAs a csv file line -> %s %n", csvFile);
        try(var writer = Files.newBufferedWriter(csvFile)) {
            writer.write(commitCountByFile.entrySet()
                    .stream()
                    .filter(e -> fileFilter.test(e.getKey()))
                    .sorted(Comparator.comparing(e -> e.getValue().get()))
                    .map(s -> s.getValue().get() + ";")
                    .collect(Collectors.joining()));
            writer.newLine();
        }
    }



    /**
     * Regroup commits by file (all commits is a list of commits listing one or several files, result is a list of file with the number of currences found in the commit list)
     * @param allCommits list of all commits as returned by 'git log'
     * @param commitFilter filter to apply on commits (e.g. all or only 'fix' commits)
     * @return foreach filepath found in commit, the number of commits that involved it
     */
    private static HashMap<String, AtomicLong> regroupCommits(List<Commit> allCommits, Predicate<Commit> commitFilter) {
        var commitCountByFile = new HashMap<String, AtomicLong>();
        allCommits
                .stream()
                .filter(commitFilter)
                .forEach(
                        c -> c.files().forEach(
                                f -> commitCountByFile.computeIfAbsent(f, k -> new AtomicLong()).incrementAndGet()
                        )
                );
        return commitCountByFile;
    }

    /**
     * Read the file containing the git log, extract the commit data
     * @param path path to the file
     * @return list of commits
     * @throws IOException if the file is not found
     */
    private static List<Commit> readFile(Path path) throws IOException {
        List<Commit> allCommits = new ArrayList<>();
        try(var reader = Files.newBufferedReader(path)){
            String line;

            List<String> lineBuffer = new ArrayList<>();

            while( (line=reader.readLine())!=null){
                //read until start of next commit
                if(line.startsWith("commit ") && !lineBuffer.isEmpty()) {
                    allCommits.add(new Commit(lineBuffer));
                    lineBuffer.clear();
                }
                lineBuffer.add(line);
            }

            //push the last/current one in list
            allCommits.add(new Commit(lineBuffer));
            lineBuffer.clear();

            System.out.printf("Number of commits in file: %d%n", allCommits.size());
        }
        return allCommits;
    }

    private static Path parseArgs(String[] args) {


        if(args==null || args.length == 0){
            System.err.println("First argument MUST be a git log output file");
            System.exit(1);
        }

        String pathStr = args[0];

        System.out.println();
        System.out.printf("Reading commits from file: %s%n", pathStr);
        Path path = Path.of(pathStr);
        if (!Files.exists(path)) {
            System.err.printf("  -> File not found: %s%n", pathStr);
            System.exit(1);
        }else{
            System.out.printf("  -> File found: %s%n", pathStr);
        }
        return path;
    }

    /**
     * Predicates to filter commits and files
     */
    public static class Predicates {

        /**
         * Matches every commit
         */
        static Predicate<Commit> allCommits = (Commit commit) -> true;

        /**
         * Matches commits that contains "fix" or "bug", or ano in their message
         */
        static Predicate<Commit> isFixCommit = (Commit commit) -> commit.message().toLowerCase()
                .contains("fix")
                ||
                commit.message().toLowerCase()
                        .contains("bug") //will match "debug" too, false positive :)
                ||
                commit.message().toLowerCase()
                        .contains("ano") ||
                commit.message().toLowerCase()
                        .contains("orrectio");

        /**
         * Matches file paths that are not java test files
         */
        static Predicate<String> notAJavaTest = (String filePath) -> {

            var isTest = filePath.contains("src/test/java") && filePath.endsWith("java");
            return !isTest;
        };

        //TODO [a118608][05/06/2024] notATsTest/notAJsTest

        /**
         * matches java files
         */
        static Predicate<String>  isJava = (String filePath) -> filePath.endsWith("java");

        /**
         * matches typescript files
         */
        static Predicate<String>  isTs = (String filePath) -> filePath.endsWith("ts");

        /**
         * matches javascript files
         */
        static Predicate<String>  isJs = (String filePath) -> filePath.endsWith("js");

        /**
         * matches css files
         */
        static Predicate<String>  isCss = (String filePath) -> filePath.endsWith("css");
        /**
         * matches html files
         */
        static Predicate<String>  isHtml = (String filePath) -> filePath.endsWith("html");

        /**
         * Combines two predicates with an 'or' operator
         */
        @SafeVarargs
        static <U> Predicate<U> or(Predicate<U>... sub){
            return (U u) -> {

                for (Predicate<U> p : sub) {
                    if(p.test(u)){
                        return true;
                    }
                }
                return false;
            };
        }

        /**
         * Combines two predicates with an 'and' operator
         */
        @SafeVarargs
        static <U> Predicate<U> and(Predicate<U>... sub){
            return (U u) -> {

                for (Predicate<U> p : sub) {
                    if(!p.test(u)){
                        return false;
                    }
                }
                return true;
            };
        }

        /**
         * Compound predicate to match source file (java, ts, js, css, html)
         */
        static Predicate<String>  sourceFile = or(isJava, isTs, isJs, isCss, isHtml);
        /**
         * Compound predicate to match source file (java, ts, js, css, html) that are not test files
         */
        static Predicate<String>  srcMain = and(sourceFile, notAJavaTest /* TODO, notAFrontTest*/);

    }



    /**
     * A commit record
     */
    public record Commit(String hash, String username, String email, OffsetDateTime date, String message, List<String> files){

        //[a118608][05/06/2024] maybe add a boolean 'merge' ?
        public static final DateTimeFormatter GIT_DATE_FORMAT = DateTimeFormatter.ofPattern("E LLL d HH:mm:ss yyyy Z", Locale.US);

        /**
         * Constructor
         * @param commitLines lines of the commit (part of the git log output related to a single commit)
         */
        public Commit (List<String> commitLines) {

            this(
                    extractHash(commitLines),
                    extractUsername(commitLines).toUpperCase(Locale.ROOT),
                    extractEmail(commitLines),
                    extractDate(commitLines),
                    extractMsg(commitLines),
                    extractFiles(commitLines)
            );
        }

        /**
         * Extract the list of files from the commit lines
         * @param commitLines: all lines of the commit
         */
        private static List<String> extractFiles(List<String> commitLines) {
            boolean foundMsg = false;

            var files = new ArrayList<String>();
            for(var line : commitLines){
                if(line.startsWith("    ")){
                    foundMsg = true;
                }

                if(foundMsg && !line.startsWith("    ")){
                    files.add(line);
                }
            }
            return files.stream().filter(s->!s.isBlank()).toList();
        }

        /**
         * Extract the message from the commit lines
         * @param commitLines: all lines of the commit
         */
        private static String extractMsg(List<String> commitLines) {
            return commitLines.stream()
                    //[a118608][10/06/2024] somehow works...
                    .filter(l -> l.startsWith("    "))
                    .map(String::trim)
                    .collect(Collectors.joining("\r\n"));
        }

        /**
         * Extract the hash from the commit lines
         * @param commitLines: all lines of the commit
         */
        private static String extractHash(Collection<String> commitLines) {
            String token = "commit ";
            return commitLines.stream().filter(s-> s.startsWith(token)).findFirst().orElseThrow().substring(token.length());
        }

        /**
         * Extract the date from the commit lines
         * @param commitLines: all lines of the commit
         */
        private static OffsetDateTime extractDate(List<String> commitLines) {

            String line = commitLines.stream().filter(s-> s.startsWith("Date:")).findFirst().orElseThrow();
            String dtString = line.substring(line.indexOf(':') + 1).trim();

            try {
                //Locale.US => my git log output is in *US* english, meaning that sept is abbreviated as 'Sep'.
                //Default would be "Sept" and throw a DateFormatException
                return OffsetDateTime.parse(dtString, GIT_DATE_FORMAT);
            }catch(DateTimeParseException e){
                System.err.printf("could not parse : '%s'%n", dtString);
                System.err.printf("template :        '%s'%n", OffsetDateTime.now()
                        .minusYears(1L) //random date that curiously mathc my last error
                        .plusMonths(3)
                        .plusDays(4L).format(GIT_DATE_FORMAT));
                throw e;
            }

        }

        /**
         * Extract the email from the commit lines
         * @param commitLines: all lines of the commit
         */
        private static String extractEmail(List<String> commitLines) {

            String authorLine = commitLines.stream().filter(s-> s.startsWith("Author:")).findFirst().orElseThrow();
            String begin = authorLine.substring(authorLine.indexOf('<')+1).trim();
            return begin.substring(0, begin.indexOf('>')).trim();

        }

        /**
         * Extract the username from the commit lines
         * @param commitLine all lines of the commit
         */
        private static String extractUsername(List<String> commitLine) {

            String authorLine = commitLine.stream().filter(s-> s.startsWith("Author:")).findFirst().orElseThrow();
            String begin = authorLine.substring(authorLine.indexOf(':')+1).trim();
            return begin.substring(0, begin.indexOf('<')).trim();

        }

    }

}
