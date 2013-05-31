import "sys/stat.h"
       "sys/types.h"
       "sys/socket.h"
       "netinet/in.h"
       "arpa/inet.h"
       "unistd.h"
       "cerrno"
       "fcntl.h"
       "assert.h"
       "iostream"
       "cstring"
       "string"
       "cstdlib"
       "cstdio"
       "cstdint"
       "map"

using namespace std

[const]
CNTL_PORT := 21

[const]
DATA_PORT := 20

[const]
TEST_PORT := 12345

[const]
BUF_SIZE := 128

[const]
READ_BUF_SIZE := 1024

bool startsWith(s: string&, pat: const char*)
    if strlen(pat) > s.length()
        return false

    index := 0
    while *pat
        if s[index++] != *pat
            return false
        pat = pat + 1

    return true

int toInt(s: const string&) = atoi(s.c_str())

string execShellCmd(cmd: const char*, res_code: int*)
    buf: char[BUF_SIZE]

    res: string
    ptr := popen(cmd, "r")
    if ptr?
        defer pclose(ptr)
        while fgets(buf,BUF_SIZE,ptr)?
            res += buf
        *res_code = 0
    else
        perror("can't open pipe!")
        *res_code = -1
    return res