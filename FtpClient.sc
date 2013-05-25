import "mysocket.sc"

[const]
HELP_STR: string := "Command provided: get/put/pwd/dir/cd/quit/?\n"

class FtpClient
    [static, const]
    CMD_BUF_LEN := 128

    sock: TcpSocket

    pwd: string

    cmd_buf: char[CMD_BUF_LEN]

    cmd_args: string[3]
    cmd_argc: int

    void parse(cmd: string)
        cmd_argc = 0
        st := 0
        for i <- 0 to cmd.length(), cmd[i]==' ' or cmd[i]=='\n'
            cmd_args[cmd_argc++] = cmd.substr(st,i-st)
            st = i + 1
            if cmd_argc==3
                return

        if cmd[cmd.length()-1] != '\n'
            cmd_args[cmd_argc++] = cmd.substr(st,cmd.length()-st)

    string readResponse(res_code: int*) = sock.readLine(res_code)

    int sendCommand(cmd: const char*)
        printf("Command: %s", cmd)
        return sock.send(cmd, strlen(cmd))

    TcpSocket enterPassiveMode()
        sendCommand("PASV\r\n")

        res_code: int
        res := readResponse(&res_code);
        printf("%s", res.c_str())
        if res_code!=-1 and res[0]=='2'
            st := res.find('(') + 1
            ed := st
            dotMeeted := 0
            while dotMeeted < 4
                ++ed
                if res[ed] == ','
                    res[ed] = '.'
                    ++dotMeeted
            ++ed
            ip := res.substr(st,ed-st-1)
            port := 0
            while res[ed]!=','
                port = (port) * 10 + res[ed]-'0'
                ++ed
            ++ed
            lowpart := 0
            while res[ed]!=')'
                lowpart = lowpart * 10 + res[ed]-'0'
                ++ed

            port = (port<<8)+lowpart
            printf("passive ip:%s port:%d\n",ip.c_str(),port);

            sock: TcpSocket
            sock.connectToServ(ip.c_str(),port)
            return sock
        else
            printf("Error!")
            exit(0)

    [public]
    int connectToHost(ip: const char*, port: int, username: const char*, password: const char*)
        if sock.connectToServ(ip, port) < 0
            perror("Can't connect to ftp server\n")
            return -1

        res_code: int
        response := readResponse(&res_code)

        sprintf(cmd_buf, "USER %s\r\n", username)
        sendCommand(cmd_buf)

        response = readResponse(&res_code)
        if response[0] != '3'
            return -1

        sprintf(cmd_buf, "PASS %s\r\n", password)
        sendCommand(cmd_buf)
        response = readResponse(&res_code)

        if response[0] != '2'
            printf("Login failed\n")
            return -1
        return 0

    [public]
    void processInput()
        loop
            printf("FTP> ")
            line: string
            if not getline(cin, line) then break
            parse(line)

            res_code: int

            switch
                when cmd_args[0] == "pwd"
                    sendCommand("PWD\r\n")
                    res := readResponse(&res_code)
                    printf("%s", res.c_str())

                when cmd_args[0] == "dir"
                    tmpSocket := @enterPassiveMode()
                    sendCommand("LIST\r\n")
                    res := readResponse(&res_code)
                    printf("%s", res.c_str())

                    loop
                        res = tmpSocket.readLine(&res_code);
                        printf("%s", res.c_str())
                        if res_code < 0 then break

                    res = readResponse(&res_code)
                    printf("\n%s", res.c_str())

                when cmd_args[0] == "cd"
                    sprintf(cmd_buf,"CWD %s\r\n",cmd_args[1].c_str())
                    sendCommand(cmd_buf)
                    res := readResponse(&res_code)
                    printf("Response: %s", res.c_str())

                when cmd_args[0] == "delete"
                    sprintf(cmd_buf,"DELE %s\r\n",cmd_args[1].c_str())
                    sendCommand(cmd_buf)
                    printf("Response: %s", readResponse(&res_code).c_str())

                when cmd_args[0] == "mkdir"
                    sprintf(cmd_buf,"MKD %s\r\n",cmd_args[1].c_str())
                    sendCommand(cmd_buf)
                    printf("Response: %s", readResponse(&res_code).c_str())

                when cmd_args[0] == "rename"
                    sprintf(cmd_buf,"RNFR %s\r\n",cmd_args[1].c_str())
                    sendCommand(cmd_buf)
                    res := readResponse(&res_code)
                    printf("%s", res.c_str())
                    if res[0] == '3'
                        sprintf(cmd_buf,"RNTO %s\r\n",cmd_args[2].c_str());
                        sendCommand(cmd_buf)
                        printf("%s", readResponse(&res_code).c_str())

                when cmd_args[0] == "get"
                    tmpSocket := enterPassiveMode()
                    file_name := cmd_args[1].c_str();
                    sprintf(cmd_buf,"RETR %s\r\n", file_name)
                    sendCommand(cmd_buf)
                    res := readResponse(&res_code)
                    printf("%s", res.c_str())

                    if res[0] == '1'
                        printf("Receving file %s\n",file_name)
                        total_size := tmpSocket.recvFile(file_name)
                        if total_size >= 0
                            printf("Receive %d bytes\n",total_size)
                        else
                            printf("Can't open file %s for writing!\n",file_name)
                        res = readResponse(&res_code)
                        printf("%s", res.c_str())
                    tmpSocket.close()

                when cmd_args[0] == "put"
                    tmpSocket := @enterPassiveMode()

                    file_name := cmd_args[1].c_str()
                    remote_file_name := cmd_args[2].c_str()
                    sprintf(cmd_buf,"STOR %s\r\n", remote_file_name)
                    sendCommand(cmd_buf)
                    res := readResponse(&res_code)
                    printf("%s", res.c_str())

                    if res[0] == '1'
                        total_size := tmpSocket.sendFile(file_name)
                        if total_size >= 0
                            printf("Transfer %d bytes\n",total_size)
                        else
                            printf("Can't open file %s for reading!\n",file_name)

                        tmpSocket.close()

                        res = readResponse(&res_code)
                        printf("%s", res.c_str())

                when cmd_args[0]=="quit"
                    sock.sendString("QUIT\r\n")
                    sock.close()
                    exit(0)

                when cmd_args[0]=="?"
                    printf("%s", HELP_STR.c_str())

                else
                    printf("wrong command\n")


int main(argc: int, argv: char**)
    if argc < 4
        printf("usage: exe [port] [username] [password]\n")
        return 0

    client: FtpClient
    if client.connectToHost("127.0.0.1",atoi(argv[1]),argv[2],argv[3]) < 0
        return 0
    client.processInput()
