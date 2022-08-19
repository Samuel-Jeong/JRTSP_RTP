package org.jmagni.jrtsp;

import lombok.extern.slf4j.Slf4j;
import org.jmagni.jrtsp.config.ConfigManager;
import org.jmagni.jrtsp.service.AppInstance;
import org.jmagni.jrtsp.service.ServiceManager;

@Slf4j
public class RtspMain {

    public static void main(String[] args) {
        if (args.length != 2) {
            log.error("Argument Error. (&0: DashServerMain, &1: config_path)");
            return;
        }

        String configPath = args[1].trim();
        log.debug("[DashServerMain] Config path: {}", configPath);
        ConfigManager configManager = new ConfigManager(configPath);

        AppInstance appInstance = AppInstance.getInstance();
        appInstance.setConfigManager(configManager);
        appInstance.setConfigPath(configPath);

        ServiceManager serviceManager = ServiceManager.getInstance();
        serviceManager.loop();
    }

}
