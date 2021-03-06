#!/usr/bin/env bash
#set -xv
usage(){
  echo "Usage: sample-runbt.sh <result-directory-path> <snappydata-base-directory-path> [-l <local-conf-file-path> -r <num-to-run-test> -m <mailAddresses>] <list-of-bts>" 1>&2
  echo " result-directory-path         Location to put the test results " 1>&2
  echo " snappydata-base-directory-path    checkout path of snappy-data " 1>&2
  echo " list-of-bts                   name of bts to run " 1>&2
  echo " Optionally any of:" 1>&2
  echo " -l <local-conf-file-path> --   path to local conf file " 1>&2
  echo " -r <n>  -- run test suite n number of times, the default is 1" 1>&2
  echo " -m mail_address -- email address to send results of run to" 1>&2
  echo " (e.g. sh sample-runbt.sh /home/snappy/gemxdRegression /home/snappy/project/snappydata sql/sql.bt sql/sqlDisk/sqlDisk.bt )" 1>&2
  echo " or"
  echo " (e.g. sh sample-runbt.sh /home/snappy/gemxdRegression /home/snappy/project/snappydata -l /home/snappy/gemxdRegression/local.conf -r 3 sql/sql.bt sql/sqlDisk/sqlDisk.bt )" 1>&2
  exit 1
}
resultDir=
if [ $# -lt 3 ]; then
  usage
else
  resultDir=$1
  mkdir -p $resultDir
  shift
fi
SNAPPYDATADIR=$1
shift

export JTESTS=$SNAPPYDATADIR/store/tests/sql/build-artifacts/linux/classes/main
export PATH=$JAVA_HOME:$PATH:$JTESTS
export GEMFIRE=$SNAPPYDATADIR/build-artifacts/scala-2.10/store
export OUTPUT_DIR=$resultDir
export myOS=`uname | tr "cyglinsu" "CYGLINSU" | cut -b1-3`

runIters=1
localconfpath=$JTESTS/sql/snappy.local.conf
bts=
TEST_JVM=
mailAddrs=
processArgs() {
  local scnt=0
  local OPTIND=1

  while getopts ":l:r:" op
  do
    case $op in
      ( "l" ) ((scnt++)) ; localconfpath=$OPTARG ;;
      ( "r" ) ((scnt++))
        case $OPTARG in
          ( [0-9]* ) runIters=$OPTARG ;;
          ( * ) ;;
        esac ;;
      ( "m" ) ((scnt++)) ; mailAddrs="${mailAddrs} $OPTARG" ;;
      ( * ) echo "Unknown argument -$OPTARG provided: see usage " ; usage ;;
    esac
    ((scnt++))
  done

  while [ ${scnt:-0} -gt 0 ]
  do
    shift
    ((scnt--))
  done

  # rest arguments are bts
  bts="$bts $*"

  # If JAVA_HOME is not already set in system then set JAVA_HOME using TEST_JVM
  if [ "x$JAVA_HOME" = "x" ]; then
    TEST_JVM=/usr
  else
    TEST_JVM=$JAVA_HOME
  fi
}

processArgs $*

list=`ls $GEMFIRE/lib/gemfirexd-client-*`
filename=`tr "/" "\n" <<< $list | tail -1`

export releaseVersion=`echo "${filename%*.*}"| cut -d'-' -f3-4`
export CLASSPATH=$JTESTS:$EXTRA_JTESTS:$GEMFIRE/lib/gemfirexd-${releaseVersion}.jar:$GEMFIRE/lib/gemfirexd-client-${releaseVersion}.jar:$JTESTS/../../libs/gemfirexd-hydra-tests-${releaseVersion}-all.jar:$GEMFIRE/lib/gemfirexd-tools-${releaseVersion}.jar
export EXTRA_JTESTS=$SNAPPYDATADIR/store/tests/core/build-artifacts/linux/classes/main
#export JTESTS_RESOURCES=$SNAPPYDATADIR/store/tests/core/src/main/java

# This is the command to run the test, make sure the correct release version of jar used or change the jar path to use correctly. Also change the jar name in sql/snappy.local.conf if incorrect

echo $SNAPPYDATADIR/store/tests/core/src/main/java/bin/run-snappy-store-bt.sh --osbuild $GEMFIRE $OUTPUT_DIR -Dproduct=snappystore -DtestJVM=$TEST_JVM/bin/java -DEXTRA_JTESTS=$EXTRA_JTESTS  -Dbt.grepLogs=true -DremovePassedTest=true  -DnumTimesToRun=$runIters -DlocalConf=$localconfpath ${bts}
$SNAPPYDATADIR/store/tests/core/src/main/java/bin/run-snappy-store-bt.sh --osbuild $GEMFIRE $OUTPUT_DIR -Dproduct=snappystore -DtestJVM=$TEST_JVM/bin/java -DEXTRA_JTESTS=$EXTRA_JTESTS  -Dbt.grepLogs=true -DremovePassedTest=true -DnumTimesToRun=$runIters -DlocalConf=$localconfpath ${bts}
