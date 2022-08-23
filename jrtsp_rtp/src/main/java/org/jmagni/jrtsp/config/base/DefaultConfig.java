package org.jmagni.jrtsp.config.base;

import org.apache.commons.configuration2.*;
import org.apache.commons.configuration2.builder.BuilderParameters;
import org.apache.commons.configuration2.builder.ConfigurationBuilderEvent;
import org.apache.commons.configuration2.builder.ReloadingFileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.event.Event;
import org.apache.commons.configuration2.event.EventListener;
import org.apache.commons.configuration2.reloading.PeriodicReloadingTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DefaultConfig {
    public static final Logger logger = LoggerFactory.getLogger(DefaultConfig.class);
    private ReloadingFileBasedConfigurationBuilder<FileBasedConfiguration> builder;
    private PeriodicReloadingTrigger trigger;
    private Configuration config;
    private String fileName;
    private ConfigChangedListener configChangedListener = null;

    public DefaultConfig(String fileName) {
        this.fileName = fileName;
    }

    protected boolean load() {
        if (this.fileName == null) {
            return false;
        } else {
            logger.info("Load config [{}]", this.fileName);
            Parameters params = new Parameters();
            File file = new File(this.fileName);
            this.builder = (new ReloadingFileBasedConfigurationBuilder(INIConfiguration.class)).configure(new BuilderParameters[]{(params.fileBased().setFile(file)).setReloadingRefreshDelay(0L)});

            try {
                this.config = this.builder.getConfiguration();
            } catch (Exception var4) {
                logger.warn("() () () Error Occurs", var4);
            }

            this.trigger = new PeriodicReloadingTrigger(this.builder.getReloadingController(), (Object)null, 1L, TimeUnit.SECONDS);
            this.trigger.start();
            this.builder.addEventListener(ConfigurationBuilderEvent.RESET, new CustomConfigurationEvent());
            logger.debug("Loading Config : {}", this.config);
            return true;
        }
    }

    public void setConfigChangedListener(ConfigChangedListener listener) {
        this.configChangedListener = listener;
    }

    public void diffConfig(Configuration config1, Configuration config2) {
        boolean changed = false;
        Iterator<String> keys = config1.getKeys();

        String key;
        Object v1;
        while(keys.hasNext()) {
            key = keys.next();
            v1 = config1.getProperty(key);
            Object v2 = config2.getProperty(key);
            if (!Objects.equals(v1, v2)) {
                this.config.setProperty(key, v2);
                if (!changed) {
                    changed = true;
                }
            }
        }

        keys = config2.getKeys();

        while(keys.hasNext()) {
            key = keys.next();
            v1 = config2.getProperty(key);
            if (!config1.containsKey(key)) {
                this.config.setProperty(key, v1);
                if (!changed) {
                    changed = true;
                }
            }
        }

        if (changed && this.configChangedListener != null) {
            this.configChangedListener.configChanged(true);
        }

    }

    public void close() {
        this.trigger.start();
        this.config.clear();
        this.config = null;
    }

    public String getStrValue(String section, String key, String defaultValue) {
        String mkey = section + "." + key;
        if (section == null) {
            return defaultValue;
        } else {
            String value = this.config.getString(mkey, defaultValue);
            logger.info("\tConfig key [{}] str [{}]", key, value);
            return value;
        }
    }

    public int getIntValue(String section, String key, int defaultValue) {
        String mkey = section + "." + key;
        if (section == null) {
            return defaultValue;
        } else {
            int value = this.config.getInt(mkey, defaultValue);
            logger.info("\tConfig key [{}] int [{}]", key, value);
            return value;
        }
    }

    public float getFloatValue(String section, String key, float defaultValue) {
        String mkey = section + "." + key;
        return section == null ? defaultValue : this.config.getFloat(mkey, defaultValue);
    }

    public boolean getBooleanValue(String section, String key, boolean defaultValue) {
        String mkey = section + "." + key;
        return section == null ? defaultValue : this.config.getBoolean(mkey, defaultValue);
    }

    private class CustomConfigurationEvent implements EventListener<Event> {
        private CustomConfigurationEvent() {
        }

        public void onEvent(Event event) {
            DefaultConfig.logger.warn("onEvent");

            try {
                Configuration newConfig = (Configuration)DefaultConfig.this.builder.getConfiguration();
                ConfigurationComparator comparator = new StrictConfigurationComparator();
                if (!comparator.compare(DefaultConfig.this.config, newConfig)) {
                    DefaultConfig.this.diffConfig(DefaultConfig.this.config, newConfig);
                }

                newConfig.clear();
            } catch (Exception var4) {
                DefaultConfig.logger.error("Error Occurs", var4);
            }

        }
    }
}
