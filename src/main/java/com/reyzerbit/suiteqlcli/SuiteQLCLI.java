package com.reyzerbit.suiteqlcli;

import com.reyzerbit.suiteqlcli.command.ProfileCommand;
import com.reyzerbit.suiteqlcli.command.RunCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "nsqli", mixinStandardHelpOptions = true, version = "nsqli 1.0", description = "Run SuiteQL against NetSuite using stored, encrypted profiles.", subcommands = {ProfileCommand.class, RunCommand.class})
public class SuiteQLCLI
{
    public static void main(String[] args)
    {
        CommandLine cli = new CommandLine(new SuiteQLCLI());

        cli.setExecutionExceptionHandler((e, commandLine, parseResult) ->
        {
            commandLine.getErr().println("Error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return 1;
        });

        System.exit(cli.execute(args));
    }
}
