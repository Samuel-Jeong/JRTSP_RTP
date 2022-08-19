package org.jmagni.jrtsp.service.monitor;


import org.jmagni.jrtsp.service.AppInstance;
import org.jmagni.jrtsp.service.scheduler.job.Job;
import org.jmagni.jrtsp.service.scheduler.job.JobContainer;
import org.jmagni.jrtsp.service.system.SystemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HaHandler extends JobContainer {

    private static final Logger logger = LoggerFactory.getLogger(HaHandler.class);

    private final AppInstance appInstance = AppInstance.getInstance();

    ////////////////////////////////////////////////////////////////////////////////

    public HaHandler(Job haHandleJob) {
        setJob(haHandleJob);
    }

    ////////////////////////////////////////////////////////////////////////////////

    public void init () {
        getJob().setRunnable(() -> {
            SystemManager systemManager = SystemManager.getInstance();

            String cpuUsageStr = systemManager.getCpuUsage();
            String memoryUsageStr = systemManager.getHeapMemoryUsage();

            logger.debug("| [{}] cpu=[{}], mem=[{}], thread=[{}] | DashUnitCount=[{}]",

                            cpuUsageStr, memoryUsageStr, Thread.activeCount()
            );
        });
    }

}
