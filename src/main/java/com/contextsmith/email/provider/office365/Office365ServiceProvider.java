package com.contextsmith.email.provider.office365;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.core.DefaultClientConfig;
import com.microsoft.graph.core.IClientConfig;
import com.microsoft.graph.extensions.GraphServiceClient;
import com.microsoft.graph.extensions.IGraphServiceClient;

public class Office365ServiceProvider {

    public IGraphServiceClient createClient(String accessToken) {
        final String header = "Bearer " + accessToken;
        IAuthenticationProvider provider = iHttpRequest -> {
            iHttpRequest.addHeader("Authorization", header);
            iHttpRequest.addHeader("Accept", "application/json");
        };

        IClientConfig config = DefaultClientConfig.createWithAuthenticationProvider(provider);
        // config.getLogger().setLoggingLevel(LoggerLevel.Debug);
        final IGraphServiceClient client = new GraphServiceClient
                                            .Builder()
                                            .fromConfig(config)
                                            .buildClient();
        return client;
    }
}
