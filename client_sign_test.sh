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

if [ ! -f "message.txt" ]; then
    echo "Message file is missing in the 'build' directory. Creating one..."
    echo "a454564654d654654e654654f654654" > message.txt
fi

# because someone thought that printing JAVA_TOOL_OPTIONS
# to stderr is a great idea...
JAVA="java ${JAVA_TOOL_OPTIONS}"
unset JAVA_TOOL_OPTIONS

for i in $(seq $MAX_ROUNDS); do
    printf "TEST $i: "
   
    if ! $JAVA -jar $PROXY client-sign reset > /dev/null; then
	exit 1
    fi

    if ! (yes | ./smpc_rsa client generate) > /dev/null; then
        exit 1
    fi

    if ! (yes | ./smpc_rsa server generate) > /dev/null; then
	((FAIL_GEN_COUNT++))
        continue
    fi

    if ! $JAVA -jar $PROXY client-sign generate > /dev/null; then
	exit 1
    fi
    
    if ! $JAVA -jar $PROXY client-sign sign > /dev/null; then
	exit 1
    fi

    if ! ./smpc_rsa server sign > /dev/null; then
	exit 1
    fi

    if ! ./smpc_rsa server verify > /dev/null; then
        exit 1
    fi

    printf "\x1b[1;32mOK\x1b[0m\n"
done;

printf "Result: %d/%d, %d%% failed\n" $FAIL_GEN_COUNT $MAX_ROUNDS $(($FAIL_GEN_COUNT * 100 / $MAX_ROUNDS))
