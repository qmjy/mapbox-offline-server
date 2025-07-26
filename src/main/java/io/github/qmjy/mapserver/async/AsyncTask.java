package io.github.qmjy.mapserver.async;

import io.github.qmjy.mapserver.MapServerDataCenter;
import io.github.qmjy.mapserver.config.AppConfig;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;

@Component
@AllArgsConstructor
public class AsyncTask {
    private final Logger logger = LoggerFactory.getLogger(AsyncTask.class);
    private final AppConfig appConfig;

    /**
     * 每10秒执行一次
     */
    @Async
    @Scheduled(cron = "0/10 * * * * ?")
    public void tasks() {
        if (!MapServerDataCenter.getInstance().isInitialized()) {
            return;
        }

        if (StringUtils.hasLength(appConfig.getDataPath())) {
            File dataFolder = new File(appConfig.getDataPath());
            if (dataFolder.isDirectory() && dataFolder.exists()) {
                File tilesetsFolder = new File(dataFolder, "tilesets");
                File[] files = tilesetsFolder.listFiles(pathname -> pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_MBTILES));
                if (files != null) {
                    for (File dbFile : files) {
                        MapServerDataCenter.getInstance().initJdbcTemplate(appConfig.getDriverClassName(), dbFile);
                    }
                }

                File[] tpks = tilesetsFolder.listFiles(pathname -> pathname.getName().endsWith(AppConfig.FILE_EXTENSION_NAME_TPK));
                if (tpks != null) {
                    for (File tpk : tpks) {
                        MapServerDataCenter.getInstance().indexTpk(tpk);
                    }
                }
            }
        }
    }
}

