package com.github.topi314.lavasrc.plugin.config;

import com.github.topi314.lavasrc.deezer.DeezerAudioTrack;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.lavasrc.deezer")
@Component
public class DeezerConfig {
}
