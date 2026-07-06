package com.example.pvplatform.module.auth.oauth;

import com.example.pvplatform.config.OAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OAuthProvidersConfig {

    @Bean
    List<OAuthProviderConfig> oauthProviders(OAuthProperties props) {
        List<OAuthProviderConfig> list = new ArrayList<>();

        // GitHub
        OAuthProperties.Providers p = props.providers();
        if (p != null && p.github() != null && p.github().enabled()) {
            OAuthProperties.Github g = p.github();
            list.add(new OAuthProviderConfig(
                "github", true,
                g.clientId(), g.clientSecret(),
                g.authorizeUrl(), g.tokenUrl(), g.userInfoUrl(), g.scopes()
            ));
        }

        // Mock — always available for testing
        list.add(new OAuthProviderConfig(
            "mock", true, "", "", "", "", "", ""
        ));

        return list;
    }
}
