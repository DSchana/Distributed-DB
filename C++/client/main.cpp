#include <arpa/inet.h>
#include <cstring>
#include <fstream>
#include <iostream>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define IP_ADDR "127.0.0.1"

int main() {
    std::ifstream request_stream("./ddb_request", std::ifstream::in);
    std::string request((std::istreambuf_iterator<char>(request_stream)), std::istreambuf_iterator<char>());

    struct sockaddr_in dst;
    int sock = socket(AF_INET, SOCK_STREAM, 0);

    memset(&dst, 0, sizeof(dst));
    dst.sin_family = AF_INET;
    dst.sin_addr.s_addr = inet_addr(IP_ADDR);
    dst.sin_port = htons(8061);

    connect(sock, (struct sockaddr*)&dst, sizeof(struct sockaddr_in));

    char rsp[1000];

    send(sock, request.c_str(), strlen(request.c_str()), 0);
    recv(sock, rsp, 1000, 0);

    std::cout << rsp << std::endl;
    close(sock);

    return 0;
}
