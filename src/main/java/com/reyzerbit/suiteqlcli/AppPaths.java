package com.reyzerbit.suiteqlcli;

import java.nio.file.Path;

public final class AppPaths
{
    private AppPaths() {}

    public static Path dir()
    {
        return Path.of(System.getProperty("user.home"), ".nsqli");
    }

    public static Path profilesFile()
    {
        return dir().resolve("profiles.enc");
    }
}
