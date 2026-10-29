package com.reyzerbit.suiteqlcli.command;

import com.reyzerbit.suiteqlcli.ProfileRepository;
import com.reyzerbit.suiteqlcli.model.Profile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "profile", description = "Manage NetSuite profiles.", subcommands = {ProfileCommand.New.class, ProfileCommand.ListProfiles.class, ProfileCommand.Show.class, ProfileCommand.Delete.class})
public class ProfileCommand implements Runnable
{
    @Override
    public void run()
    {
        new picocli.CommandLine(this).usage(System.out);
    }

    private static String mask(String secret)
    {
        return secret.length() <= 4 ? "****" : "****" + secret.substring(secret.length() - 4);
    }

    @Command(name = "new", description = "Create a profile. Missing values are prompted for (secrets are hidden).")
    static class New implements Callable<Integer>
    {
        @Parameters(index = "0", arity = "0..1", paramLabel = "<profile-id>", description = "Name for the profile.")
        String id;

        @Option(names = "--account-id", description = "NetSuite account ID, e.g. 1234567 or 1234567_SB1.")
        String accountId;

        @Option(names = "--realm", description = "OAuth realm. Defaults to the account ID, uppercased.")
        String realm;

        @Option(names = "--consumer-key")
        String consumerKey;

        @Option(names = "--consumer-secret", description = "Prefer the prompt: flags land in shell history.")
        String consumerSecret;

        @Option(names = "--token-id")
        String tokenId;

        @Option(names = "--token-secret", description = "Prefer the prompt: flags land in shell history.")
        String tokenSecret;

        @Option(names = "--force", description = "Overwrite an existing profile with the same ID.")
        boolean force;

        @Override
        public Integer call() throws Exception
        {
            ProfileRepository repo = new ProfileRepository();
            List<Profile> profiles = repo.list();

            String profileId = Prompts.valueOrPrompt(id, "Profile ID", null, false);
            boolean exists = profiles.stream().anyMatch(p -> p.id().equals(profileId));

            if (exists && !force)
            {
                System.err.println("Profile '" + profileId + "' already exists. Use --force to overwrite.");
                return 1;
            }

            String account = Prompts.valueOrPrompt(accountId, "Account ID", null, false);
            String realmValue = Prompts.valueOrPrompt(realm, "Account realm", Profile.defaultRealm(account), false);
            String ck = Prompts.valueOrPrompt(consumerKey, "Consumer key", null, true);
            String cs = Prompts.valueOrPrompt(consumerSecret, "Consumer secret", null, true);
            String ti = Prompts.valueOrPrompt(tokenId, "Token ID", null, true);
            String ts = Prompts.valueOrPrompt(tokenSecret, "Token secret", null, true);

            profiles.removeIf(p -> p.id().equals(profileId));
            profiles.add(new Profile(profileId, account, realmValue, ck, cs, ti, ts));
            repo.save(profiles);

            System.out.println("Saved profile '" + profileId + "'. Connect with: nsqli run -p " + profileId);
            return 0;
        }
    }

    @Command(name = "list", description = "List profiles.")
    static class ListProfiles implements Callable<Integer>
    {
        @Override
        public Integer call() throws Exception
        {
            List<Profile> profiles = new ProfileRepository().list();

            if (profiles.isEmpty())
            {
                System.out.println("No profiles. Create one with: nsqli profile new");
                return 0;
            }

            for (Profile p : profiles) System.out.println(p.id() + "  (" + p.accountId() + ")");
            return 0;
        }
    }

    @Command(name = "show", description = "Show a profile with secrets masked.")
    static class Show implements Callable<Integer>
    {
        @Parameters(index = "0", paramLabel = "<profile-id>")
        String id;

        @Override
        public Integer call() throws Exception
        {
            Optional<Profile> found = new ProfileRepository().find(id);

            if (found.isEmpty())
            {
                System.err.println("No profile named '" + id + "'.");
                return 1;
            }

            Profile p = found.get();
            System.out.println("id:              " + p.id());
            System.out.println("account id:      " + p.accountId());
            System.out.println("realm:           " + p.realm());
            System.out.println("consumer key:    " + mask(p.consumerKey()));
            System.out.println("consumer secret: " + mask(p.consumerSecret()));
            System.out.println("token id:        " + mask(p.tokenId()));
            System.out.println("token secret:    " + mask(p.tokenSecret()));
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete a profile.")
    static class Delete implements Callable<Integer>
    {
        @Parameters(index = "0", paramLabel = "<profile-id>")
        String id;

        @Override
        public Integer call() throws Exception
        {
            ProfileRepository repo = new ProfileRepository();
            List<Profile> profiles = repo.list();

            if (!profiles.removeIf(p -> p.id().equals(id)))
            {
                System.err.println("No profile named '" + id + "'.");
                return 1;
            }

            repo.save(profiles);

            System.out.println("Deleted profile '" + id + "'.");
            return 0;
        }
    }
}
