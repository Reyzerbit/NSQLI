package com.reyzerbit.suiteqlcli;

import com.reyzerbit.suiteqlcli.model.Profile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProfileRepository
{
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final SecureStore store = new SecureStore(AppPaths.profilesFile());

    public List<Profile> list() throws Exception
    {
        List<Profile> profiles = new ArrayList<>();
        if (!store.exists()) return profiles;

        for (JsonNode node : MAPPER.readTree(store.read()).path("profiles"))
        {
            profiles.add(new Profile(
                node.path("id").asString(),
                node.path("accountId").asString(),
                node.path("realm").asString(),
                node.path("consumerKey").asString(),
                node.path("consumerSecret").asString(),
                node.path("tokenId").asString(),
                node.path("tokenSecret").asString()));
        }

        return profiles;
    }

    public Optional<Profile> find(String id) throws Exception
    {
        return list().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public void save(List<Profile> profiles) throws Exception
    {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode array = root.putArray("profiles");

        for (Profile p : profiles)
        {
            array.addObject()
                .put("id", p.id())
                .put("accountId", p.accountId())
                .put("realm", p.realm())
                .put("consumerKey", p.consumerKey())
                .put("consumerSecret", p.consumerSecret())
                .put("tokenId", p.tokenId())
                .put("tokenSecret", p.tokenSecret());
        }

        store.write(MAPPER.writeValueAsBytes(root));
    }
}
