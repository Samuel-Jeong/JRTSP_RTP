package org.jmagni.jrtsp.config;


import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class ConfigManager {

    private UserConfig userConfig = null;

    public ConfigManager(String userConfigFilePath) {
        this.userConfig = new UserConfig(userConfigFilePath);
    }


}
