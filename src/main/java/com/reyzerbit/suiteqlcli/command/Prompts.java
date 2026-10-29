package com.reyzerbit.suiteqlcli.command;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

final class Prompts
{
    private static BufferedReader fallback;

    private Prompts() {}

    static boolean interactive()
    {
        return System.console() != null;
    }

    // Returns the supplied value, or prompts for it. Secrets are read without echo when a real console is attached.
    static String valueOrPrompt(String supplied, String label, String defaultValue, boolean secret)
    {
        if (supplied != null && !supplied.isBlank()) return supplied.trim();

        while (true)
        {
            String prompt = label + (defaultValue != null ? " [" + defaultValue + "]" : "") + ": ";
            String input = readLine(prompt, secret);

            if (input == null)
            {
                if (defaultValue != null) return defaultValue;
                throw new IllegalStateException("No input available for '" + label + "'.");
            }

            input = input.trim();
            if (input.isEmpty()) input = defaultValue;
            if (input != null && !input.isEmpty()) return input;
        }
    }

    // Returns null at end of input. A null prompt reads silently (piped scripts).
    static String readLine(String prompt, boolean secret)
    {
        Console console = System.console();

        if (console != null)
        {
            if (secret)
            {
                char[] chars = console.readPassword("%s", prompt);
                return chars == null ? null : new String(chars);
            }

            return console.readLine("%s", prompt);
        }

        // No TTY (piped input / IDE): fall back to stdin, which cannot hide input.
        if (prompt != null) System.err.print(prompt);

        try
        {
            if (fallback == null) fallback = new BufferedReader(new InputStreamReader(System.in));
            String line = fallback.readLine();

            // Windows tools (PowerShell pipes, editors) often prepend a UTF-8 BOM to the first line.
            return line != null && !line.isEmpty() && line.charAt(0) == '﻿' ? line.substring(1) : line;
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
