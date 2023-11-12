package BallCoreVelocityPlugin;

import com.google.inject.Inject;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import java.nio.file.Path;

public final class VelocityBootstrap {
	VelocityPlugin plugin;

	@Inject
	public VelocityBootstrap(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
		this.plugin = new VelocityPlugin(server, logger, dataDirectory);
	}

	@Subscribe
	public void onProxyInitialize(ProxyInitializeEvent event) {
		this.plugin.onProxyInitialize(event);
	}
}