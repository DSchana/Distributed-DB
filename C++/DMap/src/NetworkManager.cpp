#include <chrono>
#include <cstring>
#include <iostream>
#include <NetworkManager.h>
#include <rapidjson/document.h>

NetworkManager::NetworkManager() {
    memset(&serv, 0, sizeof(serv));
    serv.sin_family = AF_INET;
    serv.sin_addr.s_addr = htonl(INADDR_ANY);
    serv.sin_port = htons(8061);

    sock = socket(AF_INET, SOCK_STREAM, 0);
}

NetworkManager::~NetworkManager() {

}

int NetworkManager::start() {
    running = true;

    bind(sock, (struct sockaddr *) &serv, sizeof(struct sockaddr));
    listen(sock, 1);

    while (running) {
        if (connection_mutex.try_lock()) {
            connection_mutex.unlock();
            connections.push_back(std::async(std::launch::async, &NetworkManager::handleConnection, this));  // TODO: Fix this
        }
        else {
            std::vector<std::vector<std::future<int> >::iterator> expired_connections;
            for (auto it = connections.begin(); it != connections.end(); it++) {
                if (it->wait_for(std::chrono::microseconds(5)) == std::future_status::ready) {
                    int status = it->get();
                    expired_connections.push_back(it);
                }
            }

            for (auto& e : expired_connections) {
                connections.erase(e);
            }
        }
    }

    return 0;
}

int NetworkManager::stop() {
    running = false;

    return 0;
}

int NetworkManager::handleConnection() {
    using namespace rapidjson;

    // Since call to accept is blocking, the connection mutex will stay locked until
    // a connection is accepted which means a new connection thread will always be
    // ready to replace the old one, allowing for multiple async connections.
    connection_mutex.lock();
    int consock = accept(sock, (struct sockaddr*)&dst, &sock_size);
    connection_mutex.unlock();

    if (consock) {
        char buf[500];

        data_recv_mutex.lock();
        recv(consock, buf, 99, 0);
        data_recv_mutex.unlock();

        // TODO: Do something useful with data from socket
        std::cout << "Msg: " << buf << std::endl;
        Document payload;
        payload.Parse(buf);

        std::string cmd = payload.GetString();
    }
    else {
        return -1;
    }

    return 0;
}