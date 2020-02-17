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

int sendCommand(int sock, struct sockaddr_in& dst, std::string file) {
    connect(sock, (struct sockaddr*)&dst, sizeof(struct sockaddr_in));

    std::cout << "Command file: " << file << std::endl;

    std::ifstream request_stream(file, std::ifstream::in);
    std::string request((std::istreambuf_iterator<char>(request_stream)), std::istreambuf_iterator<char>());

    char rsp[1000];
    usleep(500000);

    send(sock, request.c_str(), strlen(request.c_str()), 0);
    recv(sock, rsp, 1000, 0);

    std::cout << "Response: " << rsp << std::endl << std::endl;

    close(sock);

    return 0;
}

int main() {
    struct sockaddr_in dst;
    int sock = socket(AF_INET, SOCK_STREAM, 0);

    memset(&dst, 0, sizeof(dst));
    dst.sin_family = AF_INET;
    dst.sin_addr.s_addr = inet_addr(IP_ADDR);
    dst.sin_port = htons(8061);

    sendCommand(sock, dst, "./insert_request");
    sendCommand(sock, dst, "./get_request");
    sendCommand(sock, dst,  "./delete_request");
    sendCommand(sock, dst, "./find_request");
    sendCommand(sock, dst, "./update_request");
    sendCommand(sock, dst, "./upsert_request");
    sendCommand(sock, dst, "./get_request");
    sendCommand(sock, dst, "./count_request");
    sendCommand(sock, dst, "./clear_request");
    sendCommand(sock, dst, "./count_request");

    return 0;
}
