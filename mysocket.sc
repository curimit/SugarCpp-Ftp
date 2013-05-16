import "var.sc"

[public]
class TcpSocket
    sockfd: int
    servaddr: sockaddr_in
    port: int

    TcpSocket()
        @sockfd = socket(AF_INET, SOCK_STREAM, 0)
        if sockfd == -1
            perror("can't create socket\n")

    TcpSocket(sockfd: int, addr: sockaddr_in)
        @sockfd = sockfd
        @servaddr = addr

    int connectToServ(ip: const char*, port: int)
        memset(&@servaddr, 0, sizeof(@servaddr))
        @servaddr.sin_family = AF_INET
        @servaddr.sin_port = htons(port)
        @servaddr.sin_addr.s_addr = inet_addr(ip)
        if connect(sockfd, &(@servaddr) as (sockaddr*), sizeof(@servaddr)) == -1
            perror("can't connect to server!\n")
            return -1
        return 0

    int send(buf: const void*, len: int)
        return global::send(sockfd, buf, len, 0)

    int recv(buf: void*, len: int)
        return global::recv(sockfd, buf, len, 0)

    int sendString(str: const char*)
        return send(str,strlen(str))

    string readLine(res_code: int*)
        res: string
        buf: char[BUF_SIZE + 1]

        loop
            memset(buf,0,sizeof(buf))
            len := recv(buf,BUF_SIZE)
            if len > 0
                res += string(buf)
                //cout<<"part:"<<res<<endl;
                if strstr(buf,"\r\n")?
                    *res_code=0;
                    //printf("Response:%s\n",res.c_str());
                    return res
            else
                *res_code = -1
                return res
    int close()

        return global::close(sockfd);
    
    int sendFile(file_name: const char*)
        buf: char[READ_BUF_SIZE]

        file := fopen(file_name, "r")
        if not file
            return -1
        defer fclose(file)

        read_size, total_size := 0
        while (read_size = fread(buf,1,READ_BUF_SIZE,file)) > 0
            send(buf,read_size)
            total_size += read_size
        return total_size

    int recvFile(file_name: const char*)
        buf: char[READ_BUF_SIZE];

        file := fopen(file_name, "w")
        if not file
            return -1
        defer fclose(file)

        recv_len, total_size := 0
        while (recv_len = recv(buf, READ_BUF_SIZE)) > 0
            fwrite(buf, 1, recv_len, file)
            total_size += recv_len
        return total_size

[public]
class TcpServer: TcpSocket
    int bindAndListen(port: int)
        memset(&servaddr,0,sizeof(servaddr))
        servaddr.sin_family = AF_INET
        servaddr.sin_addr.s_addr=htonl(INADDR_ANY)
        servaddr.sin_port = htons(port)
        if bind(sockfd, &servaddr as (sockaddr*), sizeof(servaddr)) != 0
            perror("bind failed\n")
            return -1

        if listen(sockfd,5)!=0
            perror("listen failed\n")
            return -1

        return 0

    TcpSocket getOneConn()
        loop
            connfd: int
            connaddr: sockaddr_in
            addrsz: socklen_t = sizeof(connaddr)
            connfd = accept(sockfd, &connaddr as (sockaddr*), &addrsz)
            if connfd == -1
                perror("invalid connection\n")
                continue;
            printf("new connection\n");
            return TcpSocket(connfd,connaddr);

    TcpSocket getConn()
        loop
            connfd: int
            connaddr: sockaddr_in
            addrsz: socklen_t = sizeof(connaddr)
            connfd = accept(sockfd, &connaddr as (sockaddr*), &addrsz)
            if connfd == -1
                perror("invalid connection\n")
                continue
            if fork() == 0
                //in child
                printf("new connection\n")
                return TcpSocket(connfd,connaddr);
            //else
            //in parent blocked in accept again!
