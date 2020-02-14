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

/**
 * Store a new key value pair
 *
 * @param   Key to store the value at
 * @param   Value to store
 */
template <class K, class V>
void dmap<K, V>::insert(K key, V value) {
    data[key] = value;
}

/**
 * Get value stored at key
 *
 * @param   Key whose value to get
 *
 * @return  Reference to value stored at key
 */
template <class K, class V>
V& dmap<K, V>::get(K key) {
    return data[key];
}

/**
 * Erase key-value pair at key
 *
 * @param   Key where to erase
 *
 * @return  True is pair was erased
 *          False if key-value pair could not be found
 */
template <class K, class V>
bool dmap<K, V>::erase(K key) {
    typename std::map<K, V>::iterator it;
    if ((it = data.find(key)) != data.end()) {
        data.erase(it);
        return true;
    }
    return false;
}

/**
 * Check if key exists
 *
 * @param   Key to check
 *
 * @return  True if key was found
 *          False if key was not found
 */
template <class K, class V>
bool dmap<K, V>::find(K key) {
    return data.find(key) != data.end();
}

/**
 * Update value at key
 *
 * @param   Key whose value to update
 * @param   New value to be stored
 *
 * @return  True if key is found
 *          False if key is not found
 */
template <class K, class V>
bool dmap<K, V>::update(K key, V value) {
    if (find(key)) {
        data[key] = value;
        return true;
    }
    return false;
}

/**
 * Update the value of key if it exists, create a new
 * entry in the store if key does not exist
 *
 * @param   Key whose value to upSert
 * @param   Value to be stored
 */
template <class K, class V>
void dmap<K, V>::upsert(K key, V value) {
    data[key] = value;
}

/**
 * Remove all key-value pairs
 */
template <class K, class V>
void dmap<K, V>::clear() {
    data.clear();
}

/**
 * Get the number of key-value pairs stored
 *
 * @return  Number of key-value pairs
 */
template <class K, class V>
int dmap<K, V>::size() {
    return data.size();
}

template <class K, class V>
V& dmap<K, V>::operator[](K key) {
    return data[key];
}

/// Networking

/**
 * Launch network connection manager. This will launch and
 * manage connections on separate threads.
 */
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

/**
 * Kill and clean up the network connection manager thread
 */
template <class K, class V>
int dmap<K, V>::stop() {
    running = false;

    return 0;
}

/**
 * Check if JSON is valid according to the communication protocol wiki
 *
 * @param   JSON object to be checked
 *
 * @return  True if doc is valid
 *          False if any errors are found in its format
 */
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

/**
 * Instance of server infrastructure waiting for a command
 * to come from a client. Checks if the message fits the
 * standard outlined by the communications protocol and
 * executes the request if it does.
 *
 * @return  0   if request successfully executied
 *          <0 otherwise
 */
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

        Value response(kObjectType);

        if (doc.HasMember("id") && doc["id"].IsString()) {
            response.AddMember("id", Value(doc["id"].GetString(), doc.GetAllocator()), doc.GetAllocator());
        }

        if (!isJSONValid(doc)) {
            response.AddMember("status", 400, doc.GetAllocator());

            return -1;
        }

        Value payloads(kArrayType);

        if (doc["payload"].IsArray()) {
            payloads = doc["payload"].GetArray();
        }
        else if (doc["payload"].IsObject()) {
            payloads.PushBack(doc["payload"].GetObject(), doc.GetAllocator());
        }

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
                std::string value = get(key);

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
