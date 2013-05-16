SugarCpp = ./Compiler/SugarCpp.CommandLine.exe
CC = clang++
CFLAGS= -std=c++11 -Wall -c

main: FtpServer FtpClient

# link .o files
FtpServer: FtpServer.o var.o mysocket.o
	$(CC) FtpServer.o var.o mysocket.o -o server

FtpClient: FtpClient.o var.o mysocket.o
	$(CC) FtpClient.o var.o mysocket.o -o client

# compile to .o files
FtpServer.o: FtpServer.h FtpServer.cpp mysocket.h var.h
	$(CC) $(CFLAGS) FtpServer.cpp

FtpClient.o: FtpClient.h FtpClient.cpp mysocket.h var.h
	$(CC) -std=c++11 -c FtpClient.cpp

var.o: var.h var.cpp
	$(CC) -std=c++11 -c var.cpp

mysocket.o: mysocket.h mysocket.cpp
	$(CC) -std=c++11 -c mysocket.cpp

# compile to .h/.cpp files
FtpServer.cpp, FtpServer.h: FtpServer.sc
	$(SugarCpp) FtpServer.sc

FtpClient.cpp, FtpClient.h: FtpClient.sc
	$(SugarCpp) FtpClient.sc

var.cpp, var.h: var.sc
	$(SugarCpp) var.sc

mysocket.cpp, mysocket.h: mysocket.sc
	$(SugarCpp) mysocket.sc

# clean up
clean:
	rm *.h *.cpp *.o
