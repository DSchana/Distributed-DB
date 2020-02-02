#include <cstring>
#include <iostream>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>

#define IP_ADDR "127.0.0.1"

int main() {
    char msg[100] = "What's good";
    struct sockaddr_in dst;
    int sock = socket(AF_INET, SOCK_STREAM, 0);

    memset(&dst, 0, sizeof(dst));
    dst.sin_family = AF_INET;
    dst.sin_addr.s_addr = inet_addr(IP_ADDR);
    dst.sin_port = htons(8061);

    connect(sock, (struct sockaddr*)&dst, sizeof(struct sockaddr_in));

    send(sock, msg, strlen(msg), 0);

    return 0;
}
