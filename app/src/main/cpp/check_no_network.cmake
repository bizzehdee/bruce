# Fails the build if libbruce imports network functions. Bruce's network mode is enforced in Kotlin,
# so native code must not reach the network itself (docs/adr/0001-tool-calling-format.md).
execute_process(COMMAND ${NM} -D --undefined-only ${LIBRARY} OUTPUT_VARIABLE IMPORTS RESULT_VARIABLE STATUS)
if(NOT STATUS EQUAL 0)
    message(FATAL_ERROR "could not list the imports of ${LIBRARY}")
endif()
string(REGEX MATCHALL "[ \t](socket|connect|getaddrinfo|gethostbyname|sendto|recvfrom)(@[^\n]*)?\n" FOUND "${IMPORTS}")
if(FOUND)
    message(FATAL_ERROR "libbruce imports network functions:${FOUND}")
endif()
