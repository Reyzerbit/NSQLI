package com.reyzerbit.suiteqlcli.command;

import com.reyzerbit.suiteqlcli.ProfileRepository;
import com.reyzerbit.suiteqlcli.ResultPrinter;
import com.reyzerbit.suiteqlcli.SuiteQlClient;
import com.reyzerbit.suiteqlcli.model.Profile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Open a SuiteQL console for a profile, or run a single query if one is given.")
public class RunCommand implements Callable<Integer>
{
    private static final Set<String> FORMATS = Set.of("table", "json", "csv");

    @Parameters(index = "0", arity = "0..1", paramLabel = "<query>", description = "Run this query and exit instead of opening the console.")
    String query;

    @Option(names = {"-p", "--profile"}, paramLabel = "<profile-id>", description = "Profile to connect with. Optional when only one profile exists.")
    String profileId;

    @Option(names = {"-f", "--file"}, description = "Run the query in this file and exit.")
    Path file;

    @Option(names = {"-o", "--format"}, defaultValue = "table", description = "table, json or csv (default: ${DEFAULT-VALUE}).")
    String format;

    @Option(names = {"-l", "--limit"}, defaultValue = "0", description = "Maximum rows to return (default: all, paged 1000 at a time).")
    int limit;

    @Override
    public Integer call() throws Exception
    {
        if (!FORMATS.contains(format.toLowerCase()))
        {
            System.err.println("Unknown format '" + format + "' (expected table, json or csv).");
            return 1;
        }

        Profile profile = resolveProfile();
        if (profile == null) return 1;

        SuiteQlClient client = new SuiteQlClient(profile);

        if (file != null) return execute(client, Files.readString(file).replace("﻿", "")) ? 0 : 1;
        if (query != null) return execute(client, query) ? 0 : 1;

        return console(client, profile);
    }

    private Profile resolveProfile() throws Exception
    {
        List<Profile> profiles = new ProfileRepository().list();

        if (profileId != null)
        {
            Profile found = profiles.stream().filter(p -> p.id().equals(profileId)).findFirst().orElse(null);
            if (found == null) System.err.println("No profile named '" + profileId + "'.");
            return found;
        }

        if (profiles.size() == 1) return profiles.get(0);

        if (profiles.isEmpty()) System.err.println("No profiles. Create one with: nsqli profile new");
        else System.err.println("Specify a profile with -p. Available: " + String.join(", ", profiles.stream().map(Profile::id).toList()));

        return null;
    }

    private boolean execute(SuiteQlClient client, String sql)
    {
        // SuiteQL rejects a trailing semicolon.
        sql = sql.strip();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).stripTrailing();
        if (sql.isBlank()) return true;

        try
        {
            List<JsonNode> rows = client.run(sql, limit);
            ResultPrinter.print(rows, format);
            return true;
        }
        catch (Exception e)
        {
            System.err.println("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return false;
        }
    }

    // mysql-style console: statements end with ';' and may span lines. Also reads piped scripts (nsqli run -p x < queries.sql).
    private int console(SuiteQlClient client, Profile profile)
    {
        boolean interactive = Prompts.interactive();
        boolean failed = false;
        StringBuilder buffer = new StringBuilder();

        if (interactive)
        {
            System.out.println("Connected to " + profile.id() + " (" + profile.accountId() + ").");
            System.out.println("End statements with ';'. Type 'help' for commands, 'exit' to quit.");
        }

        while (true)
        {
            String prompt = !interactive ? null : buffer.isEmpty() ? "nsqli [" + profile.id() + "]> " : "    -> ";
            String line = Prompts.readLine(prompt, false);

            if (line == null)
            {
                // End of input: run any trailing statement that was missing its ';'.
                if (!execute(client, buffer.toString())) failed = true;
                if (interactive) System.out.println();
                break;
            }

            String trimmed = line.trim();

            if (buffer.isEmpty())
            {
                if (trimmed.isEmpty()) continue;

                String command = trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
                if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit") || command.equals("\\q")) break;

                if (command.equalsIgnoreCase("help") || command.equals("\\h") || command.equals("\\?"))
                {
                    printHelp();
                    continue;
                }

                if (command.startsWith("\\"))
                {
                    metaCommand(command);
                    continue;
                }
            }

            buffer.append(line).append('\n');

            if (trimmed.endsWith(";"))
            {
                String sql = buffer.toString();
                buffer.setLength(0);

                if (!execute(client, sql))
                {
                    failed = true;

                    // Like mysql, a script stops at the first error; the interactive console keeps going.
                    if (!interactive) break;
                }
            }
        }

        return failed ? 1 : 0;
    }

    private void metaCommand(String command)
    {
        String[] parts = command.split("\\s+", 2);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (parts[0])
        {
            case "\\format" ->
            {
                if (FORMATS.contains(arg.toLowerCase()))
                {
                    format = arg.toLowerCase();
                    System.out.println("Output format: " + format);
                }
                else System.err.println("Usage: \\format table|json|csv");
            }
            case "\\limit" ->
            {
                try
                {
                    limit = Math.max(0, Integer.parseInt(arg));
                    System.out.println(limit == 0 ? "Row limit: none" : "Row limit: " + limit);
                }
                catch (NumberFormatException e)
                {
                    System.err.println("Usage: \\limit <rows>   (0 = no limit)");
                }
            }
            default -> System.err.println("Unknown command '" + parts[0] + "'. Type 'help'.");
        }
    }

    private void printHelp()
    {
        System.out.println("  <SuiteQL>;          Run a statement (may span several lines)");
        System.out.println("  \\format <fmt>       Output format: table, json or csv (now: " + format + ")");
        System.out.println("  \\limit <rows>       Max rows returned, 0 for all (now: " + limit + ")");
        System.out.println("  help, \\h            Show this help");
        System.out.println("  exit, quit, \\q      Leave the console (or press Ctrl+Z then Enter / Ctrl+D)");
    }
}
