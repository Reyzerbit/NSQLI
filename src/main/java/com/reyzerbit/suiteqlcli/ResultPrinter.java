package com.reyzerbit.suiteqlcli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ResultPrinter
{
    private static final JsonMapper PRETTY = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private ResultPrinter() {}

    public static void print(List<JsonNode> rows, String format)
    {
        switch (format.toLowerCase())
        {
            case "json" -> System.out.println(PRETTY.writeValueAsString(rows));
            case "csv" -> printCsv(rows);
            case "table" -> printTable(rows);
            default -> throw new IllegalArgumentException("Unknown format '" + format + "' (expected table, json or csv).");
        }
    }

    private static List<String> columns(List<JsonNode> rows)
    {
        Set<String> columns = new LinkedHashSet<>();
        for (JsonNode row : rows) columns.addAll(row.propertyNames());
        return new ArrayList<>(columns);
    }

    private static String cell(JsonNode row, String column)
    {
        JsonNode value = row.get(column);
        return value == null || value.isNull() ? "" : value.asString();
    }

    private static void printCsv(List<JsonNode> rows)
    {
        List<String> columns = columns(rows);
        System.out.println(String.join(",", columns.stream().map(ResultPrinter::csv).toList()));
        for (JsonNode row : rows) System.out.println(String.join(",", columns.stream().map(c -> csv(cell(row, c))).toList()));
    }

    private static String csv(String value)
    {
        return value.matches("(?s).*[\",\r\n].*") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private static void printTable(List<JsonNode> rows)
    {
        if (rows.isEmpty())
        {
            System.out.println("(0 rows)");
            return;
        }

        List<String> columns = columns(rows);
        int[] widths = new int[columns.size()];

        for (int i = 0; i < widths.length; i++)
        {
            widths[i] = columns.get(i).length();
            for (JsonNode row : rows) widths[i] = Math.max(widths[i], cell(row, columns.get(i)).length());
        }

        printLine(columns, widths);

        StringBuilder rule = new StringBuilder();
        for (int i = 0; i < widths.length; i++) rule.append(i == 0 ? "" : "-+-").append("-".repeat(widths[i]));
        System.out.println(rule);

        for (JsonNode row : rows) printLine(columns.stream().map(c -> cell(row, c)).toList(), widths);
        System.out.println("(" + rows.size() + " row" + (rows.size() == 1 ? "" : "s") + ")");
    }

    private static void printLine(List<String> values, int[] widths)
    {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < widths.length; i++) line.append(i == 0 ? "" : " | ").append(String.format("%-" + widths[i] + "s", values.get(i)));
        System.out.println(line.toString().stripTrailing());
    }
}
