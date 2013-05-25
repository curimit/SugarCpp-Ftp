import "mysocket.sc"

class FtpServer
    server: TcpServer
    ctrlSock: TcpSocket
    tmpServer: TcpServer*

    [static, const]
    CMD_BUF_LNE := 256

    binaryMode, passive, login: bool
    pwd, root_dir: string

    cmd_argc: int
    cmd_args: string[3]
    accounts: map<string, string>

    void parse(cmd: string)
        cmd_argc = 0
        st := 0
        for i <- 0 to cmd.size()-1, cmd[i]==' ' or cmd[i]=='\r'
            cmd_args[cmd_argc++] = cmd.substr(st, i-st)
            st = i + 1
            if cmd_argc == 3
                return

    [public]
    FtpServer()
        @login = false
        @passive = false
        @root_dir = "/home/curimit/work/FTP"
        @pwd = "/"
        @tmpServer = nil
        @binaryMode = true
        
        @accounts["swj"] = "123"
        @accounts["test"] = "test"

        chdir(root_dir.c_str())

    [public]
    void run(port: int)
        server.bindAndListen(port)
        ctrlSock = server.getConn()
        if processLgoin() < 0
            printf("user login failed")
            ctrlSock.close()
        loop
            res_code: int
            cmd := ctrlSock.readLine(&res_code)
            if res_code < 0
                @ctrlSock.close()
                printf("Client quit. Exit. \n")
                exit(0)
            printf("Client Command: %s", cmd.c_str())
            parse(cmd)
            cmd_buf: char[256]
            switch
                when cmd_args[0] == "PWD"
                    pwd: char[256]
                    sprintf(cmd_buf, "257 '%s'\r\n", getcwd(pwd, 256))
                    ctrlSock.sendString(cmd_buf)

                when cmd_args[0] == "CWD"
                    if chdir(cmd_args[1].c_str()) < 0
                        sprintf(cmd_buf, "550 %s\r\n", strerror(errno))
                        ctrlSock.sendString(cmd_buf)
                    else
                        ctrlSock.sendString("250 Directory successfully changed.\r\n")

                when cmd_args[0] == "RNFR"
                    old_name := cmd_args[1]
                    ctrlSock.sendString("350 Ready for RNTO.")
                    cmd = ctrlSock.readLine(&res_code)
                    parse(cmd)
                    new_name := cmd_args[1]
                    printf("Client Command: %s\n", cmd.c_str())
                    if rename(old_name.c_str(), new_name.c_str()) < 0
                        sprintf(cmd_buf, "550 %s\r\n", strerror(errno))
                        ctrlSock.sendString(cmd_buf)
                    else
                        ctrlSock.sendString("250 Rename successful.\r\n")

                when cmd_args[0] == "MKD"
                    if mkdir(cmd_args[1].c_str(), 0777) <0
                        sprintf(cmd_buf, "550 %s\r\n", strerror(errno))
                        ctrlSock.sendString(cmd_buf)
                    else
                        sprintf(cmd_buf, "257 '%s' created\r\n", cmd_args[1].c_str())
                        ctrlSock.sendString(cmd_buf)

                when cmd_args[0] == "DELE"
                    if remove(cmd_args[1].c_str()) < 0
                        sprintf(cmd_buf,"550 %s\r\n",strerror(errno))
                        ctrlSock.sendString(cmd_buf)
                    else
                        ctrlSock.sendString("250 Delete operation successful.\r\n")

                when cmd_args[0] == "PASV"
                    if tmpServer?
                        tmpServer->close()
                        delete(@tmpServer)
                        tmpServer = nil

                    tmpServer = new TcpServer()
                    tmpPort: uint = rand()%55535+10000
                    while tmpServer->bindAndListen(tmpPort) < 0
                        tmpPort = rand()%55535+10000
                    
                    printf("PASSIVE mode: open port %d\n", tmpPort)
                    sprintf(cmd_buf, "227 Entering Passive Mode(127,0,0,1,%d,%d)\r\n", tmpPort>>8, tmpPort&0xFF)
                    ctrlSock.sendString(cmd_buf)
                    passive = true;

                when cmd_args[0] == "LIST"
                    tmpSocket := tmpServer->getConn()
                    defer tmpSocket.close()

                    output: string
                    if cmd_argc == 1
                        output = execShellCmd("ls -lh", &res_code)
                    else
                        if cmd_args[1][0] == '/'
                            cmd_args[1] = root_dir + cmd_args[1]
                        sprintf(cmd_buf, "ls -alh %s", cmd_args[1].c_str())
                        output = execShellCmd(cmd_buf, &res_code)

                    if res_code == -1
                        ctrlSock.sendString("505 List error >_<\r\n")
                    else
                        ctrlSock.sendString("150 Here comes the directory listing.\r\n")
                        tmpSocket.sendString(output.c_str())

                        tmpServer->close()
                        delete(tmpServer)
                        tmpServer = nil

                        ctrlSock.sendString("226 Directory send OK.\r\n")

                    passive = false

                when cmd_args[0] == "RETR"
                    file_name := cmd_args[1].c_str()
                    file_size: uint32 = -1
                    statinfo: struct stat
                    if stat(file_name,&statinfo) < 0
                        ctrlSock.sendString("550 File not exist\r\n")
                        return

                    file_size = statinfo.st_size
                    if passive
                        sprintf(cmd_buf,"150 Opening BINARY mode data connection for %s (%u bytes).\r\n",file_name,file_size)
                        ctrlSock.sendString(cmd_buf)

                        tmpSocket := tmpServer->getOneConn();
                        defer tmpSocket.close()

                        total_size := tmpSocket.sendFile(cmd_args[1].c_str())
                        if total_size < 0
                            printf("Can't open file %s for reading!\n", file_name)
                        else
                            printf("Transfer %d bytes\n",total_size)

                        tmpServer->close()
                        delete(tmpServer)
                        tmpServer = nil

                        passive = false

                        ctrlSock.sendString("226 Transfer complete.\r\n")
                    else
                        printf("Not Passive!\n")

                when cmd_args[0] == "STOR"
                    file_name := cmd_args[1].c_str()
                    if passive
                        ctrlSock.sendString("150 Ok to send data.\r\n")

                        tmpSocket := tmpServer->getOneConn()
                        total_size := tmpSocket.recvFile(cmd_args[1].c_str())
                        if total_size < 0
                            printf("Can't open file %s for writing!\n",file_name)
                        else
                            printf("Transfer %d bytes\n",total_size)
                        tmpSocket.close()
                        tmpServer->close()
                        delete(tmpServer)
                        tmpServer = nil
                        passive = false

                        ctrlSock.sendString("226 Transfer complete.\r\n")
                    else
                        printf("Not Passive\n")

                when cmd_args[0] == "QUIT"
                    ctrlSock.close()
                    printf("Client quit. Exit.\n")
                    exit(0)

                when cmd_args[0] == "OPTS"
                    ctrlSock.sendString("200\r\n")

                when cmd_args[0] == "TYPE"
                    if cmd_args[1] == "I"
                        ctrlSock.sendString("200 Switching to Binary mode.\r\n")
                    else
                        ctrlSock.sendString("200 Switching to ASCII mode.\r\n")

                else
                    printf("Not Implemented: %s\n", cmd_args[0].c_str())
                    ctrlSock.sendString("502 Command not implemented\r\n")

    [public]
    int processLgoin()
        res_code: int

        ctrlSock.sendString("220 (curimit's ftp)\r\n")
        response := ctrlSock.readLine(&res_code)
        parse(response)
        username := cmd_args[1]
        printf("username: %s\n", username.c_str())

        ctrlSock.sendString("331 Please specify the password.\r\n")
        response = ctrlSock.readLine(&res_code)
        parse(response)
        password := cmd_args[1]
        printf("password: %s\n", password.c_str())

        if password == accounts[username]
            ctrlSock.sendString("230 Login successful.\r\n")
        else
            ctrlSock.sendString("530 Login incorrect.\r\n")
            ctrlSock.close()
            exit(0)
        return 0


int main(argc: int, argv: char**)
    if argc < 2
        printf("please specify a port!\n")
        return 0
    server: FtpServer
    server.run(atoi(argv[1]))