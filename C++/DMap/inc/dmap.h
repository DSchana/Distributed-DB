#ifndef DMAP_H
#define DMAP_H

#include <arpa/inet.h>
#include <atomic>
#include <base64.h>
#include <cstring>
#include <future>
#include <iostream>
#include <map>
#include <netinet/in.h>
#include <rapidjson/document.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <vector>
#include <mutex>

template <class K, class V>
class dmap {
    std::map<std::string, std::string> data;
    std::future<int> nm_future;

    // Networking
    bool running;

    std::vector<std::future<int> > connections;
    std::mutex connection_mutex;
    std::mutex data_recv_mutex;

    int sock;
    struct sockaddr_in dst;
    struct sockaddr_in serv;
    socklen_t sock_size = sizeof(struct sockaddr_in);

    int handleConnection();

public:
    dmap();
    ~dmap();
    void insert(K key, V value);
    V& get(K key);
    bool erase(K key);
    bool find(K key);
    bool update(K key, V value);
    void upsert(K key, V value);
    void clear();
    int size();

    // Operators
    V& operator[](K key);

    // Networking
    bool isJSONValid(rapidjson::Document& doc);

    int start();
    int stop();
};

template <class K, class V>
dmap<K, V>::dmap() {
    running = true;
    // TODO: Read port from config file

    memset(&serv, 0, sizeof(serv));
    serv.sin_family = AF_INET;
    serv.sin_addr.s_addr = htonl(INADDR_ANY);
    serv.sin_port = htons(8061);

    sock = socket(AF_INET, SOCK_STREAM, 0);

    nm_future = std::async(std::launch::async, &dmap<K, V>::start, this);
}

template <class K, class V>
dmap<K, V>::~dmap() {

}

template <class K, class V>
void dmap<K, V>::insert(K key, V value) {
    data[key] = value;
}

template <class K, class V>
V& dmap<K, V>::get(K key) {
    return data[key];
}

template <class K, class V>
bool dmap<K, V>::erase(K key) {
    typename std::map<K, V>::iterator it;
    if ((it = data.find(key)) != data.end()) {
        data.erase(it);
        return true;
    }
    return false;
}

template <class K, class V>
bool dmap<K, V>::find(K key) {
    return data.find(key) != data.end();
}

template <class K, class V>
bool dmap<K, V>::update(K key, V value) {
    if (find(key)) {
        data[key] = value;
        return true;
    }
    return false;
}

template <class K, class V>
void dmap<K, V>::upsert(K key, V value) {
    data[key] = value;
}

template <class K, class V>
void dmap<K, V>::clear() {
    data.clear();
}

template <class K, class V>
int dmap<K, V>::size() {
    return data.size();
}

template <class K, class V>
V& dmap<K, V>::operator[](K key) {
    return data[key];
}

/// Networking

template <class K, class V>
int dmap<K, V>::start() {
    bind(sock, (struct sockaddr *) &serv, sizeof(struct sockaddr));
    listen(sock, 1);

    while (running) {
        if (connection_mutex.try_lock()) {
            connection_mutex.unlock();
            connections.push_back(std::async(std::launch::async, &dmap<K, V>::handleConnection, this));  // TODO: Fix this
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

template <class K, class V>
int dmap<K, V>::stop() {
    running = false;

    return 0;
}

template <class K, class V>
bool dmap<K, V>::isJSONValid(rapidjson::Document& doc) {
    using namespace rapidjson;

    if (!(doc.HasMember("id") && doc["id"].IsString()) ||
        !(doc.HasMember("command") && doc["command"].IsString()) ||
        !doc.HasMember("payload")) {
        return false;
    }

    Value payloads(kArrayType);

    if (doc["payload"].IsArray()) {
        payloads = doc["payload"].GetArray();
    }
    else if (doc["payload"].IsObject()) {
        payloads.PushBack(doc["payload"].GetObject(), doc.GetAllocator());
    }
    else {
        return false;
    }

    std::string command = doc["command"].GetString();
    for (auto& payload : payloads.GetArray()) {
        if (command == "insert" || command == "update" || command == "upsert") {  // Needs key and value
            if (!(payload.HasMember("key") && payload["key"].IsString()) || !(payload.HasMember("value") && payload["value"].IsString())) {
                return false;
            }
        }
        else if (command == "get" || command == "delete" || command == "find") {  // Needs key only
            if (!payload.HasMember("key")) {
                return false;
            }
        }
        else if (command == "clear" || command == "count") {  // Needs nothing
            continue;
        }
        else {
            return false;
        }
    }

    return true;
}

template <class K, class V>
int dmap<K, V>::handleConnection() {
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

        std::cout << "Msg: " << buf << std::endl;
        Document doc;
        doc.Parse(buf);

        if (!isJSONValid(doc)) {
            return -1;
        }

        Value payloads(kArrayType);

        if (doc["payload"].IsArray()) {
            payloads = doc["payload"].GetArray();
        }
        else if (doc["payload"].IsObject()) {
            payloads.PushBack(doc["payload"].GetObject(), doc.GetAllocator());
        }

        Value response(kObjectType);
        response.AddMember("id", Value(doc["id"].GetString(), doc.GetAllocator()), doc.GetAllocator());

        Value return_array(kArrayType);
        std::string command = doc["command"].GetString();

        for (auto& payload : payloads.GetArray()) {
            Value return_value(kObjectType);

            if (command == "insert") {
                std::string key = payload["key"].GetString();
                std::string value = payload["value"].GetString();

                insert(key, value);

                return_value.AddMember("status", 204, doc.GetAllocator());
            }
            else if (command == "get") {
                std::string key = payload["key"].GetString();
                std::string value = get(key);  // TODO: Return this

                return_value.AddMember("status", 200, doc.GetAllocator());
                return_value.AddMember("value", Value(value.c_str(), value.size()), doc.GetAllocator());
            }
            else if (command == "delete") {
                std::string key = payload["key"].GetString();

                if (erase(key)) {
                    return_value.AddMember("status", 204, doc.GetAllocator());
                }
                else{
                    return_value.AddMember("status", 404, doc.GetAllocator());
                }
            }
            else if (command == "find") {
                std::string key = payload["key"].GetString();

                if (find(key)) {
                    return_value.AddMember("status", 204, doc.GetAllocator());
                }
                else {
                    return_value.AddMember("status", 404, doc.GetAllocator());
                }
            }
            else if (command == "update") {
                std::string key = payload["key"].GetString();
                std::string value = payload["value"].GetString();

                if (update(key, value)) {
                    return_value.AddMember("status", 204, doc.GetAllocator());
                }
                else {
                    return_value.AddMember("status", 404, doc.GetAllocator());
                }
            }
            else if (command == "upsert") {
                std::string key = payload["key"].GetString();
                std::string value = payload["value"].GetString();

                upsert(key, value);

                return_value.AddMember("status", 204, doc.GetAllocator());
            }
            else if (command == "clear") {
                clear();

                return_value.AddMember("status", 204, doc.GetAllocator());
            }
            else if (command == "count") {
                return_value.AddMember("status", 200, doc.GetAllocator());
                return_value.AddMember("value", size(), doc.GetAllocator());
            }

            return_array.PushBack(return_value, doc.GetAllocator());
        }

        response.AddMember("return", return_array, doc.GetAllocator());
    }
    else {
        return -1;
    }

    return 0;
}

#endif  // DMAP_H
