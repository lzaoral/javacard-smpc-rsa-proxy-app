#! /bin/bash

MAX_ROUNDS=1000
FAIL_GEN_COUNT=0

PROXY=target/smpc_rsa_proxy-jar-with-dependencies.jar 
if [ ! -f $PROXY ]; then
    echo "Proxy app has not been built yet. Building...'"
    mvn package
fi

if [ ! -f "./smpc_rsa" ]; then
    echo "Reference implementation is missing. Please, build it and put in the same directory as this script."
    exit 1
fi

# because someone thought that printing JAVA_TOOL_OPTIONS
# to stderr is a great idea...
JAVA="java ${JAVA_TOOL_OPTIONS}"
unset JAVA_TOOL_OPTIONS

for i in $(seq $MAX_ROUNDS); do
    printf "TEST $i: "
   
    if ! $JAVA -jar $PROXY server reset > /dev/null; then
	exit 1
    fi

    if ! (yes | ./smpc_rsa client generate) > /dev/null; then
        exit 1
    fi
    
    if ! $JAVA -jar $PROXY server generate > /dev/null; then
	((FAIL_GEN_COUNT++))
	continue
    fi
    
    if ! ./smpc_rsa client sign > /dev/null; then
	exit 1
    fi
    
    if ! $JAVA -jar $PROXY server sign > /dev/null; then
	exit 1
    fi


    if ! ./smpc_rsa server verify > /dev/null; then
        exit 1
    fi

    printf "\x1b[1;32mOK\x1b[0m\n"
done;

printf "Result: %d/%d, %d%% failed\n" $FAIL_GEN_COUNT $MAX_ROUNDS $(($FAIL_GEN_COUNT * 100 / $MAX_ROUNDS))
