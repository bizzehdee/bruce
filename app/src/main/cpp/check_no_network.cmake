# Fails the build if a library imports network functions, or starts other programs. Bruce's network
# mode is enforced in Kotlin, so native code must not reach the network itself
# (docs/adr/0001-tool-calling-format.md), nor run a program that could.
execute_process(COMMAND ${NM} -D --undefined-only ${LIBRARY} OUTPUT_VARIABLE IMPORTS RESULT_VARIABLE STATUS)
if(NOT STATUS EQUAL 0)
    message(FATAL_ERROR "could not list the imports of ${LIBRARY}")
endif()
string(REGEX MATCHALL "[ \t](socket|connect|getaddrinfo|gethostbyname|sendto|recvfrom)(@[^\n]*)?\n" FOUND "${IMPORTS}")
if(FOUND)
    message(FATAL_ERROR "${LIBRARY} imports network functions:${FOUND}")
endif()
string(REGEX MATCHALL "[ \t](fork|vfork|execv|execve|execvp|execl|execlp|posix_spawn|posix_spawnp|popen|system)(@[^\n]*)?\n" SPAWNS "${IMPORTS}")
if(SPAWNS)
    message(FATAL_ERROR "${LIBRARY} imports functions that start programs:${SPAWNS}")
endif()
