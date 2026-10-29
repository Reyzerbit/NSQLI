package com.reyzerbit.suiteqlcli.model;

public record Profile(String id, String accountId, String realm, String consumerKey, String consumerSecret, String tokenId, String tokenSecret)
{
    // NetSuite hosts use the lowercased account id with underscores turned into dashes (e.g. 1234567_SB1 -> 1234567-sb1).
    public String host()
    {
        return accountId.toLowerCase().replace('_', '-');
    }

    public static String defaultRealm(String accountId)
    {
        return accountId.toUpperCase().replace('-', '_');
    }
}
