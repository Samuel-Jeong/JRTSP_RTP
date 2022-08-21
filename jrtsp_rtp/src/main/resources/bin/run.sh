#!/bin/sh

SERVICE_NAME=jrtsp
PACKAGE_NAME=jrtsp_rtp
MAIN_CLASS_NAME=RtspMain
SERVICE_HOME=/home/${SERVICE_NAME}/${PACKAGE_NAME}

JAVA_CONF=${SERVICE_HOME}/config/user_conf.ini
LOG_FILE_NAME=${SERVICE_HOME}/config/logback.xml

PATH_TO_JAR=$SERVICE_HOME/lib/jrtsp_rtp-1.0.0-jar-with-dependencies.jar
JAVA_OPT="-Dlogback.configurationFile=${LOG_FILE_NAME}"
JAVA_OPT="$JAVA_OPT -XX:+UseG1GC -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=500 -XX:+UseLargePages -verbosegc -Xms4G -Xmx4G -verbose:gc -Xlog:gc=debug:file=$SERVICE_HOME/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=100m"

function exec_start() {
        PID=`ps -ef | grep java | grep ${MAIN_CLASS_NAME} | awk '{print $2}'`
        if ! [ -z "$PID" ]
        then
                echo "[${SERVICE_NAME}] is already running"
        else
                #ulimit -n 65535
                #ulimit -s 65535
                #ulimit -u 10240
                #ulimit -Hn 65535
                #ulimit -Hs 65535
                #ulimit -Hu 10240

                java -jar $JAVA_OPT $PATH_TO_JAR ${MAIN_CLASS_NAME} $JAVA_CONF > /dev/null 2>&1 &
                echo "[${SERVICE_NAME}] started ..."
        fi
}

function exec_stop() {
	PID=`ps -ef | grep java | grep ${MAIN_CLASS_NAME} | awk '{print $2}'`
	if [ -z "$PID" ]
	then
		echo "[${SERVICE_NAME}] is not running"
	else
		echo "stopping [${SERVICE_NAME}]"
		kill "$PID"
		sleep 1
		PID=`ps -ef | grep java | grep ${MAIN_CLASS_NAME} | awk '{print $2}'`
		if [ ! -z "$PID" ]
		then
			echo "kill -9 ${PID}"
			kill -9 "$PID"
		fi
		echo "[${SERVICE_NAME}] stopped"
	fi
}

function exec_status() {
  PID=`ps -ef | grep java | grep ${MAIN_CLASS_NAME} | awk '{print $2}'`
	if [ -z "$PID" ]
	then
		echo "[${SERVICE_NAME}] is not running"
	else
		echo "[${SERVICE_NAME}] is running"
	  ps -aux | grep ${MAIN_CLASS_NAME} | grep "$PID"
	fi
}

case $2 in
    restart)
		exec_stop
		exec_start
		;;
    start)
		exec_start
    ;;
    stop)
		exec_stop
    ;;
    status)
    exec_status
    ;;
esac
