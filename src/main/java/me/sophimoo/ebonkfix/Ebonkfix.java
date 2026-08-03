package me.sophimoo.ebonkfix;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import org.slf4j.Logger;

public class Ebonkfix extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOG.info("Initializing Ebonkfix");

        // Bounce mode obstacle passer (portal trap chunk scanning)
        MeteorClient.EVENT_BUS.subscribe(new ObstaclePasser());
    }

    @Override
    public String getPackage() {
        return "me.sophimoo.ebonkfix";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("sophimoo", "ebonkfix");
    }
}