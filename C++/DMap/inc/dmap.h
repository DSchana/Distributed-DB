#ifndef DMAP_H
#define DMAP_H

#include <arpa/inet.h>
#include <atomic>
#include <base64.h>
#include <cstring>
#include <future>
#include <fstream>
#include <iostream>
#include <map>
#include <mutex>
#include <netinet/in.h>
#include <rapidjson/document.h>
#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include <stdexcept>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

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

    unsigned int port;

    int handleConnection();
    std::string createConnection(std::string msg, std::string dst_ip);

public:
    dmap(std::string config_path = "./.config/ddb.config");
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
    int start();
    int stop();
};

template <class K, class V>
dmap<K, V>::dmap(std::string config_path) {
    using namespace rapidjson;

    running = true;

    std::ifstream s_config(config_path, std::ifstream::in);
    if (!s_config) {
        throw std::runtime_error("Missing config file: " + config_path);
    }

    std::string ddb_config((std::istreambuf_iterator<char>(s_config)), std::istreambuf_iterator<char>());

    Document config_json;
    config_json.Parse(ddb_config.c_str());

    if (!config_json.HasMember("port") || !config_json["port"].IsUint()) {
        throw std::runtime_error("Invlaid port format in config file");
    }

    port = config_json["port"].GetUint();

    memset(&serv, 0, sizeof(serv));
    serv.sin_family = AF_INET;
    serv.sin_addr.s_addr = htonl(INADDR_ANY);
    serv.sin_port = htons(port);

    sock = socket(AF_INET, SOCK_STREAM, 0);

    // Network discovery
    std::fstream net_list("./.config/network-discovery", std::fstream::out | std::fstream::in);
    if (!net_list) {
        throw std::runtime_error("Missing network discovery file: ./.config/network-discovery");
    }

    std::string ip;
    while (net_list >> ip) {
        std::cout << ip << std::endl;
        // TODO: Ping old network nodes
    }

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
            connections.push_back(std::async(std::launch::async, &dmap<K, V>::handleConnection, this));
        }
        else {
            std::vector<std::vector<std::future<int> >::iterator> expired_connections;
            for (int i = 0; i < connections.size(); i++) {
                if (connections[i].wait_for(std::chrono::microseconds(5)) == std::future_status::ready) {
                    int status = connections[i].get();
                    expired_connections.push_back(connections.begin() + i);
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
bool isJSONValid(rapidjson::Document& doc) {
    using namespace rapidjson;

    if (!(doc.HasMember("id") && doc["id"].IsString()) ||
        !(doc.HasMember("command") && doc["command"].IsString()) ||
        !doc.HasMember("payload")) {
        return false;
    }

    if (!doc["payload"].IsArray()) {
        return false;
    }

    std::string command = doc["command"].GetString();
    for (auto& payload : doc["payload"].GetArray()) {
        if (command == "insert" || command == "update" || command == "upsert") {  // Needs key and value
            if (!(payload.HasMember("key") && payload["key"].IsString()) || !(payload.HasMember("value") && payload["value"].IsString())) {
                return false;
            }
        }
        else if (command == "get" || command == "delete" || command == "find") {  // Needs key only
            if (!(payload.HasMember("key") && payload["key"].IsString())) {
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

std::string getStringFromJSON(rapidjson::Value const& doc) {
    rapidjson::StringBuffer buffer;

    buffer.Clear();

    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);
    doc.Accept(writer);

    return std::string(strdup(buffer.GetString()));
}

/**
 * Instance of server infrastruction looking to connect to
 * a server instance and send information
 *
 * @return  Response of the request
 */
template <class K, class V>
std::string dmap<K, V>::createConnection(std::string msg, std::string dst_ip) {
    struct sockaddr_in dst;
    int sock = socket(AF_INET, SOCK_STREAM, 0);

    memset(&dst, 0, sizeof(dst));
    dst.sin_family = AF_INET;
    dst.sin_addr.s_addr = inet_addr(dst_ip.c_str());
    dst.sin_port = htons(port);

    connect(sock, (struct sockaddr*)&dst, sizeof(struct sockaddr_in));

    char rsp[1000];

    send(sock, msg.c_str(), strlen(msg.c_str()), 0);
    recv(sock, rsp, 1000, 0);

    close(sock);

    return rsp;
}

// TODO: Handle discovery protocol connections
/**
 * Instance of server infrastructure waiting for a command
 * to come from a client. Checks if the message fits the
 * standard outlined by the communications protocol and
 * executes the request if it does.
 *
 * @return  0   if request successfully executied
 *          <0  otherwise
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

    std::cout << "Handling connection" <<std::endl;

    Value response(kObjectType);

    if (consock) {
        char buf[1000];

        data_recv_mutex.lock();
        recv(consock, buf, 1000, 0);
        data_recv_mutex.unlock();

        Document doc;
        doc.Parse(buf);

        Document::AllocatorType& allocator = doc.GetAllocator();

        if (doc.HasParseError() || !isJSONValid(doc)) {
            response.AddMember("status", 400, allocator);
        }
        else {
            response.AddMember("id", Value(doc["id"].GetString(), allocator), allocator);

            Value return_array(kArrayType);
            std::string command = doc["command"].GetString();

            for (auto &payload : doc["payload"].GetArray()) {
                Value return_value(kObjectType);

                if (command == "insert") {
                    std::string key(payload["key"].GetString());
                    std::string value(payload["value"].GetString());

                    insert(key, value);

                    return_value.AddMember("status", 204, allocator);
                }
                else if (command == "get") {
                    std::string key(payload["key"].GetString());
                    std::string value = get(key);

                    return_value.AddMember("status", 200, allocator);
                    return_value.AddMember("value", Value(value.c_str(), allocator), allocator);
                }
                else if (command == "delete") {
                    std::string key(payload["key"].GetString());

                    if (erase(key)) {
                        return_value.AddMember("status", 204, allocator);
                    }
                    else {
                        return_value.AddMember("status", 404, allocator);
                    }
                }
                else if (command == "find") {
                    std::string key(payload["key"].GetString());

                    if (find(key)) {
                        return_value.AddMember("status", 204, allocator);
                    }
                    else {
                        return_value.AddMember("status", 404, allocator);
                    }
                }
                else if (command == "update") {
                    std::string key(payload["key"].GetString());
                    std::string value(payload["value"].GetString());

                    if (update(key, value)) {
                        return_value.AddMember("status", 204, allocator);
                    }
                    else {
                        return_value.AddMember("status", 404, allocator);
                    }
                }
                else if (command == "upsert") {
                    std::string key(payload["key"].GetString());
                    std::string value(payload["value"].GetString());

                    upsert(key, value);

                    return_value.AddMember("status", 204, allocator);
                }
                else if (command == "clear") {
                    clear();

                    return_value.AddMember("status", 204, allocator);
                }
                else if (command == "count") {
                    return_value.AddMember("status", 200, allocator);
                    return_value.AddMember("value", size(), allocator);
                }

                return_array.PushBack(return_value, allocator);
            }

            response.AddMember("return", return_array, allocator);
        }

        // Return response JSON
        std::string res_str = getStringFromJSON(response);
        send(consock, res_str.c_str(), strlen(res_str.c_str()), 0);

        close(consock);
    }
    else {
        return -1;
    }

    return 0;
}

#endif  // DMAP_H
